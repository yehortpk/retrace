# Retrace — Claude Code plugin

Records a chronological history of a project as the agent works, into a [Retrace](../README.md) timeline.

The plugin is markdown only: a skill, a `Stop` hook and two commands. There is no code to build and no runtime beyond the agent itself — summarising what a turn accomplished is the one part of the job that needs a model, and everything else is one `curl`.

## Install

Copy this directory into `.claude/plugins/` in a project, or into `~/.claude/plugins/` to have it available everywhere. It stays dormant in projects that are not bound to Retrace.

## Setup

Create a project in Retrace. Its API key is shown **once**, with the command to run:

```
/init-retrace <your-api-key>
```

That verifies the key against the service, writes it to `.retracekey` in the project root, `chmod 600`s it, adds it to `.gitignore`, and tells you which project it bound — so a key pasted from the wrong project is caught before anything is recorded.

You can also skip the command and create `.retracekey` by hand: the project's API key on one line, nothing else. Then gitignore it.

A project with no `.retracekey` records nothing, silently. That is the default and needs no opt-out.

## Configuration

| What | Where | Who sets it |
|---|---|---|
| Service URL | the `## Endpoint` section of [SKILL.md](skills/retrace/SKILL.md) | shipped with the plugin |
| | `$RETRACE_URL`, if set, overrides it | self-hosters, CI |
| Project API key | `.retracekey` in the project root | you, once per project |

**One key per project, one file per project.** The key is scoped to a single project and cannot reach another, so several Retrace projects on one machine stay apart with no further configuration — which an environment variable could not do, since it holds one value for every project at once.

### About the key file

`.retracekey` is a plaintext file, gitignored and mode `600` — the same posture as `~/.aws/credentials` or an `.npmrc` token. It is readable by your own user and by anything running as you. That is the honest ceiling for a plugin with no runtime of its own; if that is not enough for your threat model, keep the key out of the repo and set `$RETRACE_URL` and the key in CI secrets instead.

If you pass the key to `/init-retrace` as an argument, it is written to the conversation transcript on disk. Keys are revocable from the Retrace UI.

## What gets recorded

After each turn, the `Stop` hook asks the model whether anything happened worth remembering weeks later — a feature that now works, a decision and its reasoning, something delivered, a dead end worth not repeating. Routine mechanics are not recorded, and **most turns record nothing**. A timeline full of trivia is worse than one with gaps.

`/retrace [what happened]` records an entry by hand, for the moments that happen away from the keyboard — a client call, a decision taken in a meeting. These are not a separate kind of history: same endpoint, same structure, same rows, with nothing marking which side wrote them.

Files are never attached automatically. Artifact versions pair positionally with their metadata, and getting that wrong corrupts an artifact's history, so attaching happens only when you ask for it.

## Other agents

Claude Code only, for now. The skill itself is plain prose and nothing in it is Claude-specific, so another agent can follow it if pointed at the file — but automatic recording needs a hook, and Codex, Gemini CLI and Cursor each spell that differently. Codex and Gemini cannot run a model-backed hook at all, so on those it would take a shell script to decide when to record.

That is a real amount of per-agent surface to keep working, and none of it is testable here yet. When it is worth doing, the shape is: an `AGENTS.md` pointing at the skill, plus one small script and one config per agent.

## Files

| File | Purpose |
|---|---|
| [skills/retrace/SKILL.md](skills/retrace/SKILL.md) | endpoint, key handling, request shapes, what deserves an entry, and `/retrace` for recording by hand |
| [hooks/hooks.json](hooks/hooks.json) | the `Stop` hook that offers an entry after each turn |
| [skills/init-retrace/SKILL.md](skills/init-retrace/SKILL.md) | `/init-retrace`: bind a project to its key (manual only) |
