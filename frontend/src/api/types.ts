// Mirrors the records the controllers in dev.yetpk.retrace.web return and accept. Instants arrive as
// ISO-8601 strings and UUIDs as strings; nothing here is parsed on the way in.

export interface CurrentUser {
  id: string;
  username: string;
  email: string;
  /** The project the presenting API key belongs to; always null for the UI's session. */
  projectId: string | null;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

/** A key belongs to a project, so registering issues none; creating a project does. */
export interface RegisterResponse {
  userId: string;
  username: string;
  email: string;
}

export interface ProjectSummary {
  id: string;
  name: string;
  description: string | null;
  entryCount: number;
}

export interface CreateProjectRequest {
  name: string;
  description: string | null;
}

/** The only response that ever carries the new project's first key in plaintext. */
export interface CreatedProjectResponse {
  project: ProjectSummary;
  key: IssuedKeyResponse;
}

export interface ArtifactVersionView {
  artifactId: string;
  artifactName: string;
  ordinal: number;
  label: string | null;
  filename: string | null;
  contentType: string | null;
  sizeBytes: number;
  createdAt: string;
}

export interface TimelineEntry {
  id: string;
  occurredAt: string;
  description: string;
  note: string | null;
  sessionId: string | null;
  versions: ArtifactVersionView[];
}

/** One file's place in the project's artifacts: exactly one of artifactId or name. */
export interface ArtifactAttachment {
  artifactId?: string;
  name?: string;
  label?: string;
}

export interface RecordEntryRequest {
  occurredAt: string | null;
  description: string;
  note: string | null;
  sessionId: string | null;
  artifacts: ArtifactAttachment[];
}

export interface ArtifactSummary {
  id: string;
  name: string;
  createdAt: string;
  versionCount: number;
  currentVersion: ArtifactVersionView;
}

export interface ArtifactVersionHistoryItem {
  ordinal: number;
  label: string | null;
  filename: string | null;
  contentType: string | null;
  sizeBytes: number;
  createdAt: string;
  entryId: string;
  entryOccurredAt: string;
  entryDescription: string;
  entryNote: string | null;
}

/** An artifact and its full version history, oldest first. */
export interface ArtifactHistory {
  id: string;
  name: string;
  createdAt: string;
  versions: ArtifactVersionHistoryItem[];
}

export interface ApiKeyView {
  id: string;
  name: string;
  createdAt: string;
  lastUsedAt: string | null;
}

/** A newly issued key, carrying the one and only copy of its plaintext value. */
export interface IssuedKeyResponse {
  id: string;
  name: string;
  apiKey: string;
}

/** RFC 7807 body produced by ApiExceptionHandler. */
export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
  requestedBytes?: number;
  remainingBytes?: number;
}
