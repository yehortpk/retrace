import type { TimelineEntry } from '../api/types.ts';
import { startOfDay, toDayKey } from './format.ts';

/** A run of consecutive entries from one Claude Code session, or recorded outside any session. */
export interface EntryGroup {
  sessionId: string | null;
  entries: TimelineEntry[];
}

export interface TimelineDay {
  key: string;
  date: Date;
  groups: EntryGroup[];
}

/**
 * Splits newest-first entries into local days, then each day into runs that share a session id.
 * Runs, not buckets: two stretches of one session with something else between them stay two groups,
 * because the timeline reads in order and a regrouped session would put entries out of it.
 */
export function groupTimeline(entries: TimelineEntry[]): TimelineDay[] {
  const days: TimelineDay[] = [];
  for (const entry of entries) {
    const occurredAt = new Date(entry.occurredAt);
    const key = toDayKey(occurredAt);
    let day = days.at(-1);
    if (!day || day.key !== key) {
      day = { key, date: startOfDay(occurredAt), groups: [] };
      days.push(day);
    }
    let group = day.groups.at(-1);
    if (!group || group.sessionId !== entry.sessionId) {
      group = { sessionId: entry.sessionId, entries: [] };
      day.groups.push(group);
    }
    group.entries.push(entry);
  }
  return days;
}

/** Joins pages and drops a row a refetch has shifted onto two of them while the agent was writing. */
export function mergeTimelinePages(pages: TimelineEntry[][]): TimelineEntry[] {
  const seen = new Set<string>();
  return pages.flat().filter(entry => {
    if (seen.has(entry.id)) {
      return false;
    }
    seen.add(entry.id);
    return true;
  });
}
