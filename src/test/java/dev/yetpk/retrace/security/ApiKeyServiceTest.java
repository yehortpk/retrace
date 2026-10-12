package dev.yetpk.retrace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.yetpk.retrace.domain.ApiKey;
import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.repo.ApiKeyRepository;
import dev.yetpk.retrace.repo.AppUserRepository;
import dev.yetpk.retrace.repo.ProjectRepository;
import dev.yetpk.retrace.service.error.NotFoundException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApiKeyServiceTest {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private AppUser owner;
    private Project project;

    @BeforeEach
    void resetDatabase() {
        apiKeyRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        owner = createUser("owner");
        project = createProject(owner, "Website");
    }

    private AppUser createUser(String username) {
        return appUserRepository.save(new AppUser(username, username + "@example.test", "not-a-real-hash"));
    }

    /**
     * Saved straight through the repository rather than {@code ProjectService}, which would issue a
     * default key of its own and blur what each test counts.
     */
    private Project createProject(AppUser projectOwner, String name) {
        return projectRepository.save(new Project(projectOwner, name, null, 1024));
    }

    @Test
    void Authenticate_ValidKey_ShouldReturnTheKeyWithItsProjectAndOwner() {
        IssuedApiKey issued = apiKeyService.issueKey(project, "laptop");

        Optional<ApiKey> authenticated = apiKeyService.authenticate(issued.value());

        assertThat(authenticated).isPresent();
        assertThat(authenticated.get().getId()).isEqualTo(issued.key().getId());
        // Read outside any transaction: the filter needs both without a second query, and a lazy
        // proxy here would throw instead.
        assertThat(authenticated.get().getProject().getId()).isEqualTo(project.getId());
        assertThat(authenticated.get().getProject().getOwner().getId()).isEqualTo(owner.getId());
        assertThat(authenticated.get().getProject().getOwner().getUsername()).isEqualTo("owner");
    }

    @Test
    void Authenticate_NullOrBlankKey_ShouldReturnEmpty() {
        assertThat(apiKeyService.authenticate(null)).isEmpty();
        assertThat(apiKeyService.authenticate("")).isEmpty();
        assertThat(apiKeyService.authenticate("   ")).isEmpty();
    }

    @Test
    void Authenticate_MalformedKey_ShouldReturnEmpty() {
        assertThat(apiKeyService.authenticate("no-separator-at-all")).isEmpty();
        assertThat(apiKeyService.authenticate(".secret-with-no-id")).isEmpty();
        assertThat(apiKeyService.authenticate("pm_idwithnosecret.")).isEmpty();
    }

    @Test
    void Authenticate_UnknownId_ShouldReturnEmpty() {
        assertThat(apiKeyService.authenticate("pm_doesnotexist.somesecret")).isEmpty();
    }

    @Test
    void Authenticate_WrongSecret_ShouldReturnEmpty() {
        IssuedApiKey issued = apiKeyService.issueKey(project, "laptop");

        assertThat(apiKeyService.authenticate(issued.key().getId() + ".wrong-secret")).isEmpty();
    }

    @Test
    void Authenticate_SecretOfAnotherKey_ShouldReturnEmpty() {
        IssuedApiKey first = apiKeyService.issueKey(project, "laptop");
        IssuedApiKey second = apiKeyService.issueKey(project, "desktop");
        String secondSecret = second.value().substring(second.value().indexOf('.') + 1);

        assertThat(apiKeyService.authenticate(first.key().getId() + "." + secondSecret)).isEmpty();
    }

    @Test
    void Authenticate_RevokedKey_ShouldReturnEmpty() {
        IssuedApiKey issued = apiKeyService.issueKey(project, "laptop");
        apiKeyService.revokeKey(project, issued.key().getId());

        assertThat(apiKeyService.authenticate(issued.value())).isEmpty();
    }

    @Test
    void Authenticate_ValidKey_ShouldAdvanceLastUsedAt() {
        IssuedApiKey issued = apiKeyService.issueKey(project, "laptop");
        assertThat(issued.key().getLastUsedAt()).isNull();

        apiKeyService.authenticate(issued.value());

        // Recorded off the request path, so the assertion has to wait for it rather than assume it
        // already happened.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(apiKeyRepository.findById(issued.key().getId()).orElseThrow().getLastUsedAt())
                        .isNotNull());
    }

    @Test
    void IssueKey_NewKey_ShouldPrefixThePublicId() {
        IssuedApiKey issued = apiKeyService.issueKey(project, "laptop");

        assertThat(issued.key().getId()).startsWith("pm_").hasSize(15);
    }

    @Test
    void IssueKey_NewKey_ShouldBelongToTheGivenProject() {
        IssuedApiKey issued = apiKeyService.issueKey(project, "laptop");

        assertThat(apiKeyRepository.findByIdAndProjectId(issued.key().getId(), project.getId())).isPresent();
    }

    @Test
    void IssueKey_TwoKeys_ShouldDifferInIdAndSecret() {
        IssuedApiKey first = apiKeyService.issueKey(project, "laptop");
        IssuedApiKey second = apiKeyService.issueKey(project, "desktop");

        assertThat(first.key().getId()).isNotEqualTo(second.key().getId());
        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    void FindKeys_ProjectWithARevokedKey_ShouldListOnlyActiveOnes() {
        IssuedApiKey active = apiKeyService.issueKey(project, "laptop");
        IssuedApiKey revoked = apiKeyService.issueKey(project, "old");
        apiKeyService.revokeKey(project, revoked.key().getId());

        assertThat(apiKeyService.findKeys(project))
                .extracting(ApiKey::getId)
                .containsExactly(active.key().getId());
    }

    @Test
    void FindKeys_KeyOfAnotherProjectOfTheSameOwner_ShouldNotBeListed() {
        Project other = createProject(owner, "Other");
        IssuedApiKey mine = apiKeyService.issueKey(project, "mine");
        apiKeyService.issueKey(other, "other");

        assertThat(apiKeyService.findKeys(project))
                .extracting(ApiKey::getId)
                .containsExactly(mine.key().getId());
    }

    @Test
    void FindKeys_KeyOfAnotherOwner_ShouldNotBeListed() {
        apiKeyService.issueKey(createProject(createUser("other"), "Theirs"), "theirs");

        assertThat(apiKeyService.findKeys(project)).isEmpty();
    }

    /** The case user-wide keys could not express: one owner, two projects, two separate blast radii. */
    @Test
    void RevokeKey_KeyOfAnotherProjectOfTheSameOwner_ShouldThrowNotFound() {
        Project other = createProject(owner, "Other");
        IssuedApiKey otherKey = apiKeyService.issueKey(other, "other");

        assertThatThrownBy(() -> apiKeyService.revokeKey(project, otherKey.key().getId()))
                .isInstanceOf(NotFoundException.class);

        assertThat(apiKeyService.authenticate(otherKey.value())).isPresent();
    }

    @Test
    void RevokeKey_KeyOfAnotherOwner_ShouldThrowNotFound() {
        IssuedApiKey theirs = apiKeyService.issueKey(createProject(createUser("other"), "Theirs"), "theirs");

        assertThatThrownBy(() -> apiKeyService.revokeKey(project, theirs.key().getId()))
                .isInstanceOf(NotFoundException.class);

        assertThat(apiKeyService.authenticate(theirs.value())).isPresent();
    }

    @Test
    void RevokeKey_UnknownKey_ShouldThrowNotFound() {
        assertThatThrownBy(() -> apiKeyService.revokeKey(project, "pm_doesnotexist"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void RevokeKey_OneOfTwoKeys_ShouldLeaveTheOtherWorking() {
        IssuedApiKey revoked = apiKeyService.issueKey(project, "laptop");
        IssuedApiKey kept = apiKeyService.issueKey(project, "desktop");

        apiKeyService.revokeKey(project, revoked.key().getId());

        assertThat(apiKeyService.authenticate(revoked.value())).isEmpty();
        assertThat(apiKeyService.authenticate(kept.value())).isPresent();
    }

    @Test
    void RevokeKey_AlreadyRevokedKey_ShouldKeepTheFirstRevocationTime() {
        IssuedApiKey issued = apiKeyService.issueKey(project, "laptop");
        apiKeyService.revokeKey(project, issued.key().getId());
        OffsetDateTime firstRevokedAt = apiKeyRepository.findById(issued.key().getId()).orElseThrow()
                .getRevokedAt();

        apiKeyService.revokeKey(project, issued.key().getId());

        assertThat(apiKeyRepository.findById(issued.key().getId()).orElseThrow().getRevokedAt())
                .isEqualTo(firstRevokedAt);
    }
}
