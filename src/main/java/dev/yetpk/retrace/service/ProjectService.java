package dev.yetpk.retrace.service;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.repo.EntryRepository;
import dev.yetpk.retrace.repo.ProjectEntryCount;
import dev.yetpk.retrace.repo.ProjectRepository;
import dev.yetpk.retrace.security.ApiKeyService;
import dev.yetpk.retrace.security.IssuedApiKey;
import dev.yetpk.retrace.service.dto.CreatedProject;
import dev.yetpk.retrace.service.dto.ProjectSummary;
import dev.yetpk.retrace.service.error.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "which projects are mine?" and owns the one place a project is resolved for its owner.
 * {@link #findProject} is the single entry point for owner scoping: every other service takes the
 * {@link Project} it returns, so no method further in can be reached with an unscoped id.
 */
@Service
public class ProjectService {

    /** The name given to the key every new project is created with. */
    public static final String DEFAULT_KEY_NAME = "Default key";

    private final ProjectRepository projectRepository;
    private final EntryRepository entryRepository;
    private final ArtifactStorageQuota storageQuota;
    private final ApiKeyService apiKeyService;

    public ProjectService(ProjectRepository projectRepository, EntryRepository entryRepository,
                          ArtifactStorageQuota storageQuota, ApiKeyService apiKeyService) {
        this.projectRepository = projectRepository;
        this.entryRepository = entryRepository;
        this.storageQuota = storageQuota;
        this.apiKeyService = apiKeyService;
    }

    /**
     * Creates a project and, in the same transaction, issues its first API key. Returns the key's
     * plaintext, which exists only in this return value and is never recoverable afterwards.
     *
     * <p>Issuing the key here is deliberate: the product's thesis is that the agent does the
     * remembering, and a project that needs a second trip to a settings page before an agent can write
     * to it has a setup step between its owner and the whole point.
     */
    @Transactional
    public CreatedProject createProject(AppUser owner, String name, String description) {
        Project project = projectRepository.save(
                new Project(owner, name, description, storageQuota.getLimitBytes()));
        IssuedApiKey key = apiKeyService.issueKey(project, DEFAULT_KEY_NAME);
        return new CreatedProject(
                new ProjectSummary(project.getId(), project.getName(), project.getDescription(), 0L), key);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> findProjects(AppUser owner) {
        List<Project> owned = projectRepository.findByOwnerId(owner.getId());
        if (owned.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> countsByProjectId = entryRepository
                .countByProjectIdIn(owned.stream().map(Project::getId).toList()).stream()
                .collect(Collectors.toMap(ProjectEntryCount::projectId, ProjectEntryCount::entryCount));
        return owned.stream()
                .map(project -> new ProjectSummary(
                        project.getId(),
                        project.getName(),
                        project.getDescription(),
                        countsByProjectId.getOrDefault(project.getId(), 0L)))
                .toList();
    }

    /**
     * Resolves one of {@code owner}'s projects by id. A project owned by somebody else is reported
     * exactly like one that does not exist, so ids cannot be probed across accounts.
     */
    @Transactional(readOnly = true)
    public Project findProject(AppUser owner, UUID projectId) {
        return projectRepository.findByIdAndOwnerId(projectId, owner.getId())
                .orElseThrow(() -> new NotFoundException("No project with id '%s'".formatted(projectId)));
    }
}
