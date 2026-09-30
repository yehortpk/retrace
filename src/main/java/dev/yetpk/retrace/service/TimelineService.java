package dev.yetpk.retrace.service;

import dev.yetpk.retrace.domain.Artifact;
import dev.yetpk.retrace.domain.ArtifactVersion;
import dev.yetpk.retrace.domain.Entry;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.repo.ArtifactRepository;
import dev.yetpk.retrace.repo.ArtifactVersionRepository;
import dev.yetpk.retrace.repo.EntryRepository;
import dev.yetpk.retrace.repo.ProjectRepository;
import dev.yetpk.retrace.service.dto.ArtifactVersionView;
import dev.yetpk.retrace.service.dto.AttachmentSpec;
import dev.yetpk.retrace.service.dto.RecordEntryCommand;
import dev.yetpk.retrace.service.dto.TimelineEntryView;
import dev.yetpk.retrace.service.error.NotFoundException;
import dev.yetpk.retrace.service.error.QuotaExceededException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "what happened, in order?" and owns the single write path into a project's history.
 * Every method takes a {@link Project} already resolved for its owner by
 * {@link ProjectService#findProject}, so ownership is settled before anything here runs.
 */
@Service
public class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);

    private final ProjectRepository projectRepository;
    private final EntryRepository entryRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final ArtifactStorageQuota storageQuota;
    private final FileStore fileStore;

    public TimelineService(ProjectRepository projectRepository, EntryRepository entryRepository,
                           ArtifactRepository artifactRepository,
                           ArtifactVersionRepository artifactVersionRepository, ArtifactStorageQuota storageQuota,
                           FileStore fileStore) {
        this.projectRepository = projectRepository;
        this.entryRepository = entryRepository;
        this.artifactRepository = artifactRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.storageQuota = storageQuota;
        this.fileStore = fileStore;
    }

    /**
     * Records one entry and the artifact versions it produced, in a single transaction.
     *
     * <p>The whole batch is checked against the project's storage quota before any file is written,
     * so an entry that does not fit stores nothing at all. Two sessions recording into the same
     * project concurrently both read the same remaining quota; {@code Project}'s optimistic lock
     * makes the second commit fail rather than overwrite the first one's decrement.
     */
    @Transactional
    public Entry recordEntry(Project project, RecordEntryCommand command) {
        // The caller's Project was loaded in its own transaction and is detached here, so quota
        // changes made to it would never be flushed. Re-read it into this persistence context.
        Project managed = projectRepository.findById(project.getId())
                .orElseThrow(() -> new NotFoundException("No project with id '%s'".formatted(project.getId())));
        List<AttachmentSpec> attachments = command.attachments();
        Map<AttachmentSpec, Artifact> artifactsByAttachment = resolveArtifacts(managed, attachments);

        long totalBytes = attachments.stream().mapToLong(AttachmentSpec::sizeBytes).sum();
        if (!storageQuota.isWithinLimit(managed, totalBytes)) {
            log.info("Rejected an entry for project {}: {} bytes requested, {} remaining",
                    managed.getId(), totalBytes, managed.getArtifactStorageRemainingBytes());
            throw new QuotaExceededException(totalBytes, managed.getArtifactStorageRemainingBytes());
        }

        OffsetDateTime occurredAt = command.occurredAt() != null ? command.occurredAt() : OffsetDateTime.now();
        Entry entry = entryRepository.save(new Entry(managed, occurredAt, command.description(), command.note(),
                command.sessionId()));

        Map<UUID, Integer> nextOrdinals = new HashMap<>();
        for (AttachmentSpec attachment : attachments) {
            Artifact artifact = artifactsByAttachment.get(attachment);
            int ordinal = nextOrdinals.computeIfAbsent(artifact.getId(),
                    artifactId -> artifactVersionRepository.findMaxOrdinal(artifactId) + 1);
            nextOrdinals.put(artifact.getId(), ordinal + 1);

            UUID storageKey = storeContent(managed, attachment);
            artifactVersionRepository.save(new ArtifactVersion(artifact, entry, ordinal, attachment.label(),
                    storageKey, attachment.filename(), attachment.contentType(), attachment.sizeBytes()));
        }

        if (totalBytes > 0) {
            managed.consumeArtifactStorage(totalBytes);
        }
        log.info("Recorded entry {} in project {} with {} artifact version(s), {} bytes",
                entry.getId(), managed.getId(), attachments.size(), totalBytes);
        return entry;
    }

    @Transactional(readOnly = true)
    public Page<TimelineEntryView> findTimeline(Project project, Pageable pageable) {
        Page<Entry> page = entryRepository.findByProjectIdNewestFirst(project.getId(), pageable);
        Map<UUID, List<ArtifactVersionView>> versionsByEntryId = findVersionsByEntryId(page.getContent());
        return page.map(entry -> toView(entry, versionsByEntryId));
    }

    /** Entries recorded after {@code since}, newest first — what a starting session reads to catch up. */
    @Transactional(readOnly = true)
    public List<TimelineEntryView> findTimelineSince(Project project, OffsetDateTime since) {
        List<Entry> found = entryRepository.findByProjectIdOccurredAfter(project.getId(), since);
        Map<UUID, List<ArtifactVersionView>> versionsByEntryId = findVersionsByEntryId(found);
        return found.stream().map(entry -> toView(entry, versionsByEntryId)).toList();
    }

    /**
     * Resolves every attachment to an artifact inside {@code project}. An {@code artifactId} adds a
     * version to that existing artifact; an id belonging to another project is not found rather than
     * silently linked, which is what keeps a version's entry and artifact in the same project — a
     * constraint the schema itself cannot express. An attachment carrying a name instead always
     * creates a new artifact, since a name is a current label, not an identity to look up by.
     */
    private Map<AttachmentSpec, Artifact> resolveArtifacts(Project project, List<AttachmentSpec> attachments) {
        Map<AttachmentSpec, Artifact> resolved = new LinkedHashMap<>();
        for (AttachmentSpec attachment : attachments) {
            if (attachment.artifactId() != null) {
                resolved.put(attachment, artifactRepository
                        .findByIdAndProjectId(attachment.artifactId(), project.getId())
                        .orElseThrow(() -> new NotFoundException(
                                "No artifact with id '%s' in this project".formatted(attachment.artifactId()))));
            } else {
                resolved.put(attachment, artifactRepository.save(new Artifact(project, attachment.artifactName())));
            }
        }
        return resolved;
    }

    private UUID storeContent(Project project, AttachmentSpec attachment) {
        try (InputStream content = attachment.content().getInputStream()) {
            return fileStore.storeContent(project.getId(), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded content for " + attachment.filename(), e);
        }
    }

    /**
     * Loads the versions for a whole page of entries in one query. Doing this per entry would turn a
     * 20-entry timeline into 21 round trips.
     */
    private Map<UUID, List<ArtifactVersionView>> findVersionsByEntryId(List<Entry> forEntries) {
        if (forEntries.isEmpty()) {
            return Map.of();
        }
        List<UUID> entryIds = forEntries.stream().map(Entry::getId).toList();
        Map<UUID, List<ArtifactVersionView>> byEntryId = new HashMap<>();
        for (ArtifactVersion version : artifactVersionRepository.findByEntryIdIn(entryIds)) {
            byEntryId.computeIfAbsent(version.getEntry().getId(), id -> new ArrayList<>())
                    .add(toView(version));
        }
        return byEntryId;
    }

    private TimelineEntryView toView(Entry entry, Map<UUID, List<ArtifactVersionView>> versionsByEntryId) {
        return new TimelineEntryView(entry.getId(), entry.getOccurredAt(), entry.getDescription(), entry.getNote(),
                entry.getSessionId(), versionsByEntryId.getOrDefault(entry.getId(), List.of()));
    }

    static ArtifactVersionView toView(ArtifactVersion version) {
        Artifact artifact = version.getArtifact();
        return new ArtifactVersionView(artifact.getId(), artifact.getName(), version.getOrdinal(), version.getLabel(),
                version.getFilename(), version.getContentType(), version.getSizeBytes(), version.getCreatedAt());
    }
}
