package dev.yetpk.retrace.web.dto;

import java.util.UUID;

/** Who the caller is, for a UI that needs to show it. */
public record CurrentUserView(UUID id, String username, String email) {
}
