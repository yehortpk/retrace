---
name: retrace
description: Record a moment in this project's Retrace history — a chronological log of what happened, written as the work happens. Use when a turn produced something worth remembering weeks later (a feature that now works, a decision and its reasoning, something delivered, a dead end worth not repeating), or when the user asks to record, log, or note something in the project history. Also use when asked what has happened in a project lately, to read the timeline back.
---

# Retrace

Retrace is a chronological history layer for a project. An entry is `timestamp + description + optional note`. There are deliberately no entry types and no trace of who wrote an entry — history written by an agent and history typed by a person are the same rows.

## Endpoint

The service base URL. **This is the only place in the plugin that states it** — change this line and nothing else when the service moves.

```
http://localhost:8080
```

If `$RETRACE_URL` is set in the environment, use that instead. Self-hosters and CI set it; nobody else needs to.

## The key

The project's API key lives in a file called `.retracekey` in the project root — the key on one line, nothing else. One file per project, so a machine with several Retrace projects keeps them apart with no further configuration.

Read it at the moment you need it:

```sh
cat .retracekey
```

Rules, in order:

1. **No `.retracekey`, no history.** If the file does not exist, this project is not bound to Retrace. Do nothing, say nothing, and do not offer to set it up — most projects are not Retrace projects and a prompt on every turn is noise. The exception is when the user explicitly asks for Retrace or runs `/retrace`: then tell them to run `/init-retrace`, or to create `.retracekey` by hand, and that without it there is no history recording.
2. **Never print the key.** Not in a message, not in a command you show the user, not in a summary of what you did. Pass it to `curl` by reading the file inside the command.
3. **Never commit it.** It belongs in `.gitignore`. `/init-retrace` puts it there; if you notice it tracked, say so.
4. **One failure, then stop.** If a request is rejected, report it once in a sentence and carry on with the real work. Never retry in a loop, and never let a history failure block or derail what the user actually asked for.

## Recording an entry

One `POST`, `multipart/form-data`:

```sh
curl -fsS -X POST "$RETRACE_URL/api/projects/$PROJECT_ID/entries" \
  -H "X-API-Key: $(cat .retracekey)" \
  -F 'entry={"description":"Replaced the shell-based plugin with a markdown-only one.","note":"The scripts could not summarise, which is the only part a model is needed for."};type=application/json'
```

`$PROJECT_ID` comes from the key, not from you: call `GET /api/auth/me` with the key and read `projectId` from the response. The key is scoped to exactly one project and cannot reach another, so this is also the check that the key is live — if `/api/auth/me` rejects it, stop there and report it.

### What goes in an entry

- **`description`** — what occurred, past tense, in the project's own terms. A person reading it in six months should understand what changed without opening the diff. Not "ran tests and edited three files" but "the timeline now pages by `since` instead of offset, so a slow client cannot miss entries".
- **`note`** — optional, and the most valuable field. The *why*: the reasoning, the constraint, the thing that would otherwise be lost. A decision without its reason is half an entry.

Write for the project's history, not as a report to the user. No tool names, no file counts, no "I did X then Y".

### When to record

Record a moment someone would want to read weeks later:

- a feature or fix that now works
- a decision taken, and why
- something produced or delivered
- a dead end worth not walking into twice

Do **not** record: reading or searching files, a question answered, a typo, a passing test run, work still mid-flight, or anything you would struggle to describe without naming a tool. **Silence is the common and correct outcome.** A timeline full of trivia is worse than one with gaps — every junk entry makes the real history harder to read.

One turn is at most one entry. If a turn did several things, write the one entry that says what the turn amounted to.

### Artifacts

The API can version files attached to an entry, where `files` parts pair **positionally** with `entry.artifacts`. Do not do this automatically. Get the pairing wrong and an artifact's history records the wrong label against the wrong version, which is worse than no record at all. Attach a file only when the user explicitly asks, with one file and one artifact at a time:

```sh
curl -fsS -X POST "$RETRACE_URL/api/projects/$PROJECT_ID/entries" \
  -H "X-API-Key: $(cat .retracekey)" \
  -F 'entry={"description":"Sent the pricing page to the client.","artifacts":[{"name":"pricing-page","label":"v2"}]};type=application/json' \
  -F 'files=@/abs/path/pricing.png'
```

A `name` creates a new artifact; an `artifactId` adds a version to an existing one. Check `GET /api/projects/$PROJECT_ID/artifacts` before creating, so a second version does not become a second artifact.

## Reading history back

When the user asks what has happened lately:

```sh
curl -fsS "$RETRACE_URL/api/projects/$PROJECT_ID/timeline?size=20" \
  -H "X-API-Key: $(cat .retracekey)"
```

`since` takes a timestamp for a narrower window. Summarise what comes back in prose — the user wants the story, not the JSON.
