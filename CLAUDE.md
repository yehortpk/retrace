# Project Memory

See `PLAN.md` (gitignored, not part of the repo) for the full implementation plan.

## Rules

- Do not execute git operations (add, commit, push, reset, branch, etc.) unless the user explicitly asks for that specific operation in that message.
- In Spring Data repositories, do not rely on long derived query method names (e.g. `findByProjectIdAndOccurredAtAfterOrderByOccurredAtDescIdDesc`). Prefer a short method name with an explicit `@Query` JPQL annotation instead.
