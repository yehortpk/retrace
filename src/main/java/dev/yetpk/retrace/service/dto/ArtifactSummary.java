package dev.yetpk.retrace.service.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An artifact as it appears in a list: its stable identity plus the state of its latest version. */
public record ArtifactSummary(UUID id, String name, OffsetDateTime createdAt, int versionCount,
                              ArtifactVersionView currentVersion) {
}
