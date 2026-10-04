package dev.yetpk.retrace.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new label for an artifact. Names are not unique within a project: a name is a current label,
 * not an identity, so one already in use is accepted.
 */
public record RenameArtifactRequest(@NotBlank @Size(max = 300) String name) {
}
