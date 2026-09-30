-- An artifact is identified by its UUID; the name is a mutable current label that nothing looks up
-- by. Keeping the name unique would make a rename fail on a collision that no longer means anything.
ALTER TABLE artifact DROP CONSTRAINT artifact_project_id_name_key;
