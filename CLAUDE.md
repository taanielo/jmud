# jmud — Claude Code project instructions

The authoritative engineering rules for this repository live in AGENTS.md and are imported here so every agent session loads them automatically:

@AGENTS.md

## Non-negotiable guardrails (summary — details in AGENTS.md)

1. **Single-writer tick loop**: game state is mutated only on the tick thread via per-player command queues. Reader threads parse and enqueue — nothing else. No new blocking I/O reachable from `tick()`. (AGENTS.md §5)
2. **One command system**: new player commands are `SocketCommand`s registered in `SocketCommandRegistry.createDefault()`, logic in `GameActionService`/domain services. Never touch the legacy `io.taanielo.jmud.command.*` / `core.command.*` packages, and never add logic to `SocketClient`. (AGENTS.md §3.3)
3. **Composition root**: only `GameContext` constructs `Json*Repository` implementations; everything else gets interfaces via constructors. (AGENTS.md §3.3)
4. **Java 26** per `build.gradle`; verify with `./gradlew build` before pushing; never write dependency versions from memory — check Maven Central. (AGENTS.md §12)
5. **Game content is data**: new zones/items/mobs/quests are JSON under `data/` with versioned schemas (`docs/schemas/`), not code. (AGENTS.md §11)

## Architecture roadmap

- Current assessment and target design: `docs/architecture-review-and-improvement-plan.md`
- Actionable backlog: GitHub issues **#168–#189** (label `architecture`). Each issue's "How (implementation guide)" section is binding for the implementing agent, and `Depends on: #N` lines define required ordering.
- If the plan document is missing in your checkout, the GitHub issues are the fallback source of truth.

## Autonomous loop

`.claude/commands/orchestrator.md` runs the human-authorized autonomous cycle (AGENTS.md §13). Worker agents spawned by the orchestrator do not pause for per-change confirmation but must obey every rule above — autonomy never relaxes architecture rules, only the confirmation gate.

### Scheduled runs via cron (preferred over a long-lived `/loop` session)

Every cycle persists its state to `.claude/agents/state/`, so no conversation memory is needed between cycles. Running each cycle as a **fresh headless session** keeps context (and token cost) constant instead of growing with every loop iteration.

One cycle, headless, from the repo root:

```bash
claude -p "/orchestrator" --permission-mode acceptEdits
```

Crontab (`crontab -e`) — cron's minimal PATH lacks `~/.local/bin`, where `claude` lives:

```cron
PATH=/home/taaniel/.local/bin:/usr/local/bin:/usr/bin:/bin
*/30 * * * * cd /home/taaniel/repos/jmud && flock -n .claude/agents/state/cron.flock claude -p "/orchestrator" --permission-mode acceptEdits >> .claude/agents/state/cron.log 2>&1
```

Notes:
- `flock -n` skips a firing while the previous cycle is still running; the orchestrator's own `LOCK`/`PAUSE` guards remain the second line of defense.
- **Stop the loop**: `touch .claude/agents/state/PAUSE` (and/or comment out the crontab line). Remove the file to resume.
- Unattended runs need the permission allow-list from `SETUP.md` §1.4 in `.claude/settings.local.json`, including `Bash(scripts/agent/*)` for the step scripts — headless tool calls fail instead of prompting.
- `cron.log` grows unbounded; truncate it occasionally (`: > .claude/agents/state/cron.log`).
