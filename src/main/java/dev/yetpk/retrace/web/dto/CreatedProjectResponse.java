package dev.yetpk.retrace.web.dto;

import dev.yetpk.retrace.service.dto.ProjectSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A new project and the key issued with it. This is the <b>only</b> response that ever carries that
 * key's plaintext: only a bcrypt hash is stored, so a key not saved here is lost and a new one has to
 * be issued from the project's keys.
 */
public record CreatedProjectResponse(
        ProjectSummary project,
        @Schema(description = "The project's first API key, shown exactly once. It reaches this project "
                + "and no other.")
        IssuedKeyResponse key) {
}
