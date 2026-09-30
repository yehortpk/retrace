package dev.yetpk.retrace.service;

import dev.yetpk.retrace.domain.Artifact;
import dev.yetpk.retrace.domain.ArtifactVersion;
import dev.yetpk.retrace.domain.Entry;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.repo.ArtifactRepository;
import dev.yetpk.retrace.repo.ArtifactVersionRepository;
import dev.yetpk.retrace.repo.ProjectRepository;
import dev.yetpk.retrace.service.dto.ArtifactHistory;
import dev.yetpk.retrace.service.dto.ArtifactSummary;
import dev.yetpk.retrace.service.dto.ArtifactVersionHistoryItem;
import dev.yetpk.retrace.service.dto.VersionContent;
import dev.yetpk.retrace.service.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "how did this artifact evolve?".
 *
 * <p>Containment is structural rather than checked after the fact: every method takes the
 * {@link Project} its caller already resolved and loads the artifact with
 * {@code findByIdAndProjectId}, so there is no code path that reaches an artifact without naming
 * its project. A version is addressed as {@code (artifact, ordinal)} and never by its own id.
 */
@Service
public class ArtifactService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);

    private final ProjectRepository projectRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final FileStore fileStore;

    public ArtifactService(ProjectRepository projectRepository, ArtifactRepository artifactRepository,
                           ArtifactVersionRepository artifactVersionRepository, FileStore fileStore) {
        this.projectRepository = projectRepository;
        this.artifactRepository = artifactRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.fileStore = fileStore;
    }

    @Transactional(readOnly = true)
    public List<ArtifactSummary> findArtifacts(Project project) {
        return artifactRepository.findByProjectId(project.getId()).stream()
                .map(artifact -> new ArtifactSummary(
                        artifact.getId(),
                        artifact.getName(),
                        artifact.getCreatedAt(),
                        artifactVersionRepository.countByArtifactId(artifact.getId()),
                        artifactVersionRepository.findCurrentByArtifactId(artifact.getId())
                                .map(TimelineService::toView)
                                .orElse(null)))
                .toList();
    }

    /**
     * The artifact's versions oldest first, each with the entry that produced it. This reads the same
     * rows the timeline does, from the artifact's side: the project's story and the artifact's story
     * are one set of facts, not two.
     */
    @Transactional(readOnly = true)
    public ArtifactHistory findArtifactHistory(Project project, UUID artifactId) {
        Artifact artifact = findArtifact(project, artifactId);
        List<ArtifactVersionHistoryItem> versions = artifactVersionRepository
                .findByArtifactIdOldestFirst(artifactId).stream()
                .map(ArtifactService::toHistoryItem)
                .toList();
        return new ArtifactHistory(artifact.getId(), artifact.getName(), artifact.getCreatedAt(), versions);
    }

    /**
     * Renames the artifact's label and nothing else. Its id, its versions, and the entries those
     * versions link to are untouched by construction — the whole point of identifying an artifact by
     * a UUID assigned once at creation. The name is a current label only, so two artifacts in one
     * project may carry the same one.
     */
    @Transactional
    public Artifact renameArtifact(Project project, UUID artifactId, String newName) {
        Artifact artifact = findArtifact(project, artifactId);
        log.info("Renaming artifact {} in project {} from '{}' to '{}'",
                artifactId, project.getId(), artifact.getName(), newName);
        artifact.setName(newName);
        return artifact;
    }

    /** Opens the stored file for one version. The caller owns the returned stream and must close it. */
    @Transactional(readOnly = true)
    public VersionContent openVersionContent(Project project, UUID artifactId, int ordinal) {
        ArtifactVersion version = findVersion(project, artifactId, ordinal);
        return new VersionContent(fileStore.retrieveContent(project.getId(), version.getStorageKey()),
                version.getFilename(), version.getContentType(), version.getSizeBytes());
    }

    /**
     * Deletes one version and gives its bytes back to the project's storage quota in the same
     * transaction. Deleting the last version deletes the artifact too: an artifact with no versions
     * is not something that happened.
     *
     * <p>The stored file is left in storage, consistent with the write path's rollback policy — an
     * unreferenced file is invisible, while a row pointing at a missing file is a broken download.
     */
    @Transactional
    public void deleteVersion(Project project, UUID artifactId, int ordinal) {
        // As in recordEntry, the caller's Project is detached; re-read it so the quota it gives back
        // is actually flushed.
        Project managed = projectRepository.findById(project.getId())
                .orElseThrow(() -> new NotFoundException("No project with id '%s'".formatted(project.getId())));
        ArtifactVersion version = findVersion(managed, artifactId, ordinal);
        Artifact artifact = version.getArtifact();

        artifactVersionRepository.delete(version);
        artifactVersionRepository.flush();
        managed.releaseArtifactStorage(version.getSizeBytes());

        boolean artifactRemoved = artifactVersionRepository.countByArtifactId(artifactId) == 0;
        if (artifactRemoved) {
            artifactRepository.delete(artifact);
        }
        log.info("Deleted version {} of artifact {} in project {}, releasing {} bytes{}",
                ordinal, artifactId, managed.getId(), version.getSizeBytes(),
                artifactRemoved ? " and removing the now-empty artifact" : "");
    }

    private Artifact findArtifact(Project project, UUID artifactId) {
        return artifactRepository.findByIdAndProjectId(artifactId, project.getId())
                .orElseThrow(() -> new NotFoundException(
                        "No artifact with id '%s' in this project".formatted(artifactId)));
    }

    private ArtifactVersion findVersion(Project project, UUID artifactId, int ordinal) {
        findArtifact(project, artifactId);
        return artifactVersionRepository.findByArtifactIdAndOrdinal(artifactId, ordinal)
                .orElseThrow(() -> new NotFoundException(
                        "Artifact '%s' has no version %d".formatted(artifactId, ordinal)));
    }

    private static ArtifactVersionHistoryItem toHistoryItem(ArtifactVersion version) {
        Entry entry = version.getEntry();
        return new ArtifactVersionHistoryItem(version.getOrdinal(), version.getLabel(), version.getFilename(),
                version.getContentType(), version.getSizeBytes(), version.getCreatedAt(), entry.getId(),
                entry.getOccurredAt(), entry.getDescription(), entry.getNote());
    }
}
