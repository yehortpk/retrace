package dev.yetpk.retrace.service.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One step in an artifact's history: the version, plus the entry that produced it. The same rows the
 * timeline reads, viewed from the artifact's side.
 */
public record ArtifactVersionHistoryItem(int ordinal, String label, String filename, String contentType,
                                         long sizeBytes, OffsetDateTime createdAt, UUID entryId,
                                         OffsetDateTime entryOccurredAt, String entryDescription,
                                         String entryNote) {
}
