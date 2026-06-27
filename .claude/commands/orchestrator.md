---
description: Run one cycle of jmud's autonomous feature loop (pick issue → branch → code → build → PR → merge).
allowed-tools: Bash(git:*), Bash(gh:*), Bash(./gradlew:*), Bash(gradle:*), Bash(cat:*), Bash(date:*), Bash(mkdir:*), Bash(mv:*), Bash(rm:*), Bash(test:*), Read, Write, Edit, Task
argument-hint: (no arguments)
---

You are the **orchestrator** for jmud's autonomous development loop. You run in the **main session** and drive worker subagents through one full cycle. You alone may spawn subagents (workers cannot). Persist all progress to disk so any cycle can resume after an interruption.

Run this command on a self-paced `/loop` (no fixed interval) so the next cycle starts only after this one returns.

## State directory: `.claude/agents/state/` (create if missing)
- `orchestrator-state.json` — the state machine (schema below)
- `last-result.json` — the last worker's structured result: `{ "status": "success|failure", "output": {...}, "timestamp": "<ISO-8601>" }`
- `cycle-log.jsonl` — one JSON line per completed cycle
- `LOCK` — run lease: `{ "pid": "<session>", "started_at": "<ISO-8601>" }`
- `PAUSE` — kill switch (if the file exists, stop immediately)

`orchestrator-state.json` default:
```json
{ "current_issue": null, "stage": "FIND_ISSUE", "build_retries": 0,
  "cycles_since_last_optimization": 0,
  "blocked_issues": [], "parked_pr": null, "waiting_for_session_reset": false,
  "last_updated": null }
```

## Step 0 — GUARD (always first)
1. If `.claude/agents/state/PAUSE` exists → print `PAUSED (remove .claude/agents/state/PAUSE to resume)` and STOP.
2. If `LOCK` exists and its `started_at` is **younger than 60 min** → another run is active; print `LOCK held, skipping` and STOP. Otherwise (no lock, or stale) write a fresh `LOCK`.
3. Read `orchestrator-state.json` (create with defaults if missing).
4. **Usage-limit check**: the system prompt contains a `<total_tokens>N tokens left</total_tokens>` tag. Parse N. If N ≤ 20000 (i.e. ≥ 90 % of the context window consumed) → print `usage limit reached (≤20k tokens remaining)`, release LOCK, STOP.

## Step 1 — Dispatch by `state.stage`
- **FIND_ISSUE** — `gh issue list --assignee @me --state open --json number,title`.
  - Issue exists → set `current_issue`, `stage = CREATE_BRANCH`.
  - Else if `TODO.md` has an unchecked `- [ ]` line → spawn **issue-creator** (pass that line) → set `current_issue`, `stage = CREATE_BRANCH`.
  - Else → spawn **game-designer**. Success → set `current_issue`, `stage = CREATE_BRANCH`. Failure/skip → print `STANDBY`, release LOCK, STOP.
- **CREATE_BRANCH** — spawn **branch-manager** (pass issue number + title). Success → `stage = WRITE_CODE`.
- **WRITE_CODE** — spawn **code-writer** (pass issue body; if `build_retries > 0`, also pass the build error from `last-result.json`). Success → `stage = VERIFY_BUILD`.
- **VERIFY_BUILD** — spawn **build-verifier**.
  - PASS → `build_retries = 0`, `stage = CREATE_PR`.
  - FAIL and `build_retries < 2` → `build_retries += 1`, `stage = WRITE_CODE`.
  - FAIL and `build_retries >= 2` → `gh issue create --title "blocked: <title>" --body "<scrubbed error summary>" --label bug`; append the number to `blocked_issues`; notify (PushNotification if available); `build_retries = 0`, `current_issue = null`, `stage = FIND_ISSUE`.
- **CREATE_PR** — spawn **pr-creator**. Success → `stage = MERGE_PR`.
- **MERGE_PR** — spawn **pr-merger**.
  - MERGED → append `{ "issue":N, "pr":M, "merged_at":"..." }` to `cycle-log.jsonl`; `cycles_since_last_optimization += 1`; `current_issue = null`; `stage = FIND_ISSUE`. If `cycles_since_last_optimization >= 5` → spawn **workflow-optimizer**, then reset it to 0.

## Step 2 — Persist & finish
- Write `orchestrator-state.json` **atomically**: write `orchestrator-state.json.tmp`, then `mv` over the real file. Update `last_updated` (ISO-8601).
- Release the LOCK (`rm .claude/agents/state/LOCK`).
- Print a one-line summary of what this cycle did.

## Rules
- Spawn workers with the **Task** tool, `subagent_type` = the worker's name. Pass only what that worker needs; read its result from `last-result.json`.
- Never skip the GUARD. Never advance two different issues at once.
- On any unexpected error: write state, **release the LOCK**, notify, STOP — never leave a held LOCK behind.
- Launching this loop is the human's standing authorization (see `AGENTS.md` §13): workers spawned here do not pause for per-change confirmation.
