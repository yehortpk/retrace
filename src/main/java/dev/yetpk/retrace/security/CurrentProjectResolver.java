package dev.yetpk.retrace.security;

import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.service.ProjectService;
import dev.yetpk.retrace.service.error.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The one way a controller gets a {@link Project}. It resolves the project for the current user and,
 * when the request carries an API key, <b>confines it to the key's project</b>.
 *
 * <p>Owner scoping alone is not enough once a key belongs to a project: every project of the owner
 * would still be reachable with any one of their keys. Doing the check here, rather than in each
 * handler, means a new project-scoped endpoint cannot forget it.
 *
 * <p>A key used on another project — even another project of the same owner — gets exactly the
 * answer a stranger gets: {@link NotFoundException}, never a permission error, so a key cannot be used
 * to probe which project ids exist.
 */
@Component
public class CurrentProjectResolver {

    private final CurrentUserResolver currentUserResolver;
    private final ProjectService projectService;

    public CurrentProjectResolver(CurrentUserResolver currentUserResolver, ProjectService projectService) {
        this.currentUserResolver = currentUserResolver;
        this.projectService = projectService;
    }

    /**
     * The project {@code projectId} names, if the current credential may reach it.
     *
     * @throws NotFoundException if the project does not exist, belongs to someone else, or is not the
     *         project of the API key the request was made with
     */
    public Project findProject(UUID projectId) {
        Optional<UUID> confinedProjectId = findConfinedProjectId();
        if (confinedProjectId.isPresent() && !confinedProjectId.get().equals(projectId)) {
            // Worded exactly as ProjectService words a project that does not exist.
            throw new NotFoundException("No project with id '%s'".formatted(projectId));
        }
        return projectService.findProject(currentUserResolver.findCurrentUser(), projectId);
    }

    /**
     * The project the current credential is confined to: the key's project under API key
     * authentication, and empty under a session, which may reach every project its user owns.
     */
    public Optional<UUID> findConfinedProjectId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ApiKeyAuthenticationToken keyAuthentication) {
            return Optional.of(keyAuthentication.getProjectId());
        }
        return Optional.empty();
    }
}
