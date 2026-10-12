package dev.yetpk.retrace.web.dto;

import dev.yetpk.retrace.security.IssuedApiKey;
import io.swagger.v3.oas.annotations.media.Schema;

/** A newly issued key, carrying the one and only copy of its plaintext value. */
public record IssuedKeyResponse(
        String id,
        String name,
        @Schema(description = "The full API key, shown exactly once and never retrievable again")
        String apiKey) {

    public static IssuedKeyResponse from(IssuedApiKey issued) {
        return new IssuedKeyResponse(issued.key().getId(), issued.key().getName(), issued.value());
    }
}
