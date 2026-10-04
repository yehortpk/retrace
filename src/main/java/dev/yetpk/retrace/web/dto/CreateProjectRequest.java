package dev.yetpk.retrace.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new project. Its UUID is its only handle; nothing is derived from the name. */
public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description) {
}
