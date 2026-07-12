# SETUP — preflight & running the autonomous orchestrator

This document lists the checks that **must pass before any orchestrator run** and a short guide
to running the loop. Every item here corresponds to a real failure that has blocked a run — do not
skip the preflight.

Run all commands from the repo root (`~/repos/jmud`).

---

## 1. Preflight checklist (run before every session)

Quick all-in-one check:

```bash
# 1. A real JDK 26 (not just a JRE) must be present — the build toolchain needs javac 26
javac -version            # expect: javac 26.x   (NOT "command not found")
./gradlew --stop          # kill any stale daemon caching old toolchain detection
./gradlew -q javaToolchains | grep -A4 'JDK 26'   # expect: "Is JDK: true" for language version 26

# 2. GitHub CLI authenticated AND the token has write scopes
gh auth status            # expect: "Logged in", active account

# 3. Build is green from a clean state (this is the loop's only quality gate)
./gradlew build test --console=plain
```

If all three pass, you are clear to run. Details and fixes below.

### 1.1 Java / JDK 26 (most common blocker)

The build pins a **Java 26 toolchain** (`build.gradle`: `languageVersion = 26`). Compilation needs a
full **JDK** (with `javac`) — a JRE is not enough.

- Verify: `javac -version` → `javac 26.x`. If it says *command not found*, you only have a JRE.
- Confirm Gradle sees it as a JDK:
  ```bash
  ./gradlew -q javaToolchains | grep -A4 'JDK 26'
  ```
  You need `Is JDK: true`. If it shows `Is JDK: false`, that entry is a JRE.
- **Fix (Arch Linux):**
  ```bash
  sudo pacman -S jdk-openjdk        # installs the full JDK 26 (javac), coexists with the JRE
  archlinux-java status             # confirm java-26-openjdk is available
  ```
- **After installing or changing the JDK, always stop the daemon** or Gradle serves cached
  (stale) toolchain detection and the build keeps "failing" with
  `Cannot find a Java installation ... matching {languageVersion=26}`:
  ```bash
  ./gradlew --stop
  ```

### 1.2 GitHub CLI auth + token scopes

The workers create issues, open PRs, and squash-merge via `gh` (the GitHub API). Git push itself
uses **SSH**, so a push can succeed while API calls fail — auth status alone is not enough.

- Verify login: `gh auth status` → "Logged in to github.com".
- **Fine-grained PAT — required Repository permissions (all `Read and write`):**

  | Permission     | Used for                                  |
  | -------------- | ----------------------------------------- |
  | **Issues**     | game-designer / issue-creator create issues |
  | **Pull requests** | `scripts/agent/pr-create.sh` opens, `scripts/agent/merge.sh` merges |
  | **Contents**   | squash-merge writes the merged commit to `main` |
  | Metadata       | (default, read) repo access               |

  Edit at <https://github.com/settings/tokens?type=beta> → your token → Repository permissions.
  Changes apply immediately (no re-auth). A **classic token with the `repo` scope** also works.
- Quick write test (creates then deletes a throwaway issue):
  ```bash
  n=$(gh issue create --title "perm-check (auto-delete)" --body "x"); \
  gh issue delete "${n##*/}" --yes
  ```
  If `createIssue` / `createPullRequest` returns *"Resource not accessible by personal access
  token"*, the token is missing that write permission.

### 1.3 Build gate

`scripts/agent/verify.sh` (`./gradlew check`) is the local quality gate; `scripts/agent/merge.sh`
additionally gates on GitHub CI (`gh pr checks --watch`) before merging. A run must start from a
green build:

```bash
./gradlew build test --console=plain   # expect: BUILD SUCCESSFUL
```

### 1.4 Permissions for unattended runs (`.claude/settings.json`)

For a **detached / unattended** loop, the session must not stall on permission prompts. Add
`.claude/settings.json` (not committed by default — it grants powerful unattended rights):

