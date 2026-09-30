package dev.yetpk.retrace.repo;

import java.util.UUID;

/** How many entries one project holds, as returned by {@link EntryRepository#countByProjectIdIn}. */
public record ProjectEntryCount(UUID projectId, long entryCount) {
}
