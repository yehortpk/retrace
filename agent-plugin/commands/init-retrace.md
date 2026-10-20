---
description: Bind this project to its Retrace history by storing its API key
argument-hint: <api-key> | <path-to-file-holding-the-key>
---

Bind this repository to a Retrace project. `$ARGUMENTS` is either the project's API key, or a path to a file that holds it.

Do this in order, and stop at the first step that fails.

1. **Get the key.** If `$ARGUMENTS` is empty, ask for it and stop — tell the user it is shown once when a project is created in Retrace, and that they can pass a file path instead of pasting the key. If `$ARGUMENTS` is a path to an existing file, read the key from it. Otherwise treat `$ARGUMENTS` as the key itself.

2. **Verify it before storing it.** Resolve the base URL as the retrace skill describes, then:

   ```sh
   curl -fsS "$RETRACE_URL/api/auth/me" -H "X-API-Key: <key>"
   ```

   If this fails, report that the key was rejected and write nothing. A key that cannot be verified must never be stored.

3. **Write `.retracekey`** in the project root — the key on one line, nothing else — then `chmod 600` it.

4. **Gitignore it.** If `.retracekey` is not already matched by `.gitignore`, append it. Then confirm it is not tracked:

   ```sh
   git check-ignore -v .retracekey
   ```

5. **Tell the user which project they just bound**, by name or id from the `/api/auth/me` response. This is the step that catches a key pasted from the wrong project, so state it plainly rather than just saying "done".

Then say, in one or two lines, that history recording is now on for this project, and that `/retrace` records an entry by hand.

Never print the key back to the user, and never include it in a command you show them.

**If the key was pasted as an argument, it is now in this conversation's transcript on disk.** Mention this once, and that the key can be revoked and replaced from the Retrace UI if that matters to them.
