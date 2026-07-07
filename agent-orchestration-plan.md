# Plan: Multi-Agent Orchestration System for jmud

> **Status (2026-07-04): historical design document.** The system it describes is built and lives in `.claude/commands/orchestrator.md` + `.claude/agents/*.md` — **those files are authoritative** and have evolved past this plan (issue-priority order, CI gating, architecture guardrails; the mechanical stages — branch/verify/PR/merge — were later converted from agents to deterministic scripts under `scripts/agent/`). Engineering rules: `AGENTS.md` / `CLAUDE.md`. Architecture direction: `docs/architecture-review-and-improvement-plan.md` and issues #168–#189. Do not implement from this document.

## Context

The jmud project (Java 26 MUD game server at `/home/taaniel/repos/jmud`) has no CI/CD pipeline and no automation for its development workflow. The goal is a Claude Code multi-agent system where a persistent **orchestrator loop** (running in the main session) coordinates worker subagents through the full feature lifecycle: pick an issue → branch → implement → build/test → PR → merge → optimize. This replaces manual repetitive steps and creates a self-improving workflow.

> **Important architectural constraint:** In Claude Code, a subagent invoked via the Agent/Task tool **cannot itself spawn further subagents** — nesting is not supported. Therefore the orchestrator must run in the **main session** (as a slash command driven by `/loop`), and only the main session spawns the worker subagents. The orchestrator is *not* a subagent.

---

## File Structure

```
/home/taaniel/repos/jmud/
└── .claude/
    ├── settings.json                   (project-level tool permissions)
    ├── commands/
    │   └── orchestrator.md             (main loop controller — runs in MAIN session, spawns workers)
    └── agents/
        ├── game-designer.md            (decides next best feature, creates GitHub issue)
        ├── issue-creator.md            (creates GitHub issues from TODO/known requirements)
        ├── branch-manager.md           (sync main, create feature branch)
        ├── code-writer.md              (implements Java changes per AGENTS.md)
        ├── build-verifier.md           (runs ./gradlew build + test)
        ├── pr-creator.md               (commit, push, open PR)
        ├── pr-merger.md                (squash-merge after green build)
        ├── workflow-optimizer.md       (analyzes cycles, proposes agent-prompt improvements)
        └── state/                      (gitignored runtime state)
            ├── orchestrator-state.json
            ├── last-result.json
            ├── cycle-log.jsonl
            ├── optimizer-report.md
            ├── LOCK                     (lease file; prevents overlapping runs)
            └── PAUSE                    (kill switch; presence halts the loop)
```

`.gitignore` must include `.claude/agents/state/` (state files are runtime-only). The orchestrator command and the 8 worker agent `.md` files are committed.

---

## Orchestration Flow

The orchestrator is invoked as `/loop /orchestrator` (self-paced — **no fixed interval**, so the next tick only starts after the previous cycle finishes; this avoids overlapping runs). It runs in the main session and uses the Agent tool to spawn each worker subagent in turn.

```
ORCHESTRATOR (main session, self-paced via /loop /orchestrator)
│
├─ [GUARD] If .claude/agents/state/PAUSE exists → exit (kill switch).
│          Acquire LOCK lease (PID + started-at); if a fresh lease is held → exit.
│          If cycles_this_run ≥ max_cycles → exit (budget cap).
│
├─ [FIND_ISSUE] gh issue list --assignee @me --state open
│     ├─ Issue exists → stage = CREATE_BRANCH
│     ├─ No issue, TODO.md has unchecked item → spawn issue-creator → CREATE_BRANCH
│     ├─ Nothing to do → spawn game-designer → CREATE_BRANCH
│     └─ game-designer fails → STANDBY, release LOCK, exit iteration
│
├─ [CREATE_BRANCH] spawn branch-manager
│     └─ git fetch origin → git checkout main → git pull --ff-only
│        → git checkout -b feat/issue-N-slug origin/main
│        (handles dirty tree / pre-existing branch from a crashed cycle)
│
├─ [WRITE_CODE] spawn code-writer
│     └─ reads AGENTS.md, issue body, last-result.json (if retry)
│
├─ [VERIFY_BUILD] spawn build-verifier (./gradlew build && ./gradlew test)
│     ├─ PASS → stage = CREATE_PR
│     └─ FAIL (retries < 2) → stage = WRITE_CODE, increment build_retries
│              (retries >= 2) → open "blocked" GitHub issue, PushNotification, → FIND_ISSUE
│
├─ [CREATE_PR] spawn pr-creator
│     └─ commit + push + gh pr create (Conventional Commits, Closes #N)
│        (idempotent: if branch already pushed / PR already open, reuse it)
│
├─ [MERGE_PR] spawn pr-merger
│     └─ gh pr merge --squash --delete-branch   (gate = the green local build above)
│        → append to cycle-log.jsonl, stage = FIND_ISSUE
│        every 5 cycles → spawn workflow-optimizer
│
└─ [END] release LOCK; loop self-pacing schedules the next cycle
```

