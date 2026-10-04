package dev.yetpk.retrace.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The {@code entry} part of a {@code POST …/entries} request: one thing that happened, plus a
 * description of each file accompanying it.
 *
 * <p>The validation here is not decoration. {@code RecordEntryCommand} and {@code AttachmentSpec}
 * reject a blank description and a non-positive size by throwing {@code IllegalArgumentException} —
 * those are programming-error guards on a service contract, not user-facing validation. Bean
 * Validation has to refuse the same inputs first, or a bad upload reaches a constructor and surfaces
 * as a 500 where it should have been a 400.
 */
public record RecordEntryRequest(
        @Schema(description = "When it happened; null means now", example = "2026-09-30T14:03:00Z")
        OffsetDateTime occurredAt,

        @Schema(description = "What happened, in the past tense", example = "Shipped the pricing page.")
        @NotBlank @Size(max = 2000) String description,

        @Schema(description = "Why it happened — the reasoning, not a repeat of the description",
                example = "Client asked for annual plans first.")
        @Size(max = 10000) String note,

        @Schema(description = "The agent session this was recorded from, so one session's entries can "
                + "be read together. Absent for an entry made by hand.")
        @Size(max = 200) String sessionId,

        @Schema(description = "One per uploaded file, in the same order as the 'files' parts")
        @Valid List<ArtifactAttachment> artifacts) {

    /**
     * One file's place in the project's artifacts, paired with its {@code files} part <b>by
     * position</b>.
     *
     * @param artifactId adds a version to this existing artifact, which must belong to the same
     *        project; an id from another project is a 404, never a cross-project link
     * @param name always creates a <b>new</b> artifact under this name — a name is a current label,
     *        not something to look an artifact up by, so posting a name that already exists in the
     *        project makes a second artifact rather than a version of the first
     */
    public record ArtifactAttachment(
            @Schema(description = "Existing artifact to add a version to; mutually exclusive with 'name'")
            UUID artifactId,

            @Schema(description = "Name for a new artifact; mutually exclusive with 'artifactId'")
            @Size(max = 300) String name,

            @Schema(description = "What distinguishes this version, e.g. 'Final' or 'v2 after review'")
            @Size(max = 300) String label) {

        /** Whether exactly one of the two ways of naming an artifact was given. */
        public boolean isArtifactNamedExactlyOnce() {
            boolean hasId = artifactId != null;
            boolean hasName = name != null && !name.isBlank();
            return hasId != hasName;
        }
    }

    public List<ArtifactAttachment> attachmentsOrEmpty() {
        return artifacts == null ? List.of() : artifacts;
    }
}
