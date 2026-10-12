-- An API key belongs to one project rather than to a user: its blast radius is that project, revoking
-- it disconnects exactly one repo, and the key alone says where entries go. The owner is reached
-- through project.owner_id, so there is no second column that could disagree with it.
--
-- User-wide keys have no project to move to; nothing outside dev holds one.
DELETE FROM api_key;
DROP INDEX idx_api_key_owner;
ALTER TABLE api_key DROP COLUMN owner_id;
-- Deleting a project deletes its keys.
ALTER TABLE api_key ADD COLUMN project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE;
CREATE INDEX idx_api_key_project ON api_key(project_id) WHERE revoked_at IS NULL;
