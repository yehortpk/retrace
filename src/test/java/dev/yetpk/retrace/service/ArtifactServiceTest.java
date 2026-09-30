package dev.yetpk.retrace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.yetpk.retrace.domain.Artifact;
import dev.yetpk.retrace.domain.Entry;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.service.dto.ArtifactHistory;
import dev.yetpk.retrace.service.dto.ArtifactSummary;
import dev.yetpk.retrace.service.dto.ArtifactVersionHistoryItem;
import dev.yetpk.retrace.service.dto.ArtifactVersionView;
import dev.yetpk.retrace.service.dto.AttachmentSpec;
import dev.yetpk.retrace.service.dto.RecordEntryCommand;
import dev.yetpk.retrace.service.dto.VersionContent;
import dev.yetpk.retrace.service.error.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactServiceTest extends ServiceTestSupport {

    private Project project;

    @BeforeEach
    void createProject() {
        project = projectService.createProject(owner, "Website", null);
    }

    private void recordEntryWith(String description, AttachmentSpec attachment) {
        timelineService.recordEntry(project,
                new RecordEntryCommand(null, description, null, null, List.of(attachment)));
    }

    /** Creates an artifact with two versions and returns its id. */
    private UUID createArtifactWithTwoVersions(String artifactName) {
        recordEntryWith("First draft", attachmentNamed(artifactName, "Draft", 10));
        UUID artifactId = artifactRepository.findByProjectId(project.getId()).stream()
                .filter(artifact -> artifact.getName().equals(artifactName))
                .findFirst().orElseThrow().getId();
        recordEntryWith("Shipped it", attachmentFor(artifactId, "Final", 10));
        return artifactId;
    }

    private UUID createArtifactWithOneVersion(String artifactName, int sizeBytes) {
        recordEntryWith("Added " + artifactName, attachmentNamed(artifactName, null, sizeBytes));
        return artifactRepository.findByProjectId(project.getId()).stream()
                .filter(artifact -> artifact.getName().equals(artifactName))
                .findFirst().orElseThrow().getId();
    }

    private long remainingQuota() {
        return projectRepository.findById(project.getId()).orElseThrow().getArtifactStorageRemainingBytes();
    }

    private UUID createArtifactInAnotherProject() {
        Project other = projectService.createProject(owner, "Other", null);
        timelineService.recordEntry(other,
                new RecordEntryCommand(null, "Theirs", null, null, List.of(attachmentNamed("logo", null, 5))));
        return artifactRepository.findByProjectId(other.getId()).getFirst().getId();
    }

    @Test
    void FindArtifacts_ProjectWithSeveralArtifacts_ShouldReturnThemAlphabeticallyByName() {
        createArtifactWithOneVersion("zebra", 5);
        createArtifactWithOneVersion("apple", 5);
        createArtifactWithOneVersion("mango", 5);

        assertThat(artifactService.findArtifacts(project))
                .extracting(ArtifactSummary::name)
                .containsExactly("apple", "mango", "zebra");
    }

    @Test
    void FindArtifacts_ArtifactWithSeveralVersions_ShouldReportCountAndCurrentVersion() {
        createArtifactWithTwoVersions("pricing-page");

        List<ArtifactSummary> summaries = artifactService.findArtifacts(project);

        assertThat(summaries).extracting(ArtifactSummary::name, ArtifactSummary::versionCount)
                .containsExactly(tuple("pricing-page", 2));
        assertThat(summaries.getFirst().currentVersion())
                .extracting(ArtifactVersionView::ordinal, ArtifactVersionView::label)
                .containsExactly(2, "Final");
    }

    @Test
    void FindArtifactHistory_ArtifactWithSeveralVersions_ShouldReturnOldestFirstWithItsEntry() {
        UUID artifactId = createArtifactWithTwoVersions("pricing-page");

        ArtifactHistory history = artifactService.findArtifactHistory(project, artifactId);

        assertThat(history.name()).isEqualTo("pricing-page");
        assertThat(history.versions())
                .extracting(ArtifactVersionHistoryItem::ordinal, ArtifactVersionHistoryItem::label,
                        ArtifactVersionHistoryItem::entryDescription)
                .containsExactly(tuple(1, "Draft", "First draft"), tuple(2, "Final", "Shipped it"));
    }

    @Test
    void FindArtifactHistory_ArtifactWithManyVersions_ShouldNotIssueAQueryPerVersion() {
        UUID artifactId = createArtifactWithOneVersion("pricing-page", 5);
        for (int i = 0; i < 19; i++) {
            recordEntryWith("Revision " + i, attachmentFor(artifactId, null, 5));
        }
        Statistics statistics = clearAndGetStatistics();

        ArtifactHistory history = artifactService.findArtifactHistory(project, artifactId);

        assertThat(history.versions()).hasSize(20);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void FindArtifactHistory_ArtifactFromAnotherProject_ShouldThrowNotFound() {
        UUID foreignArtifactId = createArtifactInAnotherProject();

        assertThatThrownBy(() -> artifactService.findArtifactHistory(project, foreignArtifactId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void RenameArtifact_ExistingArtifact_ShouldPreserveIdVersionsAndEntryLinks() {
        UUID artifactId = createArtifactWithTwoVersions("pricing-page");
        ArtifactHistory before = artifactService.findArtifactHistory(project, artifactId);

        Artifact renamed = artifactService.renameArtifact(project, artifactId, "pricing-page-v2");

        assertThat(renamed.getId()).isEqualTo(artifactId);
        ArtifactHistory after = artifactService.findArtifactHistory(project, artifactId);
        assertThat(after.name()).isEqualTo("pricing-page-v2");
        assertThat(after.versions()).isEqualTo(before.versions());
    }

    @Test
    void RenameArtifact_NameAlreadyUsedInTheProject_ShouldSucceedBecauseNameIsNotIdentity() {
        UUID artifactId = createArtifactWithOneVersion("pricing-page", 5);
        createArtifactWithOneVersion("logo", 5);

        Artifact renamed = artifactService.renameArtifact(project, artifactId, "logo");

        assertThat(renamed.getName()).isEqualTo("logo");
        assertThat(artifactService.findArtifacts(project)).extracting(ArtifactSummary::name)
                .containsExactly("logo", "logo");
    }

    @Test
    void RenameArtifact_ArtifactFromAnotherProject_ShouldThrowNotFound() {
        UUID foreignArtifactId = createArtifactInAnotherProject();

        assertThatThrownBy(() -> artifactService.renameArtifact(project, foreignArtifactId, "mine"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void OpenVersionContent_StoredVersion_ShouldReturnItsBytesFilenameAndType() throws IOException {
        recordEntryWith("Took notes", attachmentWithContent("notes", "meeting-notes.txt", "what we agreed"));
        UUID artifactId = artifactRepository.findByProjectId(project.getId()).getFirst().getId();

        VersionContent content = artifactService.openVersionContent(project, artifactId, 1);

        assertThat(content.filename()).isEqualTo("meeting-notes.txt");
        assertThat(content.contentType()).isEqualTo("text/plain");
        try (InputStream stream = content.content()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("what we agreed");
        }
    }

    @Test
    void OpenVersionContent_UnknownOrdinal_ShouldThrowNotFound() {
        UUID artifactId = createArtifactWithOneVersion("logo", 5);

        assertThatThrownBy(() -> artifactService.openVersionContent(project, artifactId, 2))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void OpenVersionContent_ArtifactFromAnotherProject_ShouldThrowNotFound() {
        UUID foreignArtifactId = createArtifactInAnotherProject();

        assertThatThrownBy(() -> artifactService.openVersionContent(project, foreignArtifactId, 1))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void DeleteVersion_ArtifactWithSeveralVersions_ShouldReleaseItsBytesAndKeepTheRest() {
        UUID artifactId = createArtifactWithTwoVersions("pricing-page");
        long before = remainingQuota();

        artifactService.deleteVersion(project, artifactId, 2);

        assertThat(remainingQuota()).isEqualTo(before + 10);
        assertThat(artifactService.findArtifactHistory(project, artifactId).versions())
                .extracting(ArtifactVersionHistoryItem::ordinal).containsExactly(1);
    }

    @Test
    void DeleteVersion_LastVersionOfAnArtifact_ShouldDeleteTheArtifact() {
        UUID artifactId = createArtifactWithOneVersion("logo", 5);

        artifactService.deleteVersion(project, artifactId, 1);

        assertThat(artifactRepository.findByIdAndProjectId(artifactId, project.getId())).isEmpty();
        assertThat(artifactService.findArtifacts(project)).isEmpty();
    }

    @Test
    void DeleteVersion_LastVersionOfAnArtifact_ShouldKeepTheEntryThatProducedIt() {
        UUID artifactId = createArtifactWithOneVersion("logo", 5);

        artifactService.deleteVersion(project, artifactId, 1);

        assertThat(entryRepository.findAll()).extracting(Entry::getDescription).containsExactly("Added logo");
    }

    @Test
    void DeleteVersion_ArtifactFromAnotherProject_ShouldThrowNotFound() {
        UUID foreignArtifactId = createArtifactInAnotherProject();

        assertThatThrownBy(() -> artifactService.deleteVersion(project, foreignArtifactId, 1))
                .isInstanceOf(NotFoundException.class);
    }
}
