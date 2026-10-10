import { useQuery } from '@tanstack/react-query';
import { ApiError, request } from './client.ts';
import type { ArtifactHistory, ArtifactSummary } from './types.ts';

export function artifactsKey(projectId: string) {
  return ['artifacts', projectId] as const;
}

export function artifactHistoryKey(projectId: string, artifactId: string) {
  return ['artifacts', projectId, artifactId] as const;
}

export function findArtifacts(projectId: string) {
  return request<ArtifactSummary[]>(`/api/projects/${projectId}/artifacts`);
}

export function findArtifactHistory(projectId: string, artifactId: string) {
  return request<ArtifactHistory>(`/api/projects/${projectId}/artifacts/${artifactId}`);
}

export function renameArtifact(projectId: string, artifactId: string, name: string) {
  return request<ArtifactHistory>(`/api/projects/${projectId}/artifacts/${artifactId}`, {
    method: 'PATCH',
    json: { name },
  });
}

export function deleteVersion(projectId: string, artifactId: string, ordinal: number) {
  return request<void>(`/api/projects/${projectId}/artifacts/${artifactId}/versions/${ordinal}`, {
    method: 'DELETE',
  });
}

/** A plain link target: the session cookie rides along and the server names the file. */
export function toDownloadUrl(projectId: string, artifactId: string, ordinal: number) {
  return `/api/projects/${projectId}/artifacts/${artifactId}/versions/${ordinal}/download`;
}

/** The stored bytes read as text, for previewing a small text file in place. */
export async function findVersionText(projectId: string, artifactId: string, ordinal: number) {
  const response = await fetch(toDownloadUrl(projectId, artifactId, ordinal), { credentials: 'include' });
  if (!response.ok) {
    throw new ApiError(response.status, null);
  }
  return response.text();
}

export function useArtifacts(projectId: string, enabled = true) {
  return useQuery({ queryKey: artifactsKey(projectId), queryFn: () => findArtifacts(projectId), enabled });
}

export function useArtifactHistory(projectId: string, artifactId: string | undefined) {
  return useQuery({
    queryKey: artifactHistoryKey(projectId, artifactId ?? ''),
    queryFn: () => findArtifactHistory(projectId, artifactId!),
    enabled: artifactId !== undefined,
  });
}
