import { useQuery } from '@tanstack/react-query';
import { request } from './client.ts';
import type { ApiKeyView, IssuedKeyResponse } from './types.ts';

/** A key belongs to one project, so every call names it. */
export function keysKey(projectId: string) {
  return ['keys', projectId] as const;
}

function keysPath(projectId: string) {
  return `/api/projects/${encodeURIComponent(projectId)}/keys`;
}

export function findKeys(projectId: string) {
  return request<ApiKeyView[]>(keysPath(projectId));
}

export function issueKey(projectId: string, name: string) {
  return request<IssuedKeyResponse>(keysPath(projectId), { method: 'POST', json: { name } });
}

export function revokeKey(projectId: string, keyId: string) {
  return request<void>(`${keysPath(projectId)}/${encodeURIComponent(keyId)}`, { method: 'DELETE' });
}

export function useKeys(projectId: string) {
  return useQuery({ queryKey: keysKey(projectId), queryFn: () => findKeys(projectId) });
}
