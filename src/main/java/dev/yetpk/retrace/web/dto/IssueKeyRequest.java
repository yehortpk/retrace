package dev.yetpk.retrace.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A name for a new API key, so a list of keys can be told apart when one needs revoking. */
public record IssueKeyRequest(@NotBlank @Size(max = 100) String name) {
}
