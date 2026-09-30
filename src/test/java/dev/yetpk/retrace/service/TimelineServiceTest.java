package dev.yetpk.retrace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.yetpk.retrace.domain.ArtifactVersion;
import dev.yetpk.retrace.domain.Entry;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.service.dto.ArtifactVersionView;
import dev.yetpk.retrace.service.dto.AttachmentSpec;
import dev.yetpk.retrace.service.dto.RecordEntryCommand;
import dev.yetpk.retrace.service.dto.TimelineEntryView;
import dev.yetpk.retrace.service.error.NotFoundException;
import dev.yetpk.retrace.service.error.QuotaExceededException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.OptimisticLockException;
import org.springframework.transaction.support.TransactionTemplate;

class TimelineServiceTest extends ServiceTestSupport {

    private Project project;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void createProject() {
        project = projectService.createProject(owner, "Website", null);
    }

    private RecordEntryCommand command(String description, String note) {
        return new RecordEntryCommand(null, description, note, null, List.of());
    }

    private RecordEntryCommand commandWith(String description, AttachmentSpec... attachments) {
        return new RecordEntryCommand(null, description, null, null, List.of(attachments));
    }

    /**
     * Merges a stale {@link Project} into a fresh transaction and flushes, which is what a second
     * concurrent {@code recordEntry} would do at commit time.
     */
    private void recordEntryWithStaleProject(Project staleProject) {
        transactionTemplate.executeWithoutResult(status -> {
            staleProject.consumeArtifactStorage(10);
            entityManager.merge(staleProject);
            entityManager.flush();
        });
    }

    private long remainingQuota() {
        return projectRepository.findById(project.getId()).orElseThrow().getArtifactStorageRemainingBytes();
    }

    private UUID onlyArtifactId() {
        return artifactRepository.findByProjectId(project.getId()).getFirst().getId();
    }

    /**
     * Counts versions whose artifact and entry sit in different projects. The schema cannot express
     * that constraint without composite foreign keys, so {@code recordEntry} enforces it and this
     * pins it down.
     */
    private long countVersionsCrossingProjects() {
        return entityManager.createQuery("""
                select count(v) from ArtifactVersion v
                where v.artifact.project.id <> v.entry.project.id""", Long.class).getSingleResult();
    }

