package dev.yetpk.retrace.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Who the caller is, and — under an API key — which project the key belongs to. */
public record CurrentUserView(
        UUID id,
        String username,
        String email,
        @Schema(description = "The project the presenting API key belongs to; null under a session",
                nullable = true)
        UUID projectId) {
}
