package dev.yetpk.retrace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import dev.yetpk.retrace.domain.ApiKey;
import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.security.ApiKeyService;
import dev.yetpk.retrace.service.dto.CreatedProject;
import dev.yetpk.retrace.service.dto.ProjectSummary;
import dev.yetpk.retrace.service.dto.RecordEntryCommand;
import dev.yetpk.retrace.service.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ProjectServiceTest extends ServiceTestSupport {

    /** A spy, so one test can make key issuance fail and observe what that does to the project. */
    @MockitoSpyBean
    private ApiKeyService apiKeyService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void recordEntry(Project project, String description) {
        timelineService.recordEntry(project, new RecordEntryCommand(null, description, null, null, List.of()));
    }

    private static String secretOf(String presentedKey) {
        return presentedKey.substring(presentedKey.indexOf('.') + 1);
    }

    @Test
    void CreateProject_NewProject_ShouldInitializeQuotaFromConfiguredLimit() {
        Project project = createProject(owner, "Website");

        assertThat(project.getArtifactStorageRemainingBytes()).isEqualTo(storageQuota.getLimitBytes());
    }

    @Test
    void CreateProject_NewProject_ShouldAssignAGeneratedId() {
        CreatedProject created = projectService.createProject(owner, "Website", "a description");

        assertThat(created.project().id()).isNotNull();
        assertThat(created.project().name()).isEqualTo("Website");
        assertThat(created.project().description()).isEqualTo("a description");
        assertThat(created.project().entryCount()).isZero();
        assertThat(projectRepository.findById(created.project().id())).isPresent();
    }

    @Test
    void CreateProject_SameNameForDifferentOwners_ShouldCreateTwoDistinctProjects() {
        AppUser other = createUser("other");

        CreatedProject mine = projectService.createProject(owner, "Website", null);
        CreatedProject theirs = projectService.createProject(other, "Website", null);

        assertThat(mine.project().id()).isNotEqualTo(theirs.project().id());
    }

    @Test
    void CreateProject_NewProject_ShouldIssueExactlyOneKeyForIt() {
        CreatedProject created = projectService.createProject(owner, "Website", null);

        List<ApiKey> keys = apiKeyRepository.findActiveByProjectId(created.project().id());
        assertThat(keys).extracting(ApiKey::getId).containsExactly(created.key().key().getId());
        assertThat(keys.getFirst().getName()).isEqualTo(ProjectService.DEFAULT_KEY_NAME);
        assertThat(apiKeyRepository.count()).isEqualTo(1);
    }

    @Test
    void CreateProject_TwoProjects_ShouldEachGetTheirOwnKey() {
        CreatedProject first = projectService.createProject(owner, "First", null);
        CreatedProject second = projectService.createProject(owner, "Second", null);

        assertThat(first.key().key().getId()).isNotEqualTo(second.key().key().getId());
        assertThat(apiKeyRepository.findActiveByProjectId(first.project().id()))
                .extracting(ApiKey::getId).containsExactly(first.key().key().getId());
        assertThat(apiKeyRepository.findActiveByProjectId(second.project().id()))
                .extracting(ApiKey::getId).containsExactly(second.key().key().getId());
    }

    @Test
    void CreateProject_NewProject_ShouldReturnTheKeyAsIdAndSecret() {
        CreatedProject created = projectService.createProject(owner, "Website", null);

        String value = created.key().value();
        assertThat(value).startsWith(created.key().key().getId() + ".");
        assertThat(secretOf(value)).isNotBlank().doesNotContain(".");
    }

    @Test
    void CreateProject_NewProject_ShouldStoreOnlyABcryptHashOfTheSecret() {
        CreatedProject created = projectService.createProject(owner, "Website", null);

        String secret = secretOf(created.key().value());
        ApiKey stored = apiKeyRepository.findById(created.key().key().getId()).orElseThrow();
        assertThat(stored.getSecretHash()).isNotEqualTo(secret).doesNotContain(secret).startsWith("$2");
        assertThat(passwordEncoder.matches(secret, stored.getSecretHash())).isTrue();
    }

    @Test
    void CreateProject_IssuedKey_ShouldAuthenticateAsTheOwnerConfinedToTheNewProject() {
        CreatedProject created = projectService.createProject(owner, "Website", null);

        ApiKey authenticated = apiKeyService.authenticate(created.key().value()).orElseThrow();

        assertThat(authenticated.getProject().getId()).isEqualTo(created.project().id());
        assertThat(authenticated.getProject().getOwner().getId()).isEqualTo(owner.getId());
    }

    /** The project and its key are one transaction: a project never exists without a way to connect an agent. */
    @Test
    void CreateProject_KeyIssuanceFails_ShouldLeaveNoProjectBehind() {
        doThrow(new IllegalStateException("simulated failure"))
                .when(apiKeyService).issueKey(any(Project.class), anyString());

        assertThatThrownBy(() -> projectService.createProject(owner, "Website", null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(projectRepository.count()).isZero();
        assertThat(apiKeyRepository.count()).isZero();
    }

    @Test
    void DeleteProject_ProjectWithKeys_ShouldDeleteItsKeys() {
        CreatedProject doomed = projectService.createProject(owner, "Doomed", null);
        Project doomedProject = projectService.findProject(owner, doomed.project().id());
        String secondKey = apiKeyService.issueKey(doomedProject, "second").value();
        CreatedProject kept = projectService.createProject(owner, "Kept", null);

        projectRepository.deleteById(doomed.project().id());

        assertThat(apiKeyRepository.findActiveByProjectId(doomed.project().id())).isEmpty();
        assertThat(apiKeyService.authenticate(doomed.key().value())).isEmpty();
        assertThat(apiKeyService.authenticate(secondKey)).isEmpty();
        assertThat(apiKeyService.authenticate(kept.key().value())).isPresent();
    }

    @Test
    void FindProjects_OwnerWithProjects_ShouldReturnOnlyTheirsWithEntryCounts() {
        Project counted = projectService.findProject(owner,
                projectService.createProject(owner, "Counted", "has entries").project().id());
        createProject(owner, "Empty");
        createProject(createUser("other"), "Theirs");
        recordEntry(counted, "First");
        recordEntry(counted, "Second");

        List<ProjectSummary> summaries = projectService.findProjects(owner);

        assertThat(summaries)
                .extracting(ProjectSummary::name, ProjectSummary::entryCount)
                .containsExactly(tuple("Counted", 2L), tuple("Empty", 0L));
    }

    @Test
    void FindProjects_OwnerWithNoProjects_ShouldReturnEmptyList() {
        assertThat(projectService.findProjects(owner)).isEmpty();
    }

    @Test
    void FindProject_OwnedProject_ShouldReturnIt() {
        CreatedProject created = projectService.createProject(owner, "Website", null);

        assertThat(projectService.findProject(owner, created.project().id()).getId())
                .isEqualTo(created.project().id());
    }

    @Test
    void FindProject_ProjectOwnedByAnotherUser_ShouldThrowNotFound() {
        Project theirs = createProject(createUser("other"), "Website");

        assertThatThrownBy(() -> projectService.findProject(owner, theirs.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void FindProject_UnknownId_ShouldThrowNotFound() {
        assertThatThrownBy(() -> projectService.findProject(owner, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
