package dev.yetpk.retrace.web;

import dev.yetpk.retrace.domain.Entry;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.security.CurrentUserResolver;
import dev.yetpk.retrace.service.ProjectService;
import dev.yetpk.retrace.service.TimelineService;
import dev.yetpk.retrace.service.dto.AttachmentSpec;
import dev.yetpk.retrace.service.dto.RecordEntryCommand;
import dev.yetpk.retrace.service.dto.TimelineEntryView;
import dev.yetpk.retrace.web.dto.RecordEntryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Records entries — the one write path into a project's history, reached identically by the UI form
 * and by the agent's {@code curl}. Nothing here distinguishes the two, and nothing stored says which
 * one wrote a given row.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class EntryController {

    private final TimelineService timelineService;
    private final ProjectService projectService;
    private final CurrentUserResolver currentUserResolver;

    public EntryController(TimelineService timelineService, ProjectService projectService,
                           CurrentUserResolver currentUserResolver) {
        this.timelineService = timelineService;
        this.projectService = projectService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "Record one entry, optionally with files",
            description = """
                    Multipart with one `entry` part (`application/json`) and zero or more `files` parts.

                    **`entry.artifacts[i]` pairs with `files[i]` by position.** There is no name or id \
                    linking a described artifact to its uploaded bytes — only the order the parts appear \
                    in. A mismatch between the two counts is refused with a 400 naming both, because a \
                    silently misaligned pairing would attach the wrong file to the wrong artifact.

                    Each artifact is named either by `artifactId`, which adds a version to that existing \
                    artifact, or by `name`, which always creates a new one. An entry with no files at all \
                    is the common case — a client call, a decision — and needs no `files` part.

                    Send the JSON part with its content type set, or it binds as plain text:
                    `-F 'entry={...};type=application/json'`""")
    @ApiResponse(responseCode = "201", description = "Recorded; the body carries the entry with the "
            + "ordinals it produced")
    @ApiResponse(responseCode = "400", description = "Invalid entry, an empty file, or a count mismatch "
            + "between artifacts and files", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid credential", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such project for this owner, or an artifactId "
            + "from another project", content = @Content)
    @ApiResponse(responseCode = "409", description = "Another writer recorded into this project "
            + "concurrently; retry", content = @Content)
    @ApiResponse(responseCode = "413", description = "The batch would exceed the project's storage quota; "
            + "the body carries requestedBytes and remainingBytes", content = @Content)
    @PostMapping(path = "/entries", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schemaProperties = {
                    @SchemaProperty(name = "entry", schema = @Schema(implementation = RecordEntryRequest.class)),
                    @SchemaProperty(name = "files", array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                            schema = @Schema(type = "string", format = "binary")))
            },
            encoding = @Encoding(name = "entry", contentType = MediaType.APPLICATION_JSON_VALUE)))
    public ResponseEntity<TimelineEntryView> recordEntry(
            @PathVariable UUID projectId,
            @RequestPart("entry") @Valid RecordEntryRequest entry,
            @RequestPart(name = "files", required = false) List<MultipartFile> files) {
        Project project = findProject(projectId);
        List<MultipartFile> uploads = files == null ? List.of() : files;
        List<RecordEntryRequest.ArtifactAttachment> attachments = entry.attachmentsOrEmpty();
        validatePairing(attachments, uploads);

        Entry recorded = timelineService.recordEntry(project, new RecordEntryCommand(
                entry.occurredAt(), entry.description(), entry.note(), entry.sessionId(),
                toAttachmentSpecs(attachments, uploads)));
        return ResponseEntity.status(HttpStatus.CREATED).body(timelineService.findEntry(project, recorded.getId()));
    }

    private Project findProject(UUID projectId) {
        return projectService.findProject(currentUserResolver.findCurrentUser(), projectId);
    }

    /**
     * Checks the positional contract before anything is stored.
     *
     * <p>This is the single most likely way for an agent-written {@code curl} to be wrong, so each
     * message says what was actually sent rather than leaving a generic bind error to be interpreted.
     */
    private void validatePairing(List<RecordEntryRequest.ArtifactAttachment> attachments,
                                 List<MultipartFile> uploads) {
        if (attachments.size() != uploads.size()) {
            throw new IllegalArgumentException(
                    ("entry.artifacts and files must pair up by position, but %d artifact(s) were described "
                            + "and %d file(s) uploaded").formatted(attachments.size(), uploads.size()));
        }
        for (int i = 0; i < attachments.size(); i++) {
            if (!attachments.get(i).isArtifactNamedExactlyOnce()) {
                throw new IllegalArgumentException(
                        "entry.artifacts[%d] needs exactly one of artifactId or name".formatted(i));
            }
            MultipartFile upload = uploads.get(i);
            // AttachmentSpec throws on a non-positive size, which would surface as a 500. An empty
            // upload is a caller mistake, so it has to be refused here as a 400.
            if (upload == null || upload.isEmpty()) {
                throw new IllegalArgumentException("files[%d] ('%s') is empty"
                        .formatted(i, upload == null ? "" : upload.getOriginalFilename()));
            }
        }
    }

    /**
     * Pairs each described artifact with its uploaded file, passing the {@link MultipartFile} itself
     * as the content source. It satisfies {@code InputStreamSource} as is, so nothing here buffers an
     * upload and the service owns opening and closing each stream.
     */
    private List<AttachmentSpec> toAttachmentSpecs(List<RecordEntryRequest.ArtifactAttachment> attachments,
                                                   List<MultipartFile> uploads) {
        List<AttachmentSpec> specs = new ArrayList<>(attachments.size());
        for (int i = 0; i < attachments.size(); i++) {
            RecordEntryRequest.ArtifactAttachment attachment = attachments.get(i);
            MultipartFile upload = uploads.get(i);
            specs.add(new AttachmentSpec(
                    attachment.artifactId(),
                    attachment.name(),
                    attachment.label(),
                    upload.getOriginalFilename(),
                    upload.getContentType(),
                    upload.getSize(),
                    upload));
        }
        return specs;
    }
}
