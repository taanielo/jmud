# jmud — Architecture Review & System Design Improvement Plan

**Date:** 2026-07-04
**Scope:** Full review of architecture, features, `TODO.md`, and existing design docs (`docs/architecture-plan.md`, `docs/architectural-suggestions.md`). This document supersedes both as the single consolidated improvement plan.
**Status:** Proposal — no code changes included.

---

## 1. Executive Summary

jmud is a Java MUD server (~20.3k LOC main, ~12.5k LOC test, 77 test classes) with telnet and SSH transports, a tick-based game loop, JSON file persistence, and a feature set that has grown rapidly through an agent-driven GitHub issue → PR workflow (`agent-orchestration-plan.md`, `.claude/agents/`).

The codebase is in **much better shape than the previous review** (`architectural-suggestions.md`) described: dependency wiring is centralized, the `Player` aggregate is decomposed, per-player command queues confine game-state mutation to the tick thread, and the client-pool leak is fixed. The core concurrency model — single-writer tick loop fed by queues — is the *right* architecture for a MUD and should be preserved.

The remaining problems cluster into four areas, in priority order:

1. **Correctness bugs still open** — telnet line framing can throw `ArrayIndexOutOfBoundsException` and silently corrupts fragmented input; player saves swallow `IOException` (silent data loss).
2. **Blocking I/O on the game loop** — JSON file writes execute on the single tick thread; one slow disk write stalls every player.
3. **Structural debt** — `SocketClient` has *grown* to 2,242 lines (was 1,157); two dead command systems and an abandoned parallel character model still ship; messaging is still globally broadcast with room scoping bolted on ad hoc.
4. **Missing engineering foundations** — no CI, no static analysis, no coverage gate, no architecture-rule enforcement, no metrics. For a project where most code is written by agents, automated gates are not optional polish; they are the primary quality mechanism.

The plan below is phased so every phase ships independently and the game keeps running throughout.

---

## 2. Current Architecture (verified snapshot)

### 2.1 Runtime model

```
                    ┌────────────────────────────────────────────┐
 telnet :4444 ──►   │ SocketServer ──► SocketClient (per client) │
                    │                    │ reader (virtual thread)│
 ssh :2222 ────►    │ SshServer ─► SshGameShell ─► SocketClient  │
                    └────────────┬───────────────────────────────┘
                                 │ enqueue(Runnable)
                                 ▼
                   PlayerCommandQueue (per player, ConcurrentLinkedQueue)
                                 │ drained on tick
                                 ▼
              FixedRateTickScheduler (1 thread, 1000 ms default)
                                 │
        ┌─────────┬──────────┬───┴──────┬───────────┬───────────┐
        ▼         ▼          ▼          ▼           ▼           ▼
   TickClock  MobRegistry  Effect/   Resting/   Respawn/   CorpseDecay
              (AI, wander) Healing   Cooldown   per-player  ticker
                           tickers   trackers   tickers
```

* **Transports:** telnet (`SocketServer`, loopback by default) and SSH (`Apache SSHD` + BouncyCastle, password auth against the game user registry). Both run on virtual threads (`Main.java:37-38`).
* **Concurrency model:** reader threads never mutate game state directly. `SocketClient.handleCommand()` (line 439) enqueues onto the session's `PlayerCommandQueue`, which is a `Tickable` drained by the **single** tick thread. This is effective thread confinement — the previous review's #1 critical issue is resolved in design.
* **Composition root:** `GameContext.create()` (`core/server/socket/GameContext.java`) wires ~27 services once at startup and is passed everywhere.
* **Persistence:** JSON files under `data/` (players, users, rooms, items, mobs, attacks, abilities, classes, races, shops, quests, banks, effects), schema-versioned DTOs with mappers, JSON Schema files in `docs/schemas/`. Player saves are atomic (`tmp` + `ATOMIC_MOVE`, `JsonPlayerRepository.savePlayer`).
* **Auditing:** async JSONL sink with bounded queue (`core/audit/`).
* **Config:** `jmud.properties` + system-property overrides via `GameConfig`; ports/hosts via CLI args or env vars.
* **Stack:** Java toolchain 26 (`build.gradle:14`), Lombok, Log4j2, Jackson, commons-lang3, Apache SSHD 2.17, JUnit 5.

