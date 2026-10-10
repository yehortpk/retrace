import { useInfiniteQuery } from '@tanstack/react-query';
import { request } from './client.ts';
import type { RecordEntryRequest, TimelineEntry } from './types.ts';

const PAGE_SIZE = 30;

export function timelineKey(projectId: string) {
  return ['timeline', projectId] as const;
}

export function findTimeline(projectId: string, page: number, size = PAGE_SIZE) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return request<TimelineEntry[]>(`/api/projects/${projectId}/timeline?${query}`);
}

/**
 * Multipart, with the entry as an `application/json` part and `files[i]` paired with
 * `entry.artifacts[i]` by position — the order of the two arrays is the whole contract.
 */
export function recordEntry(projectId: string, entry: RecordEntryRequest, files: File[]) {
  const body = new FormData();
  body.append('entry', new Blob([JSON.stringify(entry)], { type: 'application/json' }));
  files.forEach(file => body.append('files', file, file.name));
  return request<TimelineEntry>(`/api/projects/${projectId}/entries`, { method: 'POST', body });
}

/**
 * The timeline endpoint returns a bare page with no total, so a short page is how the end is
 * recognised. Refetching on focus is left on: the agent writes into this list while it is open.
 */
export function useTimeline(projectId: string) {
  return useInfiniteQuery({
    queryKey: timelineKey(projectId),
    queryFn: ({ pageParam }) => findTimeline(projectId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) => (lastPage.length < PAGE_SIZE ? undefined : allPages.length),
  });
}
