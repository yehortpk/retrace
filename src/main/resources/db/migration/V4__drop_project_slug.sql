-- A project is addressed by its UUID everywhere: in REST paths and in the agent's
-- PROJECT_MEMORY_PROJECT binding. The slug was a second, mutable handle for the same row, so it is
-- dropped rather than kept as a label nothing resolves by.
ALTER TABLE project DROP CONSTRAINT project_owner_id_slug_key;
ALTER TABLE project DROP COLUMN slug;
