---
description: Pulls a Jira issue and prepares local context to start the feature
argument-hint: <issue-key> (e.g., ECOM-142)
allowed-tools: Bash(git:*), mcp__atlassian__*
---
You will prepare context for implementing issue **$ARGUMENTS**.
Run these steps in order, no skipping:
1. **Fetch the issue from Jira** via Atlassian MCP. Capture:
    - Title, description, acceptance criteria
    - Current status and type (Story, Bug, Task)
    - Assignee, reporter
    - Recent comments (last 5)
2. **Validate preconditions:**
    - Are we on `main` or `develop`? If not, ask for confirmation before proceeding.
    - Any uncommitted changes? If so, **stop** and ask for guidance.
    - Is the issue in a valid state to start? (`To Do`, `Refined`, `Ready`)
3. **Move the issue to "In Progress"** via MCP. Add a short comment:
   `Starting development via Claude Code.`
4. **Create the branch** following the CLAUDE.md convention:
   ​```
   git checkout -b feat/$ARGUMENTS-<title-slug>
   ​```
5. **Generate a technical plan** in `/tmp/plan-$ARGUMENTS.md` containing:
    - Summary of what needs to be done (2-3 paragraphs)
    - Files to create/modify (list)
    - Harness skills that will be consulted
    - Test strategy
    - Identified risks (security, performance, breaking changes)
6. **Present the plan** to the developer and **wait for confirmation**
   before writing any production code.
   DO NOT start coding before the plan is approved. This is the quality gate.