package dev.yetpk.retrace.web.dto;

import java.util.UUID;

/**
 * A registered account. It carries no API key: a key belongs to a project, and creating one issues
 * its first key.
 */
public record RegisterResponse(UUID userId, String username, String email) {
}
