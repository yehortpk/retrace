import type { ProblemDetail } from './types.ts';

/**
 * A failed call, carrying the server's ProblemDetail when there was one. `message` is already the
 * most useful thing to show a person: the problem's detail, else its title, else the status.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`);
    this.status = status;
    this.problem = problem;
  }
}

/** One line for a person: a validation failure's field messages when there are any, else the message. */
export function describeError(error: Error) {
  const fieldErrors = error instanceof ApiError ? error.problem?.errors : undefined;
  if (fieldErrors && Object.keys(fieldErrors).length > 0) {
    return Object.entries(fieldErrors).map(([field, message]) => `${field}: ${message}`).join('; ');
  }
  return error.message;
}

const XSRF_COOKIE = 'XSRF-TOKEN';
const XSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

// A 401 from these is an answer about the credentials just offered, not a session that ran out,
// so it must reach the form that offered them instead of sending the browser back to /login.
const CREDENTIAL_PATHS = new Set(['/api/auth/login', '/api/auth/register']);

let unauthorizedListener: (() => void) | null = null;

/** Registers what happens when the session is missing or expired. One listener; the app shell sets it. */
export function setUnauthorizedListener(listener: () => void) {
  unauthorizedListener = listener;
}

interface RequestOptions {
  method?: string;
  json?: unknown;
  body?: BodyInit;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers = new Headers({ Accept: 'application/json' });
  let body = options.body;
  if (options.json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(options.json);
  }
  if (!SAFE_METHODS.has(method)) {
    const token = await findCsrfToken();
    if (token) {
      headers.set(XSRF_HEADER, token);
    }
  }

  const response = await fetch(path, { method, headers, body, credentials: 'include' });
  if (!response.ok) {
    if (response.status === 401 && !CREDENTIAL_PATHS.has(path)) {
      unauthorizedListener?.();
    }
    throw new ApiError(response.status, await readProblem(response));
  }
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/**
 * The XSRF-TOKEN cookie is written on any response, but signing in replaces the token and clears
 * the cookie until the next request. A write issued straight after login would otherwise go out
 * without one, so a cheap read is made first to have the server issue it.
 */
async function findCsrfToken(): Promise<string | null> {
  const existing = readCookie(XSRF_COOKIE);
  if (existing) {
    return existing;
  }
  await fetch('/api/auth/me', { credentials: 'include' }).catch(() => undefined);
  return readCookie(XSRF_COOKIE);
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  const match = document.cookie.split('; ').find(cookie => cookie.startsWith(prefix));
  return match ? decodeURIComponent(match.slice(prefix.length)) : null;
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('json')) {
    return null;
  }
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return null;
  }
}
