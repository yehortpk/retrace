-- Split artifact into identity (artifact) and content (artifact_version).
-- Each version owns exactly one stored file; the artifact row keeps only the stable id and the renameable name.

CREATE TABLE artifact_version (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  artifact_id  uuid NOT NULL REFERENCES artifact(id) ON DELETE CASCADE,
  entry_id     uuid NOT NULL REFERENCES entry(id) ON DELETE CASCADE,
  ordinal      integer NOT NULL CHECK (ordinal > 0),         -- 1,2,3… dense per artifact
  label        text,                                          -- optional, e.g. 'Final'
  storage_key  uuid NOT NULL UNIQUE,                          -- the file's name in storage
  filename     text NOT NULL,                                 -- original name, for download
  content_type text NOT NULL,
  size_bytes   bigint NOT NULL CHECK (size_bytes >= 0),
  created_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (artifact_id, ordinal)
);
CREATE INDEX idx_av_entry ON artifact_version(entry_id);

-- Carry existing single-file artifacts over as their first version.
INSERT INTO artifact_version (artifact_id, entry_id, ordinal, storage_key, filename, content_type, size_bytes, created_at)
SELECT id, entry_id, 1, storage_key, filename, content_type, size_bytes, created_at
FROM artifact;

DROP INDEX idx_artifact_entry;
ALTER TABLE artifact
  DROP COLUMN entry_id,
  DROP COLUMN storage_key,
  DROP COLUMN filename,
  DROP COLUMN content_type,
  DROP COLUMN size_bytes;
