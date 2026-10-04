package dev.yetpk.retrace.web.dto;

import java.time.OffsetDateTime;

/**
 * An API key as it can be listed: its public id, its name, and when it was made and last used.
 * The key's value is deliberately absent — after issuance it does not exist anywhere to show.
 */
public record ApiKeyView(String id, String name, OffsetDateTime createdAt, OffsetDateTime lastUsedAt) {
}
