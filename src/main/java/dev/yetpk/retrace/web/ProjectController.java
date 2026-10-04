package dev.yetpk.retrace.web;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.security.CurrentUserResolver;
import dev.yetpk.retrace.service.ProjectService;
import dev.yetpk.retrace.service.dto.ProjectSummary;
import dev.yetpk.retrace.web.dto.CreateProjectRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lists and creates projects. The owner comes from the credential, never from the request. */
@RestController
@RequestMapping("/api/projects")
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

    @Operation(summary = "Create a project",
            description = "The returned UUID is the project's only handle — it is what every other path "
                    + "is nested under, and what an agent is configured with.")
    @PostMapping
    public ResponseEntity<ProjectSummary> createProject(@Valid @RequestBody CreateProjectRequest request) {
        AppUser owner = currentUserResolver.findCurrentUser();
        Project created = projectService.createProject(owner, request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ProjectSummary(created.getId(), created.getName(), created.getDescription(), 0L));
    }
}