No CI exists in this repo, so **the local `build-verifier` step is the only quality gate** before merge. There is no CI polling.

---

## State Management

`orchestrator-state.json` schema:
```json
{
  "current_issue": null,
  "stage": "FIND_ISSUE",
  "build_retries": 0,
  "cycles_since_last_optimization": 0,
  "cycles_this_run": 0,
  "max_cycles": 10,
  "blocked_issues": [],
  "parked_pr": null,
  "waiting_for_session_reset": false,
  "last_updated": "<ISO-8601>"
}
```

Written atomically: `write to .json.tmp → mv to .json`. Subagents communicate via `last-result.json`:
```json
{ "status": "success|failure", "output": { ... }, "timestamp": "..." }
```

Only one worker runs at a time, so `last-result.json` is not contended. The **LOCK lease** (not atomic writes) is what prevents two *orchestrator* runs from racing on state and the single git working tree.

---

## Concurrency & Safety

- **Lease lock** (`state/LOCK`): written at GUARD with `{pid, started_at}`. A run refuses to start if a non-stale lease is held. Stale = older than a configured timeout (e.g., 60 min) so a crashed run self-heals. Always released at END / on early exit.
- **Self-paced loop** (no fixed interval): the primary defense against overlap — the next tick is scheduled only after the current cycle returns. The lease is a backstop.
- **Kill switch** (`state/PAUSE`): create the file to halt the loop after the current cycle; remove it to resume.
- **Budget cap** (`max_cycles`): bounds tokens/$ per run; the loop exits when reached.
- **Failure alerts**: blocked builds and unexpected errors emit a `PushNotification` so a human is informed.
- **Single checkout** (recovery): every stage is idempotent — branch-manager tolerates a pre-existing branch/dirty tree; pr-creator reuses an already-pushed branch or open PR; so a crash mid-stage resumes from the persisted `stage` without duplicating work.

---

## Session / Rate-Limit Handling

The earlier design tried to parse `claude --print /usage` and assumed a short wait "resets the session." That model is incorrect: `/usage` is an interactive view (not reliable in `--print`), it conflates *context-window %* (per conversation) with *rate limits* (5-hour rolling + weekly), and a `/loop` reuses the **same** conversation — waiting does not reset it.

Correct approach:
- **Fresh contexts per cycle**: each worker subagent gets a fresh context, so per-conversation context growth is bounded to the orchestrator's own bookkeeping (kept small — it reads/writes state, not full diffs).
- **Rate limits**: rely on the harness's native rate-limit handling. When a rate-limit error is actually observed, set `waiting_for_session_reset = true` and back off via `ScheduleWakeup` keyed to the **reported reset time**, not a fixed interval. Clear the flag once a cycle completes successfully.
- All durable progress lives in `orchestrator-state.json`, so a pause loses no work — the next run resumes from the persisted `stage`.

---

## Governance — relationship to AGENTS.md / CLAUDE.md

The repo's `AGENTS.md` §11–12 and the root `CLAUDE.md` "Solve Github issue" alias impose a **hard rule: no code changes before explicit confirmation**. A fully autonomous loop that writes code and merges to `main` would contradict this.

**Resolution:** add an explicit carve-out to `AGENTS.md` stating that the orchestrator path constitutes standing authorization — when a worker agent is spawned by the orchestrator, the interactive-confirmation alias does **not** apply (the human authorized the whole loop by launching it). Workers invoked outside the orchestrator (manual "Solve Github issue") still follow the STOP-and-confirm rule. Without this carve-out the agents would be told both to stop and not to stop.

