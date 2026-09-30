package dev.yetpk.retrace.service.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** One thing that happened, with whatever artifact versions it produced. */
public record TimelineEntryView(UUID id, OffsetDateTime occurredAt, String description, String note,
                                String sessionId, List<ArtifactVersionView> versions) {
}
