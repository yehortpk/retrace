CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

CREATE TABLE app_user (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  username      text NOT NULL UNIQUE,
  email         text NOT NULL UNIQUE,
  password_hash text NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now()
);

-- An API key is issued automatically at registration (see RegistrationService).
CREATE TABLE api_key (
  id           text PRIMARY KEY,                             -- public id, e.g. 'pm_a1b2c3d4e5f6'
  owner_id     uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name         text NOT NULL,                                -- 'Default key' at registration
  secret_hash  text NOT NULL,                                -- bcrypt of the secret half
  created_at   timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz,
  revoked_at   timestamptz
);
CREATE INDEX idx_api_key_owner ON api_key(owner_id) WHERE revoked_at IS NULL;

CREATE TABLE project (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id    uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name        text NOT NULL,
  slug        text NOT NULL,                                 -- stable handle for agents
  description text,
  created_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (owner_id, name),
  UNIQUE (owner_id, slug)
);

CREATE TABLE entry (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  occurred_at timestamptz NOT NULL,
  description text NOT NULL,                                 -- what happened
  note        text,                                          -- why / context
  session_id  text,                                          -- Claude Code session_id, for grouping
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_entry_project_time ON entry(project_id, occurred_at DESC, id DESC);
CREATE INDEX idx_entry_session ON entry(session_id) WHERE session_id IS NOT NULL;

CREATE TABLE artifact (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),    -- immutable identity
  project_id   uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  entry_id     uuid NOT NULL REFERENCES entry(id) ON DELETE CASCADE,
  name         text NOT NULL,                                 -- RENAMEABLE label
  storage_key  uuid NOT NULL UNIQUE,                           -- the file's name in storage
  filename     text NOT NULL,                                 -- original name, for download
  content_type text NOT NULL,
  size_bytes   bigint NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (project_id, name)                                    -- lookup convenience, NOT identity
);
CREATE INDEX idx_artifact_entry ON artifact(entry_id);
