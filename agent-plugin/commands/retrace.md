---
description: Record an entry in this project's Retrace history by hand
argument-hint: [what happened]
---

Record an entry in this project's Retrace history, using the retrace skill for the endpoint, the key and the request shape.

If `.retracekey` does not exist, this project is not bound to Retrace. Tell the user to run `/init-retrace <key>` to bind it, or to create `.retracekey` by hand with the project's API key on one line — and that until then nothing is recorded and the rest of the work is unaffected. Do not write an entry.

If `$ARGUMENTS` describes what happened, record that. Write it as project history: what occurred, in the past tense, in the project's own terms. Put the reasoning in `note` — a decision without its reason is half an entry. The user's words are the source here, so do not second-guess whether the moment is worth recording; they asked.

If `$ARGUMENTS` is empty, record what this session has done so far, under the editorial rules in the skill. If nothing in the session meets them, say so instead of inventing an entry.

If the user asks for a file to be attached, follow the artifacts section of the skill — one file and one artifact at a time, checking the existing artifacts first so a new version does not become a duplicate artifact.

Confirm what was recorded in one line. Never print the key.
