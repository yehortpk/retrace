package dev.yetpk.retrace.service.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** An artifact and its full version history, oldest first. */
public record ArtifactHistory(UUID id, String name, OffsetDateTime createdAt,
                              List<ArtifactVersionHistoryItem> versions) {
}
