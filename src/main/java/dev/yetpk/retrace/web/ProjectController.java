package dev.yetpk.retrace.web;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.security.CurrentUserResolver;
import dev.yetpk.retrace.service.ProjectService;
import dev.yetpk.retrace.service.dto.CreatedProject;
import dev.yetpk.retrace.service.dto.ProjectSummary;
import dev.yetpk.retrace.web.dto.CreateProjectRequest;
import dev.yetpk.retrace.web.dto.CreatedProjectResponse;
import dev.yetpk.retrace.web.dto.IssuedKeyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists and creates projects — <b>reachable by session only</b>. An API key belongs to one project, so
 * it has no business seeing the owner's other projects or creating new ones. The owner comes from the
 * credential, never from the request.
 */
@RestController
@RequestMapping("/api/projects")
@SecurityRequirement(name = "session")
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUserResolver currentUserResolver;

    public ProjectController(ProjectService projectService, CurrentUserResolver currentUserResolver) {
        this.projectService = projectService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "The caller's projects, with how much history each holds")
    @GetMapping
    public List<ProjectSummary> findProjects() {
        return projectService.findProjects(currentUserResolver.findCurrentUser());
    }

    @Operation(summary = "Create a project and issue its first API key",
            description = "The returned UUID is the project's only handle — it is what every other path "
                    + "is nested under. The response also carries the project's first API key in "
                    + "plaintext, for the only time it exists: an agent configured with that key writes to "
                    + "this project and no other.")
    @ApiResponse(responseCode = "201", description = "Created; the body carries the project's key once")
    @PostMapping
    public ResponseEntity<CreatedProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        AppUser owner = currentUserResolver.findCurrentUser();
        CreatedProject created = projectService.createProject(owner, request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CreatedProjectResponse(created.project(), IssuedKeyResponse.from(created.key())));
    }
}
