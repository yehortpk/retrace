package dev.yetpk.retrace.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A registered account and its first API key.
 *
 * @param apiKey the full credential to send as {@code X-API-Key}. This is the <b>only</b> response
 *        that ever contains it: only a bcrypt hash is stored, so a key not saved here is lost and a
 *        new one has to be issued.
 */
public record RegisterResponse(
        UUID userId,
        String username,
        String email,
        String apiKeyId,
        @Schema(description = "The full API key, shown exactly once and never retrievable again")
        String apiKey) {
}