```json
{
  "permissions": {
    "defaultMode": "acceptEdits",
    "allow": [
      "Bash(git:*)", "Bash(gh:*)", "Bash(./gradlew:*)", "Bash(gradle:*)",
      "Bash(cat:*)", "Bash(date:*)", "Bash(mkdir:*)", "Bash(mv:*)",
      "Bash(test:*)", "Bash(ls:*)", "Bash(scripts/next-issue.sh:*)",
      "Bash(scripts/agent/branch.sh:*)", "Bash(scripts/agent/verify.sh:*)",
      "Bash(scripts/agent/pr-create.sh:*)", "Bash(scripts/agent/merge.sh:*)",
      "Read", "Write", "Edit", "Task"
    ]
  }
}
```

Easiest: run `/permissions` inside this repo and add those rules. **Security note:** this lets the
loop commit, push, and squash-merge to `main` unattended with only the local build as a gate.

---

## 2. Running `/loop /orchestrator`

> Launch from **inside `~/repos/jmud`** so the worker agents in `.claude/agents/` resolve as native
> subagents (`game-designer`, `code-writer`, …) and the step scripts in `scripts/agent/` resolve
> by relative path.

### One supervised cycle (recommended for the first run)

Run the command **without** `/loop` so it does one stage and stops; inspect, then run again:

```
/orchestrator
```

Each invocation advances one stage: `FIND_ISSUE → CREATE_BRANCH → WRITE_CODE → VERIFY_BUILD →
CREATE_PR → MERGE_PR`. State persists in `.orchestrator/` between calls.

### Continuous (self-paced) loop

```
/loop /orchestrator
```

No interval — the next cycle starts only after the previous one returns (prevents overlapping runs
that would race on the single git checkout and shared state).

### Scheduled runs via cron (preferred for unattended use)

Every cycle persists its state to `.orchestrator/`, so no conversation memory is needed
between cycles. Running each cycle as a **fresh headless session** keeps context (and token cost)
constant, unlike a long-lived `/loop` session whose context grows with every iteration.

One cycle, headless, from the repo root:

```bash
CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS=0 claude -p "/orchestrator" --model sonnet --effort high --permission-mode acceptEdits
```

Crontab (`crontab -e`) — cron's minimal PATH lacks `~/.local/bin`, where `claude` lives:

```cron
PATH=/home/taaniel/.local/bin:/usr/local/bin:/usr/bin:/bin
CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS=0
*/30 * * * * cd /home/taaniel/repos/jmud && flock -n .orchestrator/cron.flock claude -p "/orchestrator" --model sonnet --effort high --permission-mode acceptEdits >> .orchestrator/cron.log 2>&1
```

`CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS=0` makes a headless run wait indefinitely for background
tasks instead of killing them after 10 minutes. The orchestrator prompt already requires workers
to be spawned synchronously, so this is a safety net: without it, an accidentally backgrounded
code-writer is terminated mid-implementation, the LOCK is left held, and the next two cron
firings skip on it — one feature cycle then stretches to 2–3 hours of wall time.

Notes:

- `flock -n` skips a firing while the previous cycle is still running; the orchestrator's own
  `LOCK`/`PAUSE` guards remain the second line of defense.
- Unattended runs need the §1.4 permission allow-list in `.claude/settings.local.json`, including
  `Bash(scripts/agent/*)` for the step scripts — headless tool calls fail instead of prompting.
- `cron.log` is auto-trimmed by `guard.sh` (kept to its last 500 lines once it passes 1 MB); no manual truncation needed.
- Don't run cron and an interactive `/loop` at the same time — the LOCK guard serializes them,
  but there's no reason to pay for both.

**Model/effort policy:** the orchestrator session runs on **Sonnet** (it only dispatches scripts
and composes commit titles); each subagent pins its own model in its frontmatter — **code-writer
on Opus** (the one real engineering task per cycle), **code-reviewer on Opus** (deep review of
the last ~10 merged cycles, runs every 10 merges), game-designer/workflow-optimizer on Sonnet,
issue-creator on Haiku. Effort `high` is inherited by subagents and is the recommended default for
Opus coding work — don't raise it to `xhigh`/`max` loop-wide, and don't run the loop session on a
Fable/max configuration; the quality gates (local `check`, CI, retries), not model ceiling, are
what protect `main`.

