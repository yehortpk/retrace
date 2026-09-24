-- Remaining bytes out of retrace.artifacts.project-storage-limit, decremented as versions are
-- stored rather than derived by summing artifact_version.size_bytes. NULL by default; the
-- application sets it explicitly (from the configured limit) when a project is created.
ALTER TABLE project
  ADD COLUMN artifact_storage_remaining_bytes bigint
    CHECK (artifact_storage_remaining_bytes >= 0);
