---
description: Reads the logs of a failed CI run and proposes a fix
argument-hint: <run-id> (from GitHub Actions)
allowed-tools: Bash(gh:*), Bash(git:*), Bash(./mvnw:*)
---

Run **$ARGUMENTS** of GitHub Actions failed. Let's diagnose and fix it.

1. **Fetch the run logs:**
   ```bash
   gh run view $ARGUMENTS --log-failed
   ```

2. **Identify the failure type:**
    - Compilation failure → read the class and compiler message
    - Test failure → read test name, failing assertion, stack trace
    - Container build failure → error in Dockerfile/Buildpack
    - Coverage below minimum → identify uncovered files

3. **For each failure, propose a fix** by presenting:
    - Root cause (one sentence)
    - File and line affected
    - Proposed change (diff)

4. **Wait for approval** before applying the change.

5. **After approval:**
    - Apply the fix
    - Run only the relevant subset locally (e.g., the failing test)
    - If it passes, commit with message `fix(ci): <short description>`
    - `git push`

6. **Report** back to the dev and link the new CI run when it appears.

**Important:** if the failure indicates a problem outside the code (CI infra,
Docker offline, cache issue), **DO NOT** modify code — only report the
diagnosis to the dev.