    @Test
    void RecordEntry_NoAttachments_ShouldSaveTheEntry() {
        Entry entry = timelineService.recordEntry(project,
                command("Shipped the pricing page.", "Client asked for annual plans first."));

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getDescription()).isEqualTo("Shipped the pricing page.");
        assertThat(entry.getNote()).isEqualTo("Client asked for annual plans first.");
        assertThat(entry.getOccurredAt()).isNotNull();
    }

    @Test
    void RecordEntry_NoOccurredAt_ShouldDefaultToNow() {
        OffsetDateTime beforeCall = OffsetDateTime.now().minusSeconds(1);

        Entry entry = timelineService.recordEntry(project, command("Something happened", null));

        assertThat(entry.getOccurredAt()).isAfter(beforeCall);
    }

    @Test
    void RecordEntry_ExistingArtifactId_ShouldAddAVersionInsteadOfASecondArtifact() {
        timelineService.recordEntry(project, commandWith("First draft", attachmentNamed("pricing-page", "Draft", 10)));
        UUID artifactId = onlyArtifactId();

        timelineService.recordEntry(project, commandWith("Shipped it", attachmentFor(artifactId, "Final", 10)));

        assertThat(artifactRepository.findByProjectId(project.getId())).hasSize(1);
        assertThat(artifactVersionRepository.findByArtifactIdOldestFirst(artifactId))
                .extracting(ArtifactVersion::getOrdinal, ArtifactVersion::getLabel)
                .containsExactly(tuple(1, "Draft"), tuple(2, "Final"));
    }

    @Test
    void RecordEntry_ArtifactName_ShouldAlwaysCreateANewArtifact() {
        timelineService.recordEntry(project, commandWith("First", attachmentNamed("pricing-page", null, 10)));
        timelineService.recordEntry(project, commandWith("Second", attachmentNamed("pricing-page", null, 10)));

        assertThat(artifactRepository.findByProjectId(project.getId()))
                .hasSize(2)
                .allSatisfy(artifact -> assertThat(artifact.getName()).isEqualTo("pricing-page"));
    }

    @Test
    void RecordEntry_SeveralVersionsOfOneArtifactInOneEntry_ShouldAssignDenseOrdinals() {
        timelineService.recordEntry(project, commandWith("First", attachmentNamed("pricing-page", "A", 10)));
        UUID artifactId = onlyArtifactId();

        timelineService.recordEntry(project, commandWith("Two at once",
                attachmentFor(artifactId, "B", 10), attachmentFor(artifactId, "C", 10)));

        assertThat(artifactVersionRepository.findByArtifactIdOldestFirst(artifactId))
                .extracting(ArtifactVersion::getOrdinal)
                .containsExactly(1, 2, 3);
    }

    @Test
    void RecordEntry_ArtifactIdFromAnotherProject_ShouldThrowNotFound() {
        Project other = projectService.createProject(owner, "Other", null);
        timelineService.recordEntry(other, commandWith("Theirs", attachmentNamed("logo", null, 10)));
        UUID foreignArtifactId = artifactRepository.findByProjectId(other.getId()).getFirst().getId();

        assertThatThrownBy(() -> timelineService.recordEntry(project,
                commandWith("Mine", attachmentFor(foreignArtifactId, null, 10))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void RecordEntry_EntriesInSeveralProjects_ShouldNeverLinkAVersionAcrossProjects() {
        Project other = projectService.createProject(owner, "Other", null);
        timelineService.recordEntry(project, commandWith("Mine", attachmentNamed("logo", null, 10)));
        timelineService.recordEntry(other, commandWith("Theirs", attachmentNamed("logo", null, 10)));

        assertThat(countVersionsCrossingProjects()).isZero();
    }

    @Test
    void RecordEntry_WithAttachments_ShouldConsumeExactlyTheStoredBytes() {
        long before = remainingQuota();

        timelineService.recordEntry(project, commandWith("With files",
                attachmentNamed("a", null, 30), attachmentNamed("b", null, 12)));

        assertThat(remainingQuota()).isEqualTo(before - 42);
    }

    @Test
    void RecordEntry_BatchExceedingQuota_ShouldStoreNothingAtAll() {
        long limit = storageQuota.getLimitBytes();
        int oversized = (int) limit;

        assertThatThrownBy(() -> timelineService.recordEntry(project, commandWith("Too big",
                attachmentNamed("a", null, 10), attachmentNamed("b", null, oversized))))
                .isInstanceOf(QuotaExceededException.class);

        assertThat(entryRepository.findAll()).isEmpty();
        assertThat(artifactVersionRepository.findAll()).isEmpty();
        assertThat(remainingQuota()).isEqualTo(limit);
    }

    @Test
    void RecordEntry_BatchExceedingQuota_ShouldReportRequestedAndRemainingBytes() {
        long limit = storageQuota.getLimitBytes();
        int oversized = (int) limit + 1;

        assertThatThrownBy(() -> timelineService.recordEntry(project,
                commandWith("Too big", attachmentNamed("a", null, oversized))))
                .isInstanceOfSatisfying(QuotaExceededException.class, e -> {
                    assertThat(e.getRequestedBytes()).isEqualTo(oversized);
                    assertThat(e.getRemainingBytes()).isEqualTo(limit);
                });
    }

    @Test
    void RecordEntry_StaleProjectFromAConcurrentReader_ShouldFailOnOptimisticLock() {
        // Two sessions each read the project at version 0. The first records and commits, bumping the
        // row to version 1. The second still holds version 0: its update matches no row, so Hibernate
        // rejects it instead of silently overwriting the first writer's quota decrement.
        Project staleReader = projectRepository.findById(project.getId()).orElseThrow();
        entityManager.detach(staleReader);
        timelineService.recordEntry(project, commandWith("First writer", attachmentNamed("a", null, 10)));
        entityManager.clear();

        assertThatThrownBy(() -> recordEntryWithStaleProject(staleReader))
                .isInstanceOf(OptimisticLockException.class);

        // The first writer's decrement survives: 1024 - 10, not overwritten back to 1024 - 20.
        assertThat(remainingQuota()).isEqualTo(storageQuota.getLimitBytes() - 10);
    }

    @Test
    void FindTimeline_EntriesSharingATimestamp_ShouldNotRepeatARowAcrossPages() {
        OffsetDateTime sharedInstant = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        for (int i = 0; i < 6; i++) {
            timelineService.recordEntry(project,
                    new RecordEntryCommand(sharedInstant, "Entry " + i, null, null, List.of()));
        }

        Page<TimelineEntryView> firstPage = timelineService.findTimeline(project, PageRequest.of(0, 3));
        Page<TimelineEntryView> secondPage = timelineService.findTimeline(project, PageRequest.of(1, 3));

        assertThat(firstPage.getTotalElements()).isEqualTo(6);
        assertThat(firstPage.getContent()).extracting(TimelineEntryView::id)
                .doesNotContainAnyElementsOf(secondPage.getContent().stream().map(TimelineEntryView::id).toList());
    }

    @Test
    void FindTimeline_EntriesWithAndWithoutFiles_ShouldAttachEachEntrysVersions() {
        timelineService.recordEntry(project, commandWith("Shipped it", attachmentNamed("pricing-page", "Final", 10)));
        timelineService.recordEntry(project, command("Just talked to the client", null));

        List<TimelineEntryView> timeline = timelineService.findTimeline(project, PageRequest.of(0, 10)).getContent();

        assertThat(timeline).extracting(TimelineEntryView::description)
                .containsExactly("Just talked to the client", "Shipped it");
        assertThat(timeline.get(0).versions()).isEmpty();
        assertThat(timeline.get(1).versions()).singleElement()
                .extracting(ArtifactVersionView::artifactName, ArtifactVersionView::ordinal,
                        ArtifactVersionView::label)
                .containsExactly("pricing-page", 1, "Final");
    }

    @Test
    void FindTimeline_FullPageOfEntries_ShouldNotIssueAQueryPerEntry() {
        for (int i = 0; i < 20; i++) {
            timelineService.recordEntry(project, commandWith("Entry " + i, attachmentNamed("artifact-" + i, null, 5)));
        }
        Statistics statistics = clearAndGetStatistics();

        List<TimelineEntryView> page = timelineService.findTimeline(project, PageRequest.of(0, 20)).getContent();

        assertThat(page).hasSize(20).allSatisfy(entry -> assertThat(entry.versions()).hasSize(1));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void FindTimeline_AnotherProjectsEntries_ShouldExcludeThem() {
        Project other = projectService.createProject(owner, "Other", null);
        timelineService.recordEntry(other, command("Theirs", null));
        timelineService.recordEntry(project, command("Mine", null));

        assertThat(timelineService.findTimeline(project, PageRequest.of(0, 10)).getContent())
                .extracting(TimelineEntryView::description).containsExactly("Mine");
    }

    @Test
    void FindTimelineSince_EntriesOlderAndNewerThanTheInstant_ShouldReturnOnlyNewer() {
        OffsetDateTime now = OffsetDateTime.now();
        timelineService.recordEntry(project,
                new RecordEntryCommand(now.minusDays(2), "Old news", null, null, List.of()));
        timelineService.recordEntry(project,
                new RecordEntryCommand(now.minusMinutes(5), "Recent", null, null, List.of()));

        List<TimelineEntryView> since = timelineService.findTimelineSince(project, now.minusDays(1));

        assertThat(since).extracting(TimelineEntryView::description).containsExactly("Recent");
    }
}
