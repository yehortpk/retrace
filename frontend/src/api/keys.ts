import { useQuery } from '@tanstack/react-query';
import { request } from './client.ts';
import type { ApiKeyView, IssuedKeyResponse } from './types.ts';

export const keysKey = ['keys'] as const;

export function findKeys() {
  return request<ApiKeyView[]>('/api/keys');
}

export function issueKey(name: string) {
  return request<IssuedKeyResponse>('/api/keys', { method: 'POST', json: { name } });
}

export function revokeKey(keyId: string) {
  return request<void>(`/api/keys/${encodeURIComponent(keyId)}`, { method: 'DELETE' });
}

export function useKeys() {
  return useQuery({ queryKey: keysKey, queryFn: findKeys });
}
