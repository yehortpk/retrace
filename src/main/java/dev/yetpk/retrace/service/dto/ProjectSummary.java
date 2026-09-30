package dev.yetpk.retrace.service.dto;

import java.util.UUID;

/** A project as it appears in a list: enough to identify it and show how much history it holds. */
public record ProjectSummary(UUID id, String name, String description, long entryCount) {
}