### 2.2 Feature completeness

Per `TODO.md`: **64 features implemented**, 12 of 24 "Planned" items done. Remaining planned work is content/gameplay (poison DoT, CURE, boss abilities, scrolls, two new zones, titles, gossip history, SHOUT/WHISPER, GIVE, day/night cycle, aliases) — none of it architecturally hard, but several items (poison, day/night, boss abilities) touch the effect/tick engines and will benefit from the cleanups below.

### 2.3 Development workflow

Features are developed by a multi-agent orchestrator (`/loop /orchestrator`): game-designer → issue → branch → code-writer → build-verifier → PR → merge. Git history confirms this cadence (#142–#166 in recent log). Rules live in `AGENTS.md` + `/agent-rules/` (repo-parent level). **There is no CI** — the only build gate is the build-verifier agent running locally.

---

## 3. Progress Since the Previous Review

Tracking `docs/architectural-suggestions.md` items against today's code:

| # | Suggestion | Status today |
|---|------------|--------------|
| 1 | Break up `SocketClient` god class | **Partial** — `PlayerSession`, `GameActionService` (587 LOC), `TelnetConnection`/`TelnetClientConnection` extracted, but `SocketClient` itself grew to **2,242 lines** |
| 2 | Fix player concurrency | **Done (design)** — per-player command queue drained on tick thread; `Player` decomposed into immutable-ish state records |
| 3 | Centralize dependency wiring | **Done** — `GameContext` record, built once in `Main` |
| 4 | Delete dead code | **Not done** — `command/` package, `core/command/` package, `ClientContext`, and the parallel `core/character/` model (`Character`/`BaseCharacter`/`PlayerCharacter`/`Stats`) all still present |
| 5 | Decompose `Player` | **Done** — `PlayerIdentity`, `PlayerCombatState`, `PlayerInventory`, `PlayerPreferences`, `PlayerVitals` |
| 6 | Room-scoped messaging | **Not done** — `MessageBroadcaster` is still global-only; room delivery is hand-rolled in `SocketClient.deliverRoomMessage` |
| 7 | Split `RoomService` | **Not done** — grew from 319 to 545 lines |
| 8 | Unify repository error handling | **Not done** — `JsonPlayerRepository.savePlayer` still logs-and-swallows `IOException` (lines 52-54); 6+ duplicate exception classes remain |
| 9 | Telnet buffer bug | **Not done** — see Finding C1 |
| 10 | Client pool leak | **Done** — `clientPool.remove(this)` at `SocketClient.java:253` |

The `docs/architecture-plan.md` layering proposal (domain/app/adapter/infra/bootstrap) has **not started** — everything is still under `core.*` with the composition root inside the socket package.

---

## 4. Findings

### 4.1 Correctness (fix first)

**C1 — Telnet line framing is broken.** `TelnetClientConnection.readLine()` allocates a fresh 1 KB buffer per `read()` and passes it to `SocketCommand.readString(bytes)`, which:
- ignores the actual byte count returned by `read()` and scans the full array (`SocketCommand.java:35`);
- evaluates `bytes[i] == 13 && bytes[i + 1] == 10` — when a lone CR sits at index 1023, `bytes[i + 1]` throws `ArrayIndexOutOfBoundsException`;
- assumes one TCP read == one complete line. Character-mode telnet clients (the default for many) send one byte per read, so `crlfPos` stays 0 and every keystroke resolves to `""` — input is silently dropped. Fragmented or coalesced packets corrupt commands the same way;
- mixes IAC handling with line data: a packet containing IAC *and* a command discards the command.

This is a protocol-layer defect, not an edge case: correctness depends on TCP segment boundaries, which are never guaranteed.

**C2 — Silent player-data loss.** `JsonPlayerRepository.savePlayer()` catches `IOException` and only logs (`JsonPlayerRepository.java:52-54`). A full disk or permission error means progress silently vanishes on next load. Callers cannot react (retry, warn the player, refuse quit).

**C3 — Blocking file I/O on the tick thread.** Player saves, and any repository write triggered by a command, run inside the drained command queue — i.e., on the *single* tick thread that also runs mob AI, effects, and healing for everyone. One slow fsync stalls the whole world past its 1000 ms budget. There is also **no tick-overrun detection**: `scheduleAtFixedRate` silently queues up late ticks.

**C4 — Mutable state still leaks across threads.** `PlayerCombatState.effects()` returns its internal `List` directly (line 25-27), and `Player.effects()` passes it through. Under the queue model only the tick thread *should* touch it, but nothing enforces that — any code holding a `Player` reference on a reader thread (e.g. prompt rendering, WHO listings) can observe or mutate mid-tick state. Confinement is currently a convention, not a guarantee.

**C5 — No save-on-shutdown.** The shutdown hook (`Main.java:26-29`) stops the scheduler and clears the tick registry but does not flush online players or the audit queue. A `SIGTERM` during play loses everything since the last per-event save.

### 4.2 Design debt

**D1 — `SocketClient` re-accreted (2,242 LOC).** Extractions happened, but the class still owns authentication flow, character creation state, command context construction, message delivery/room fan-out, prompt rendering, audit correlation, and lifecycle. Because every agent-written feature touches it, it grows every cycle — the class is a merge-conflict and regression hotspot. The delivery logic (`deliverMessages`, `deliverRoomMessage`, `sendToUsername`) duplicates what a proper broadcaster should own.

**D2 — Three command systems, one real.** `core/server/socket/SocketCommand*` is active; `command/` (`CommandRegistry`, `AbstractCommand`) is vestigially referenced; `core/command/` is fully dead. `core/character/` is an abandoned parallel domain model alongside `core/player/`. Dead code is disproportionately harmful here because **agents pattern-match on existing code** — a code-writer agent can (and eventually will) extend the wrong command system.

**D3 — Global-only `MessageBroadcaster`.** The interface (`broadcast(Client, Message)`) supports only broadcast-to-all. Room/party/target scoping lives in `SocketClient` as manual loops over the client pool. Every new social feature (SHOUT to adjacent rooms, WHISPER, party chat — all on the TODO list) will re-implement fan-out unless scoping moves into the messaging layer.

**D4 — Repository construction duplication in `GameContext`.** `JsonRaceRepository` is constructed 4×, `JsonItemRepository` 3×, `JsonAttackRepository` and `JsonClassRepository` 2× each (`GameContext.java:202-293`) — each instance re-reads and re-parses the same JSON. Harmless at today's scale, but it shows the wiring is by-hand and drift-prone; one shared instance per repository is trivially available since `GameContext` already exists.

**D5 — Non-deterministic combat RNG.** `ThreadLocalCombatRandom` is injected concretely. The `CombatRandom` port exists, but there is no seeded/deterministic implementation wired for tests or replay. Given the stated goal of deterministic, server-authoritative gameplay (AGENTS.md §1), RNG should be seedable per tick or per encounter.

**D6 — Per-player tickables sprawl.** `PlayerSession` manually registers/unregisters ~6 tick subscriptions per player (command queue, cooldowns, respawn, effects, healing, resting). Lifecycle correctness depends on every path calling the right cleanups. A single per-player `Tickable` that internally composes these systems would collapse the subscription bookkeeping and make per-player tick cost measurable.

**D7 — `RoomService` (545 LOC) mixes location tracking, rendering, item/corpse management, and movement.** Same finding as the previous review, further grown since.

### 4.3 Engineering practice gaps (2026 baseline)

| Gap | Why it matters here specifically |
|-----|----------------------------------|
| **No CI pipeline** | The orchestrator merges PRs after a *local* build-verifier run. A GitHub Actions gate (`gradle build` + tests on PR) is the minimum trust anchor for agent-merged code. |
| **No static analysis** | No Error Prone, NullAway/JSpecify, SpotBugs, or Checkstyle/Spotless. Agents reproduce whatever slips through; analyzers are the cheapest reviewer that never tires. |
| **No architecture enforcement** | `docs/architecture-plan.md` defines dependency rules, but nothing enforces them — which is exactly why the socket package became the composition root. ArchUnit tests turn the layering doc into a failing build. |
| **No coverage or mutation signal** | 77 test classes is healthy, but without JaCoCo thresholds regressions in coverage are invisible; PIT (mutation testing) on the combat/effect engines would validate that tests assert behavior, not just execute lines. |
| **No dependency automation** | Versions are hand-pinned in `build.gradle` (JUnit 5.10.2 is notably behind). A Gradle **version catalog** (`gradle/libs.versions.toml`) + Renovate/Dependabot keeps the toolchain current without agent guesswork. |
| **No runtime observability** | Log4j2 + audit JSONL only. No tick-duration histogram, queue-depth gauge, or online-player count. The single most important health metric for this architecture — *tick overrun* — is unmeasured. |
| **Docs drift** | `readme.md` says Java 25; `build.gradle` says 26. Two overlapping architecture docs with stale claims (this document replaces them). `readme.md` "Features (TODO)" section is years behind `TODO.md`. |

### 4.4 Security posture (brief)

Broadly sound for a hobby-scale server: PBKDF2 at 310k iterations, auth rate limiting with lockout, telnet loopback-only by default with a warning on non-loopback binds, SSH as the encrypted transport, no secrets in logs (AGENTS.md rule). Two notes:
- `data/ssh/hostkey.ser` implies Java-serialized host key material (SSHD's `SimpleGeneratorHostKeyProvider` default). Prefer PEM (`hostkey.pem`) — serialization formats are opaque and Java deserialization of on-disk state is a needless risk class.
- Input length is implicitly capped at 1024 bytes by the (broken) read buffer; when fixing C1, add an explicit max-line-length and per-connection input rate limit so a client can't spam the command queue faster than ticks drain it (queue is unbounded).

---

## 5. Improvement Plan

Phases are ordered by risk-reduction per unit of effort. Each phase is independently shippable; none blocks feature development for long.

### Phase 0 — Correctness (do immediately, ~1–2 agent cycles each)

| Item | Fix |
|------|-----|
| **P0.1** Telnet framing (C1) | Replace read-and-scan with a proper **line assembler**: accumulate bytes into a per-connection buffer, strip IAC sequences in-stream, emit a command only on LF/CRLF, enforce max line length (e.g. 512 chars), drop with a warning past that. Unit-test with fragmented, coalesced, CR-at-boundary, and IAC-interleaved inputs. |
| **P0.2** Save error propagation (C2) | `PlayerRepository.savePlayer` throws the shared `RepositoryException`; callers decide (retry once, notify player "save failed", block QUIT-without-save). Consolidate the 6+ per-domain exception clones into one while touching this. |
| **P0.3** Save/flush on shutdown (C5) | Shutdown hook: stop accepting input → drain command queues one final tick → save all online players → flush audit sink → stop scheduler. |
| **P0.4** Tick overrun detection (C3, partial) | Measure tick duration in `FixedRateTickScheduler.runTick`; log WARN when > interval, expose the histogram (see Phase 3). This is the 10-line canary that tells you when P2.2 becomes urgent. |

### Phase 1 — Engineering foundations (the highest-leverage phase for an agent-driven repo)

| Item | Action |
|------|--------|
| **P1.1** CI | GitHub Actions: `./gradlew build` on every PR + main; branch protection requires green. The orchestrator's pr-merger agent gates on CI status instead of (or in addition to) the local build-verifier. |
| **P1.2** Gradle hygiene | Commit to the Gradle **wrapper** as the invocation path everywhere (docs currently say `gradle build`); move dependencies to a **version catalog**; enable Renovate/Dependabot. Align readme's Java version with the toolchain (26). |
| **P1.3** Static analysis | Add Error Prone + NullAway (with JSpecify annotations on new code), Spotless for formatting. Wire into `check` so CI enforces it. Introduce with a baseline so existing violations don't block; ratchet down over time. |
| **P1.4** Architecture tests | ArchUnit test module encoding the target rules from §6: domain packages must not import socket/Jackson/file-I/O types; only the bootstrap package may construct `Json*Repository`; no code outside `core/server/socket` may import from it. **This is what stops `SocketClient` from re-accreting.** |
| **P1.5** Coverage + mutation | JaCoCo report with a modest floor (e.g. 70% on `core/combat`, `core/effects`, `core/ability`); PIT run (scheduled, not per-PR) on the same engines. |
| **P1.6** Agent-rules sync | Update `.claude/agents/code-writer.md` and `/agent-rules/java.md` to reference the new gates (run `./gradlew check`, never touch `command/` or `core/command/`, extend `GameActionService` not `SocketClient`). Agent instructions are part of the architecture in this repo. |

### Phase 2 — Structural consolidation

| Item | Action |
|------|--------|
| **P2.1** Delete dead code (D2) | Remove `command/`, `core/command/`, `ClientContext`, and the `core/character/` parallel model (`Character`, `BaseCharacter`, `PlayerCharacter`, `Stats`, `BasicStats` — keep only what `ClassDefinition`/`Race` actually need). Do this *before* further refactoring so agents stop seeing the wrong patterns. Pure deletion PR, trivially reviewable. |
| **P2.2** Async persistence (C3) | Introduce a write-behind `PersistenceQueue`: repositories enqueue snapshots (players are already immutable-ish records — cheap to snapshot); a dedicated virtual thread drains and writes atomically; `flush()` for shutdown/QUIT paths. Tick thread never touches disk again. |
| **P2.3** Room-scoped messaging (D3) | Extend the broadcaster: `sendToPlayer(Username, Message)`, `broadcastToRoom(RoomId, Message, exclusions)`, `broadcastGlobal(...)`, later `broadcastToParty(...)`. Move `deliverMessages`/`deliverRoomMessage` out of `SocketClient` into the implementation. Unblocks SHOUT/WHISPER/party-chat TODO items cleanly. |
| **P2.4** Shrink `SocketClient` to a transport adapter (D1) | Target end-state: read line → enqueue → render results. Move authentication/creation flow into an `app`-level session service, command-context construction into the dispatcher, delivery into P2.3's broadcaster. Measure success bluntly: `SocketClient` < 300 LOC, and an SSH connection shares all logic except the transport. |
| **P2.5** Package layering (from `architecture-plan.md`) | Adopt the domain/app/adapter/infra/bootstrap split **incrementally**: create `bootstrap` first and move `GameContext` there (smallest step, biggest rule-clarity win), then migrate per-feature as files are touched. Keep it package-level; a Gradle multi-module split is not warranted at 20k LOC. ArchUnit (P1.4) enforces the rules from day one even while packages migrate. |
| **P2.6** Deterministic RNG (D5) | Wire a seedable `CombatRandom` (seed = f(worldSeed, tick, actorId)) as the default; keep `ThreadLocalCombatRandom` only if a non-deterministic mode is explicitly wanted. Log the seed in audit entries → any combat becomes replayable from the audit trail. |
| **P2.7** Split `RoomService` (D7) | `PlayerLocationService` (who is where, enter/leave/move), `RoomRenderer` (look output), `RoomItemService` (ground items, corpses). Do after P2.3 since rendering/delivery boundaries shift. |
| **P2.8** Per-player tick composition (D6) | Replace the ~6 manual registrations per player with one `PlayerTicker` composing queue-drain, cooldowns, effects, healing, resting, respawn. One register/unregister per session; per-player tick cost becomes measurable. |

### Phase 3 — Observability & operability

| Item | Action |
|------|--------|
| **P3.1** Metrics | Micrometer with a simple registry (JMX or a `/metrics` line on an admin port): tick duration histogram, tickable count, per-player queue depth, online players, saves/sec, save failures, auth failures. Tick-overrun (P0.4) graduates into this. |
| **P3.2** Structured logging | Log4j2 JSON layout option + correlation-id (already exists per command) as an MDC field, so audit JSONL and server logs join on it. |
| **P3.3** Ops runbook | One doc: start/stop, backup `data/` (it's the whole database — say so explicitly), restore, host-key rotation, what tick-overrun warnings mean. |
| **P3.4** Data safety | Periodic `data/` snapshot (tar + retention) via cron or a tick-driven job; JSON schema validation on load already implied by versioned DTOs — add a `--validate-data` startup flag that fails fast on malformed files. |

### Phase 4 — Feature enablement (existing TODO, now cheaper)

Sequence the remaining `TODO.md` items to exploit the cleanups:

1. **Poison DoT + CURE** — pure effect-engine content once P2.2 keeps ticks lean; good PIT target.
2. **SHOUT / WHISPER / GIVE / gossip history** — trivial after room-scoped messaging (P2.3); gossip history is a bounded ring buffer in the broadcaster.
3. **Boss abilities + zone-level warnings + Sewers/Ruins zones** — content-only; validates that the data-driven pipeline needs no code for new zones.
4. **Day/night cycle** — a world-level `Tickable` publishing phase-change events on the existing `PlayerEventBus`; room descriptions gain an optional night variant in the schema (bump `room.v2`).
5. **Titles, kill high-scores, aliases** — player-record additions; aliases resolve in the dispatcher before command matching.

---

## 6. Target Architecture (end-state after Phase 2)

```
bootstrap/          Main, GameBootstrap (owns all Json* construction, config, wiring)
app/                PlayerSessionService, CommandExecutionService, GameActionService,
                    PersistenceQueue, tick orchestration
domain/             player, world, combat, effects, ability, quest, shop, bank, party
                    + ports: repositories, CombatRandom, Clock, MessageBroadcaster
adapter/socket/     SocketServer, SocketClient (thin), telnet line assembler, dispatcher
adapter/ssh/        SshServer, SshGameShell (thin)
infra/              Json* repositories, audit sinks, FixedRateTickScheduler, seeded RNG
```

Dependency rules (ArchUnit-enforced): `domain` imports nothing above it; `app` → `domain`; adapters → `app`; `infra` → `domain`+`app`; only `bootstrap` sees `infra` concretions. The single-writer tick loop and per-player queues stay exactly as they are — they are the load-bearing design decision and they're correct.

Scalability note: one tick thread comfortably handles hundreds of players for a text MUD *once disk I/O is off the loop* (P2.2). If profiling ever shows otherwise, the queue model shards naturally (players partitioned across N tick workers by room/zone) without changing any domain code — another reason to keep confinement strict now.

---

## 7. Prioritized Roadmap

| Phase | Contents | Effort (agent cycles) | Risk if skipped |
|-------|----------|----------------------|-----------------|
| **0** | Telnet framing, save errors, shutdown flush, tick canary | ~4 small PRs | Crashes, silent data loss |
| **1** | CI, catalogs, Error Prone/NullAway, ArchUnit, coverage, agent-rule updates | ~6 small PRs | Agent-merged regressions; god classes regrow |
| **2** | Dead-code purge, async persistence, scoped messaging, SocketClient diet, layering, seeded RNG, RoomService split, PlayerTicker | ~10–14 PRs, incremental | Feature velocity decays; tick stalls at scale |
| **3** | Metrics, structured logs, runbook, backups | ~4 PRs | Blind operations |
| **4** | Remaining TODO features in the order above | ongoing | — |

Ground rules while executing (consistent with `AGENTS.md`): every refactor PR is behavior-preserving and separately mergeable; deletion PRs contain only deletions; new code carries JSpecify nullness annotations and Javadoc on domain services; no PR mixes phases.

---

## 8. Open Decisions

1. **Persistence future** — JSON-per-entity is fine at this scale and is transparently diffable (valuable for an agent workflow). Revisit only if cross-entity transactions appear (e.g. trade between players); then embedded SQLite/H2 behind the existing repository ports, not before.
2. **Package rename scope** — migrate `core.*` → layered packages opportunistically (as files are touched) vs. one big-bang move. Recommendation: opportunistic, with ArchUnit rules written against *both* old and new locations during transition.
3. **Telnet retention** — SSH is the secure path; telnet could become a dev-only flag default-off in a future release. Low urgency given loopback default + warning already in place.
4. **`data-nonexistent-xyz/`** — appears to be a test fixture or leftover in the repo root; confirm and remove or relocate under `src/test/resources`.

---

*Supersedes the former `docs/architectural-suggestions.md` (findings folded into §3–4) and `docs/architecture-plan.md` (layering adopted into §5–6), both now removed; they remain available in git history.*
