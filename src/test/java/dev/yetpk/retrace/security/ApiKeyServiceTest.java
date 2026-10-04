package dev.yetpk.retrace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.yetpk.retrace.domain.ApiKey;
import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.repo.ApiKeyRepository;
import dev.yetpk.retrace.repo.AppUserRepository;
import dev.yetpk.retrace.service.error.NotFoundException;
import java.time.Duration;
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
    private ApiKeyRepository apiKeyRepository;

    private AppUser owner;

    @BeforeEach
    void resetDatabase() {
        apiKeyRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        owner = createUser("owner");
    }

    private AppUser createUser(String username) {
        return appUserRepository.save(new AppUser(username, username + "@example.test", "not-a-real-hash"));
    }

    @Test
    void Authenticate_ValidKey_ShouldReturnTheOwner() {
        IssuedApiKey issued = apiKeyService.issueKey(owner, "laptop");

        Optional<AppUser> authenticated = apiKeyService.authenticate(issued.value());

        assertThat(authenticated).isPresent();
        assertThat(authenticated.get().getId()).isEqualTo(owner.getId());
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
        IssuedApiKey issued = apiKeyService.issueKey(owner, "laptop");

        assertThat(apiKeyService.authenticate(issued.key().getId() + ".wrong-secret")).isEmpty();
    }

    @Test
    void Authenticate_RevokedKey_ShouldReturnEmpty() {
        IssuedApiKey issued = apiKeyService.issueKey(owner, "laptop");
        apiKeyService.revokeKey(owner, issued.key().getId());

        assertThat(apiKeyService.authenticate(issued.value())).isEmpty();
    }

    @Test
    void Authenticate_ValidKey_ShouldAdvanceLastUsedAt() {
        IssuedApiKey issued = apiKeyService.issueKey(owner, "laptop");
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
        IssuedApiKey issued = apiKeyService.issueKey(owner, "laptop");

        assertThat(issued.key().getId()).startsWith("pm_").hasSize(15);
    }

    @Test
    void IssueKey_TwoKeys_ShouldDifferInIdAndSecret() {
        IssuedApiKey first = apiKeyService.issueKey(owner, "laptop");
        IssuedApiKey second = apiKeyService.issueKey(owner, "desktop");

        assertThat(first.key().getId()).isNotEqualTo(second.key().getId());
        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    void FindKeys_OwnerWithARevokedKey_ShouldListOnlyActiveOnes() {
        IssuedApiKey active = apiKeyService.issueKey(owner, "laptop");
        IssuedApiKey revoked = apiKeyService.issueKey(owner, "old");
        apiKeyService.revokeKey(owner, revoked.key().getId());

        assertThat(apiKeyService.findKeys(owner))
                .extracting(ApiKey::getId)
                .containsExactly(active.key().getId());
    }

    @Test
    void FindKeys_KeyOfAnotherOwner_ShouldNotBeListed() {
        AppUser other = createUser("other");
        apiKeyService.issueKey(other, "theirs");

        assertThat(apiKeyService.findKeys(owner)).isEmpty();
    }

    @Test
    void RevokeKey_KeyOwnedByAnotherUser_ShouldThrowNotFound() {
        AppUser other = createUser("other");
        IssuedApiKey theirs = apiKeyService.issueKey(other, "theirs");

        assertThatThrownBy(() -> apiKeyService.revokeKey(owner, theirs.key().getId()))
                .isInstanceOf(NotFoundException.class);

        assertThat(apiKeyService.authenticate(theirs.value())).isPresent();
    }

    @Test
    void RevokeKey_UnknownKey_ShouldThrowNotFound() {
        assertThatThrownBy(() -> apiKeyService.revokeKey(owner, "pm_doesnotexist"))
                .isInstanceOf(NotFoundException.class);
    }
}
