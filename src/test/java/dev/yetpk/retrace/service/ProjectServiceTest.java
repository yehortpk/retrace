package dev.yetpk.retrace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.service.dto.ProjectSummary;
import dev.yetpk.retrace.service.dto.RecordEntryCommand;
import dev.yetpk.retrace.service.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectServiceTest extends ServiceTestSupport {

    private void recordEntry(Project project, String description) {
        timelineService.recordEntry(project, new RecordEntryCommand(null, description, null, null, List.of()));
    }

    @Test
    void CreateProject_NewProject_ShouldInitializeQuotaFromConfiguredLimit() {
        Project project = projectService.createProject(owner, "Website", null);

        assertThat(project.getArtifactStorageRemainingBytes()).isEqualTo(storageQuota.getLimitBytes());
    }

    @Test
    void CreateProject_NewProject_ShouldAssignAGeneratedId() {
        Project project = projectService.createProject(owner, "Website", "a description");

        assertThat(project.getId()).isNotNull();
        assertThat(project.getName()).isEqualTo("Website");
        assertThat(project.getDescription()).isEqualTo("a description");
    }

    @Test
    void CreateProject_SameNameForDifferentOwners_ShouldCreateTwoDistinctProjects() {
        AppUser other = createUser("other");

        Project mine = projectService.createProject(owner, "Website", null);
        Project theirs = projectService.createProject(other, "Website", null);

        assertThat(mine.getId()).isNotEqualTo(theirs.getId());
    }

    @Test
    void FindProjects_OwnerWithProjects_ShouldReturnOnlyTheirsWithEntryCounts() {
        Project counted = projectService.createProject(owner, "Counted", "has entries");
        projectService.createProject(owner, "Empty", null);
        projectService.createProject(createUser("other"), "Theirs", null);
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
        Project created = projectService.createProject(owner, "Website", null);

        assertThat(projectService.findProject(owner, created.getId()).getId()).isEqualTo(created.getId());
    }

    @Test
    void FindProject_ProjectOwnedByAnotherUser_ShouldThrowNotFound() {
        Project theirs = projectService.createProject(createUser("other"), "Website", null);

        assertThatThrownBy(() -> projectService.findProject(owner, theirs.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void FindProject_UnknownId_ShouldThrowNotFound() {
        assertThatThrownBy(() -> projectService.findProject(owner, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
