package dev.yetpk.retrace.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What a new account needs. The issued API key comes back on the response, once. */
public record RegisterRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "At least 8 characters; stored only as a bcrypt hash")
        @NotBlank @Size(min = 8, max = 200) String password) {
}