---

## Agent Responsibilities

| Component | Type | Tools | Key Responsibility |
|---|---|---|---|
| `orchestrator` | **command (main session)** | Bash, Read, Write, Agent | State machine: guards, reads state, spawns the right worker, advances stage |
| `game-designer` | agent | Bash, Read, Write | Surveys game state + open/closed issues, proposes next best feature, creates issue |
| `issue-creator` | agent | Bash, Read, Write | Formalizes a known TODO item/requirement into a GitHub issue (assigns to @me) |
| `branch-manager` | agent | Bash, Read, Write | Sync `main`, create `feat/issue-N-<slug>`, tolerate dirty tree / existing branch |
| `code-writer` | agent | Bash, Read, Write, Edit, Glob, Grep | Implements Java changes; enforces AGENTS.md (layers, thread safety, Java 26); writes tests |
| `build-verifier` | agent | Bash, Write | `./gradlew build && ./gradlew test`; extracts errors into structured result |
| `pr-creator` | agent | Bash, Read, Write | Stages files by name, commits with Co-Author line, pushes, opens PR (idempotent) |
| `pr-merger` | agent | Bash, Write | `gh pr merge --squash --delete-branch` after the green local build |
| `workflow-optimizer` | agent | Bash, Read, Write, Edit | Reads `cycle-log.jsonl`, **proposes** prompt improvements to worker agents (with validation + rollback) |

8 worker agents + 1 orchestrator command.

---

## Game Designer Agent — Detail

**File**: `.claude/agents/game-designer.md`
**Tools**: `Bash, Read, Write`
**Triggered by**: orchestrator when no assigned issues exist and TODO.md has no unchecked items

### Distinction from `issue-creator`

| | `game-designer` | `issue-creator` |
|---|---|---|
| **Input** | Nothing — uses its own judgment | A known requirement string from the orchestrator |
| **Role** | Decides *what* to build next | Formats an already-decided requirement |
| **Ideation** | Yes — reads game state, reasons about player experience | No |

### Process

1. **Survey what exists** — read `readme.md`, the `data/` directory tree, `docs/architecture-plan.md`, and **both open and closed** GitHub issues/PRs (`gh issue list --state all --limit 30`) to understand current features, what's in flight, and what recently shipped.

2. **Dedup guard** — before proposing anything, check open issues and prior `game-design`-labeled issues. If an equivalent feature is already open, or the number of open auto-generated issues exceeds a cap (e.g., 3), **stop** and report `status: success, output: { skipped: "backlog full / duplicate" }` rather than creating more.

3. **Identify gaps** — reason about typical MUD features vs what jmud has. Dimensions: player progression, world depth, social systems, economy, content variety, quality of life.

4. **Score candidates** by `player_value × implementation_feasibility`. Pick the top one scoped to a single PR (not an architectural overhaul).

5. **Create the GitHub issue** with:
   - Title: imperative verb phrase under 70 chars
   - Body sections: *Why* (game design rationale), *What* (player-facing behaviour), *Acceptance criteria* (bulleted checklist), *Technical notes* (which Java packages are involved)
   - Labels: `enhancement`, `game-design` (label must be created first — see Setup)
   - Assigned to `@me`

6. **Write result** to `last-result.json` with `issue_number` and `issue_url`.

### Rules

- Issue body written from a *player experience* perspective first, technical details second.
- Do not propose features that break save-game compatibility unless no simpler option exists.
- Do not propose architectural refactors — those live in `docs/architectural-suggestions.md`.
- Issues created here are labeled `game-design` so they are distinguishable in history and dedup.

---

## Workflow Optimizer — Guardrails

- **Scope**: may only touch the system-prompt *body* of non-orchestrator worker agents — never frontmatter, never result schemas, never source code, never the orchestrator.
- **Propose, don't blind-edit**: write the suggested diff to `optimizer-report.md`. Apply only low-risk wording changes; structural changes are left for human review.
- **Validation**: after any edit, confirm the agent file still parses (valid YAML frontmatter, required keys present). Keep the pre-edit version (e.g., `*.md.bak`) so a bad edit can be rolled back.
- If nothing warrants change, write `optimizer-report.md` with "No Action Taken".

