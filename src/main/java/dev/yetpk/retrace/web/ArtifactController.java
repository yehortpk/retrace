package dev.yetpk.retrace.web;

import dev.yetpk.retrace.domain.Artifact;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.security.CurrentUserResolver;
import dev.yetpk.retrace.service.ArtifactService;
import dev.yetpk.retrace.service.ProjectService;
import dev.yetpk.retrace.service.dto.ArtifactHistory;
import dev.yetpk.retrace.service.dto.ArtifactSummary;
import dev.yetpk.retrace.service.dto.VersionContent;
import dev.yetpk.retrace.web.dto.RenameArtifactRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads and maintains a project's artifacts: how each one evolved, version by version.
 *
 * <p>Every path is nested under its project and every lookup resolves the artifact together with it,
 * so <b>an artifact is never reachable outside its project</b>. A real artifact id used under a
 * different project's id is a 404 for read, rename, download, and delete alike.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/artifacts")
public class ArtifactController {

    private final ArtifactService artifactService;
    private final ProjectService projectService;
    private final CurrentUserResolver currentUserResolver;

    public ArtifactController(ArtifactService artifactService, ProjectService projectService,
                              CurrentUserResolver currentUserResolver) {
        this.artifactService = artifactService;
        this.projectService = projectService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "The project's artifacts, each with its current version and version count")
    @ApiResponse(responseCode = "404", description = "No such project for this owner", content = @Content)
    @GetMapping
    public List<ArtifactSummary> findArtifacts(@PathVariable UUID projectId) {
        return artifactService.findArtifacts(findProject(projectId));
    }

    @Operation(summary = "One artifact and its full version history",
            description = "Each version carries the entry that produced it: an artifact's story and the "
                    + "project's story are the same rows, read from two sides.")
    @ApiResponse(responseCode = "404", description = "No such artifact in this project", content = @Content)
    @GetMapping("/{artifactId}")
    public ArtifactHistory findArtifactHistory(@PathVariable UUID projectId, @PathVariable UUID artifactId) {
        return artifactService.findArtifactHistory(findProject(projectId), artifactId);
    }

    @Operation(summary = "Rename an artifact",
            description = "Touches the name and nothing else — the id, the versions, and the entries those "
                    + "versions link to are untouched. Names are not unique in a project, so one already "
                    + "in use is accepted.")
    @ApiResponse(responseCode = "404", description = "No such artifact in this project", content = @Content)
    @PatchMapping("/{artifactId}")
    public ArtifactHistory renameArtifact(@PathVariable UUID projectId, @PathVariable UUID artifactId,
                                          @Valid @RequestBody RenameArtifactRequest request) {
        Project project = findProject(projectId);
        Artifact renamed = artifactService.renameArtifact(project, artifactId, request.name());
        return artifactService.findArtifactHistory(project, renamed.getId());
    }

    @Operation(summary = "Download one version's stored file",
            description = "Streams the stored bytes with the filename and content type recorded at upload. "
                    + "`Content-Length` comes from the database row, which is also the cheapest check that "
                    + "storage and the database still agree about this version.")
    @ApiResponse(responseCode = "404", description = "No such artifact in this project, or no such version",
            content = @Content)
    @GetMapping("/{artifactId}/versions/{ordinal}/download")
    public ResponseEntity<InputStreamResource> downloadVersion(
            @PathVariable UUID projectId,
            @PathVariable UUID artifactId,
            @Parameter(description = "The version's dense position in its artifact's history (1…N), "
                    + "not a database id")
            @PathVariable int ordinal) {
        VersionContent content = artifactService.openVersionContent(findProject(projectId), artifactId, ordinal);
        // InputStreamResource lets Spring write and close the stream, including on a client
        // disconnect — VersionContent's contract is that the caller closes it.
        return ResponseEntity.ok()
                .headers(toDownloadHeaders(content))
                .contentLength(content.sizeBytes())
                .body(new InputStreamResource(content.content()));
    }

    @Operation(summary = "Delete one version",
            description = "Gives the version's bytes back to the project's storage quota in the same "
                    + "transaction. Deleting the last version deletes the artifact too: an artifact with "
                    + "no versions is not something that happened.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such artifact in this project, or no such version",
            content = @Content)
    @DeleteMapping("/{artifactId}/versions/{ordinal}")
    public ResponseEntity<Void> deleteVersion(
            @PathVariable UUID projectId,
            @PathVariable UUID artifactId,
            @Parameter(description = "The version's dense position in its artifact's history (1…N), "
                    + "not a database id")
            @PathVariable int ordinal) {
        artifactService.deleteVersion(findProject(projectId), artifactId, ordinal);
        return ResponseEntity.noContent().build();
    }

    private Project findProject(UUID projectId) {
        return projectService.findProject(currentUserResolver.findCurrentUser(), projectId);
    }

    /**
     * A filename is user-supplied, so it is attached with {@link ContentDisposition}'s RFC 5987
     * encoding rather than interpolated into the header — a name carrying a quote or a non-ASCII
     * character would otherwise produce a malformed or truncated header.
     */
    private static HttpHeaders toDownloadHeaders(VersionContent content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(content.filename() == null ? "download" : content.filename(),
                        java.nio.charset.StandardCharsets.UTF_8)
                .build());
        headers.setContentType(content.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(content.contentType()));
        return headers;
    }
}
