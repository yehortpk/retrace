import { useQuery } from '@tanstack/react-query';
import { request } from './client.ts';
import type { CurrentUser, RegisterRequest, RegisterResponse } from './types.ts';

export const currentUserKey = ['current-user'] as const;

export function register(registration: RegisterRequest) {
  return request<RegisterResponse>('/api/auth/register', { method: 'POST', json: registration });
}

/** Spring Security's form login: url-encoded fields, 204 on success, 401 on bad credentials. */
export function logIn(username: string, password: string) {
  return request<void>('/api/auth/login', {
    method: 'POST',
    body: new URLSearchParams({ username, password }),
  });
}

export function logOut() {
  return request<void>('/api/auth/logout', { method: 'POST' });
}

export function findCurrentUser() {
  return request<CurrentUser>('/api/auth/me');
}

export function useCurrentUser() {
  return useQuery({ queryKey: currentUserKey, queryFn: findCurrentUser, staleTime: Infinity });
}
