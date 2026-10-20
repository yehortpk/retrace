# Retrace

A chronological history layer for a solo project — written by the coding agent as it works.

A project's history ends up scattered across files, screenshots, chat logs, and personal memory.
Weeks later you cannot reconstruct *what happened, what changed, what was produced, and why*. Task
managers track work *to do*, not what *occurred*. Retrace records what occurred, so you can open a
project months later and read its history end to end — including how each artifact evolved version
by version.

The core bet is that **the agent does the remembering**. As a coding agent works, meaningful moments
are recorded automatically. Entries you write by hand — a client call, a decision in a meeting — are
not merely similar but **identical**: same endpoint, same structure, same stored row, with nothing
recording which side wrote it.

## Domain model

An **Entry** is `timestamp + description + optional note + optional artifacts`. There are
deliberately **no entry types**.

An **Artifact** has a UUID assigned at creation that never changes, and is **versioned** — each
version links to the entry that produced it. Renaming an artifact changes its label, never its
identity or its history. The same rows answer both *what happened over time?* (the timeline) and
*how did this evolve?* (artifact history).

Non-goals: project management, task tracking, time tracking, invoicing, CRM, approvals, team
collaboration.

## Stack

| Part | Choice |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Gradle (Kotlin DSL, committed wrapper) |
| Database | PostgreSQL 17 — native `uuid` and `timestamptz`, Hibernate + Flyway |
| Dev database | Docker Compose, auto-started by `spring-boot-docker-compose` |
| Frontend | React 19 + Vite + TypeScript, TanStack Query, React Router |
| Auth | One API key per project (`X-API-Key`) for agents, form-login session for the UI |
| Agent integration | A markdown-only Claude Code plugin (`agent-plugin/`) — skill, hook, commands |
| API docs | springdoc — `/v3/api-docs`, `/swagger-ui.html` |

## Getting started

Prerequisites: JDK 21, Node 22, Docker.

```sh
./gradlew bootRun                           # starts Postgres via compose, runs Flyway, serves :8080
cd frontend && npm install && npm run dev   # :5173, proxies /api to :8080
```

Then register at <http://localhost:5173/register> and create a project. The project's API key is
shown **once**, together with the `/init-retrace` command to paste into the agent for binding a
repository.

Packaged:

```sh
cd frontend && npm run build && cd ..
./gradlew clean bootJar
java -jar build/libs/retrace-0.1.0.jar      # SPA + API on one origin
```

Tests run against the compose Postgres under the `test` profile:

```sh
./gradlew test
```

## Configuration

Defaults live in [application.yml](src/main/resources/application.yml); everything below is
overridable by environment variable.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/retrace` | datasource |
| `DB_USER` / `DB_PASSWORD` | `retrace` / `retrace` | datasource credentials |
| `STORAGE_ROOT` | `./storage` | where artifact version files are written |
| `retrace.artifacts.project-storage-limit` | `500MB` | cumulative artifact bytes per project |

## API

One API, two ways to authenticate. The UI signs in with a form login and a session; an agent sends
an `X-API-Key` header. Authentication establishes **who owns the data and nothing else** — an entry
carries no trace of which credential created it.

Every project-scoped path is nested under its project UUID, and an artifact is never reachable
outside its project: an id from another project reads as 404, never 403. Errors are RFC 7807
`ProblemDetail`.

| Method | Path | Auth |
|---|---|---|
| `POST` | `/api/auth/register` | public |
| `POST` | `/api/auth/login`, `/api/auth/logout` | public |
| `GET` | `/api/auth/me` | either — carries `projectId` under key auth |
| `GET` `POST` | `/api/projects` | session only |
| `GET` `POST` `DELETE` | `/api/projects/{projectId}/keys[/{keyId}]` | session only |
| `GET` | `/api/projects/{projectId}/timeline?page=&size=&since=` | either |
| `POST` | `/api/projects/{projectId}/entries` | either |
| `GET` | `/api/projects/{projectId}/artifacts` | either |
| `GET` `PATCH` | `/api/projects/{projectId}/artifacts/{artifactId}` | either |
| `GET` | `…/artifacts/{artifactId}/versions/{ordinal}/download` | either |
| `DELETE` | `…/artifacts/{artifactId}/versions/{ordinal}` | either |

A key cannot mint or revoke keys, list the owner's other projects, or reach another project.

### Recording an entry

`POST …/entries` is `multipart/form-data` for both callers — one write path, so agent history and
manual history cannot drift apart. The `files` parts pair **positionally** with `entry.artifacts`.

```sh
curl -fsS -X POST "$URL/api/projects/$PROJECT_ID/entries" \
  -H "X-API-Key: $API_KEY" \
  -F 'entry={"description":"Shipped the pricing page.","note":"Client asked for annual plans first.","artifacts":[{"name":"pricing-page","label":"Final"}]};type=application/json' \
  -F 'files=@/abs/path/pricing.png'
```

Passing an `artifactId` adds a version to that artifact; passing a `name` creates a new one. The
server copies the uploaded bytes, so history survives the original file moving.

## Agent integration

The integration is markdown: a skill, a `Stop` hook and two commands in
[agent-plugin/](agent-plugin/) — see its [README](agent-plugin/README.md). There is no code to build
and no runtime beyond the agent itself.

- **A project is bound** by `.retracekey` in its root, holding that project's API key — written by
  `/init-retrace <key>`, which verifies the key against the service first and names the project it
  bound. One key per project, one file per project, so several projects on one machine stay apart.
- **At each stop** a prompt hook asks the agent whether the turn produced a moment worth reading
  weeks later. Most turns do not, and recording nothing is the common and correct outcome. A project
  with no `.retracekey` is silent.
- **The agent writes the entry** and sends it, with the key read from the file at the moment of the
  call. A failed send is reported once and never retried, so history never blocks the work.

The agent supplies the judgment, because it has the whole conversation and is the only one that
knows *why*. That is also the only part a script could not do, which is why there is no longer a
script: summarising is the job, and everything else is one `curl`.

Claude Code only, for now. The skill is plain prose and ports to any agent that reads instructions,
but automatic recording needs a hook, and Codex, Gemini CLI and Cursor each spell that differently —
the first two cannot run a model-backed hook at all.

## Layout

```
src/main/java/dev/yetpk/retrace/
  config/      SecurityConfig, OpenApiConfig, AsyncConfig
  domain/      AppUser, ApiKey, Project, Entry, Artifact, ArtifactVersion
  repo/        Spring Data JPA repositories
  security/    API-key filter and service, registration, current user/project resolvers
  service/     FileStore, quota, ProjectService, TimelineService, ArtifactService, dto/, error/
  web/         REST controllers + request/response DTOs
src/main/resources/db/migration/   V1…V7
frontend/      Vite + React + TypeScript
agent-plugin/  skill, Stop hook, and commands — markdown only, nothing to build
storage/       artifact version files (gitignored)
```

## Status

Backend, web UI, and the Claude Code plugin are in place: persistence, services, REST API,
project-scoped API keys, and the React app over the API. Still ahead — showing the `/init-retrace`
command alongside the key that project creation already returns, plugin publishing, support for
other agents, a deliberate logging pass, and full-stack integration tests on Testcontainers.