---

## Key Design Decisions

- **Orchestrator is a command, not an agent**: required because subagents cannot spawn subagents.
- **Self-paced loop**: prevents overlapping runs; lease lock is the backstop.
- **Local build is the merge gate**: no CI exists; `build-verifier` green ⇒ merge.
- **Issue Creator is additive**: normal path is the user creating issues manually; issue-creator only runs when nothing is assigned and TODO.md has unchecked items.
- **Retry loop**: build failure sends code-writer back with the error log; exhausted retries open a "blocked" issue, notify, and skip rather than stalling.
- **Squash merge**: mandatory (`--squash`) to match existing history style.
- **State is gitignored**: agent/command definitions travel with the repo; runtime state does not.

---

## Setup (one-time, before first run)

1. Create the missing label: `gh label create game-design --description "Auto-proposed game feature" --color a2eeef`
2. Generate the Gradle wrapper so builds are pinned/reproducible: `gradle wrapper` (then commit `gradlew`, `gradlew.bat`, `gradle/wrapper/`).
3. Add the AGENTS.md governance carve-out (see Governance section).

---

## Files to Create / Modify

1. `/home/taaniel/repos/jmud/.claude/settings.json` — permissions: `Bash(git:*)`, `Bash(gh:*)`, `Bash(./gradlew:*)`, `Bash(gradle:*)`, plus `Read`, `Write`, `Edit`, and worker spawns; set a `defaultMode` so the unattended loop does not stall on prompts. **Security note:** this grants the loop unattended commit/push/merge rights — review the tradeoff before enabling.
2. `/home/taaniel/repos/jmud/.claude/commands/orchestrator.md`
3. `/home/taaniel/repos/jmud/.claude/agents/game-designer.md`
4. `/home/taaniel/repos/jmud/.claude/agents/issue-creator.md`
5. `/home/taaniel/repos/jmud/.claude/agents/branch-manager.md`
6. `/home/taaniel/repos/jmud/.claude/agents/code-writer.md`
7. `/home/taaniel/repos/jmud/.claude/agents/build-verifier.md`
8. `/home/taaniel/repos/jmud/.claude/agents/pr-creator.md`
9. `/home/taaniel/repos/jmud/.claude/agents/pr-merger.md`
10. `/home/taaniel/repos/jmud/.claude/agents/workflow-optimizer.md`
11. Update `/home/taaniel/repos/jmud/.gitignore` — add `.claude/agents/state/`
12. Update `/home/taaniel/repos/jmud/AGENTS.md` — add the orchestrator governance carve-out
13. Add the Gradle wrapper (`gradle wrapper`) and align `AGENTS.md` Java version note to **26** (matches `build.gradle`).

---

## Secret Hygiene

Per AGENTS.md/CLAUDE.md "never log security keys, tokens, etc.": build-verifier and pr-creator must not echo environment variables or paste full build logs into issues/PRs (logs can surface the `gh` token or ssh material). Truncate to the relevant error lines and scrub anything token-shaped.

---

## Verification

1. Confirm `.claude/commands/orchestrator.md` exists and `.claude/agents/` contains all **8** worker files with valid frontmatter.
2. Confirm Setup done: `gh label list` shows `game-design`; `./gradlew --version` works.
3. **Dry-run**: invoke `/orchestrator` with no open issues and game-designer at its dedup cap → it should enter STANDBY and log that, releasing the LOCK.
4. **Kill switch**: `touch .claude/agents/state/PAUSE` → next tick exits immediately; remove it → resumes.
5. **Happy path**: create a test issue assigned to @me → orchestrator picks it up, branches, code-writer runs, build passes, PR opens, squash-merges.
6. After a full cycle: `cycle-log.jsonl` has one entry and `last-result.json` reflects the merge.
7. After 5 cycles: `optimizer-report.md` exists (a proposed diff or "No Action Taken"), and any edited agent file still parses.
8. **Teardown**: close/delete the test issue, delete the test branch and merged PR artifacts, and clear `state/` so the dry-run leaves no residue.
