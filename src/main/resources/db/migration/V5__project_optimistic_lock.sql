-- Concurrent writes to one project race on its artifact storage remainder. The version column backs
-- JPA optimistic locking: a second writer that read the same row fails on commit instead of
-- silently overwriting the first writer's decrement.
ALTER TABLE project ADD COLUMN version bigint NOT NULL DEFAULT 0;