### Leaving an interactive loop running in the background (alternative to cron)

The `/loop` runs inside an interactive session; "background" means a detached terminal that stays
alive (the machine must stay on and online). Context grows each cycle, so prefer cron above for
long unattended stretches:

```bash
tmux new -s jmud-loop
cd ~/repos/jmud
claude            # then inside Claude:  /loop /orchestrator
# detach: Ctrl-b then d        reattach: tmux attach -t jmud-loop
```

### Stopping / pausing (kill switch)

```bash
touch .orchestrator/PAUSE     # next cycle exits cleanly at the GUARD step
rm    .orchestrator/PAUSE     # resume
```

### State directory (`.orchestrator/`, git-ignored)

| File                       | Purpose                                              |
| -------------------------- | ---------------------------------------------------- |
| `orchestrator-state.json`  | the state machine (current issue, stage, counters)   |
| `last-result.json`         | the last worker's structured result                  |
| `cycle-log.jsonl`          | one line per completed cycle                          |
| `LOCK`                     | run lease (stale after 35 min); prevents overlap     |
| `PAUSE`                    | kill switch (presence = stop)                        |

PAUSE/LOCK are enforced deterministically by `scripts/agent/guard.sh` (the orchestrator's Step 0).
If a run is interrupted, a leftover `LOCK` younger than 35 min will make the next run skip with
`LOCK held` — at most one skipped 30-min cron firing; delete it manually only if you are sure no
run is active.

---

## 3. Known robustness gaps (read before unattended use)

- **The verify step (`scripts/agent/verify.sh`) can't distinguish infra failures from code failures.** A missing JDK, a stale
  daemon, or a dependency-resolution error looks like a build failure, so the loop would waste its
  WRITE_CODE retries rewriting correct code and then file a bogus `blocked:` issue. The preflight in
  §1 is what prevents this — **do not skip it**.
- **What seeds a cycle:** if `TODO.md` has unchecked `- [ ]` items, `FIND_ISSUE` turns the first one
  into an issue (via `issue-creator`) *before* falling through to `game-designer`. Empty/checked
  `TODO.md` ⇒ `game-designer` invents the feature.

---

## 4. Playing via the browser web client (issue #527)

The WebSocket transport (issue #526) doubles as an embedded HTTP server for a
dependency-free browser terminal (assets live in `web/`, served by the jmud
process — no CDN, no build step).

**Open it locally:** start jmud (the WebSocket server is on by default) and browse
to the WebSocket port over plain HTTP:

```bash
./gradlew run --args="--ws-port 8080"
# then open http://127.0.0.1:8080/ in a browser and click Connect
```

`GET /` serves the terminal page; `GET /ws` is the game WebSocket. The page
pre-fills its WebSocket-URL field from the address you loaded it from, so a local
run needs no configuration — create a character, fight, chat, QUIT exactly as over
telnet, colors included. Password prompts are masked automatically (heuristic: the
input masks when the last server line ends with `password:`). Command history is on
the up/down arrows; a dropped connection shows a disconnected badge with a one-click
**Reconnect** that resumes through the server-side linkdead path (issue #343).

**Point it at a remote server:** the same `web/` assets can be hosted from any
static web host (or opened from disk). Set the field at the top of the page to your
server's endpoint — e.g. `wss://mud.example.com/ws` — and click Connect. For a
public deployment, terminate TLS (`wss://`) at a reverse proxy in front of jmud and
restrict the browser `Origin` with `--ws-allowed-origins` (see the runbook config
table). Override the served asset directory with `--ws-web-root <dir>` /
`JMUD_WS_WEB_ROOT` if you relocate `web/`.
