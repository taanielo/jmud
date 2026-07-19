# AGENTS.md

This file defines how Codex agents must operate in this **Java MUD (Multi-User Dungeon)** project.

The goal is to ensure **correctness under concurrency**, **predictable game behavior**, and **long-term evolvability**, while taking advantage of **modern Java (Java 26 — always match the toolchain in `build.gradle`, which is authoritative over any version mentioned in prose)** features, including **virtual threads**.

---

## 1. Core Principles

* Correctness > performance > cleverness
* Game rules must be **deterministic** and **server-authoritative**
* Concurrency must be **explicitly designed**, never accidental
* Modern Java features are preferred when they improve clarity and safety
* Prefer **value objects** over primitives for domain concepts; use primitives sparingly
* Every domain service class and its public methods must have Javadoc

Agents must behave like a cautious backend engineer operating a real-time multiplayer system.

---

## 2. Rule Hierarchy

When making decisions, apply rules in this order:

1. Folder-specific rules in `/agent-rules/` (if present)
2. This `AGENTS.md`
3. General Java best practices

Local rules always override global ones.

---

## 3. Game Architecture Expectations

### 3.1 Domain-Centric Design

* Core game logic must live in the **domain layer**
* Domain objects are **stateful and behavior-rich**
* Avoid anemic models

Examples of domain concepts:

* `Player`
* `Room`
* `World`
* `Command`
* `Combat`

Infrastructure (networking, persistence, scheduling) must not leak into domain logic.

---

### 3.2 Clean Architecture Guide

* Layers (from inner to outer): **Domain** → **Application/Use Cases** → **Interface/Adapters** → **Infrastructure**
* Dependency rule: outer layers depend on inner layers only; never the reverse
* Domain layer: entities, value objects, domain services, domain exceptions
* Application layer: orchestration/use cases; coordinates repositories and domain services
* Interface/Adapters: controllers, command parsing, socket adapters, DTO mapping
* Infrastructure: persistence, networking, scheduling, file I/O, JSON repositories
* Repository interfaces live in the domain; implementations live in infrastructure
* Anti-patterns:
  * Framework annotations or I/O in domain models
  * Domain code calling JSON mappers or socket APIs directly

Examples (current packages):
* `io.taanielo.jmud.core.player`, `io.taanielo.jmud.core.combat` → Domain
* `io.taanielo.jmud.core.server.socket` → Interface/Adapters
* `io.taanielo.jmud.core.*.repository.json` → Infrastructure

---

### 3.3 Canonical Implementations (Anti-Drift — read before writing any code)

The codebase contains historical duplicates. These rules pick the winners; extending a loser is always wrong:

* **Command system**: the ONLY real one is `SocketCommand` + `SocketCommandRegistry` (`core.server.socket`). A new player command = a `SocketCommand` implementation, registered in `SocketCommandRegistry.createDefault()`, with its game logic in `GameActionService` or a domain service — **never inside `SocketClient`**.
* **Forbidden packages** (dead code scheduled for deletion in issue #178): never modify, extend, import, or imitate `io.taanielo.jmud.command.*`, `io.taanielo.jmud.core.command.*`, `core.server.ClientContext`, or the parallel character model (`core.character.Character`/`BaseCharacter`/`PlayerCharacter`/`Stats`/`BasicStats`). The canonical player model is `core.player.Player` and its component records.
* **`SocketClient` is frozen**: it is an oversized transport adapter being shrunk (issue #182). Do not add fields, game logic, or fan-out loops to it. If your change seems to need it, the logic belongs in `GameActionService`, `PlayerSession`, a domain service, or the messaging layer.
* **Composition root**: only `GameContext` (moving to a `bootstrap` package, issue #181) may construct `Json*Repository` implementations, engines, and schedulers. Everywhere else: constructor injection of interfaces.
* **Message delivery**: use the `MessageBroadcaster` abstraction; do not hand-roll loops over the client pool. (Scoped room/player delivery arrives with issue #180 — extend it there, not ad hoc.)

### 3.4 Architecture Roadmap

`docs/architecture-review-and-improvement-plan.md` is the current architecture assessment and target design. The actionable backlog is GitHub issues **#168–#189** (label `architecture`); each contains a "How (implementation guide)" section that is binding for the implementing agent, plus explicit `Depends on:` ordering. Feature work must not contradict the target design described there.

---

## 4. Concurrency & Thread Safety (Critical)

This is a multi-user, concurrent system. Thread safety is not optional.

### 4.1 Ownership of State

* Every mutable game object must have a **clear owner**
* Shared mutable state must be avoided whenever possible
* Prefer **message passing** over shared mutation

If ownership is unclear, the design is invalid.

---

### 4.2 Synchronization Rules

* Do **not** synchronize on public objects
* Avoid coarse-grained locks
* Prefer:

  * confinement
  * immutability
  * structured concurrency

Allowed primitives:

* `synchronized` (narrow scope only)
* `ReentrantLock` (only when needed)
* `Atomic*` types for simple counters

---

### 4.3 Virtual Threads (Mandatory Where Applicable)

Java virtual threads must be preferred for:

* Player connections (socket read loops)
* Blocking I/O (sockets, persistence workers)

Rules:

* One virtual thread per player **connection** is the pattern; command **execution** does NOT get its own thread — see §5
* Do not pool virtual threads manually
* Avoid thread-local state unless strictly required

Example mindset:

> Blocking is fine. Contention is not.

---

### 4.4 Structured Concurrency

When running multiple related tasks:

* Use structured concurrency constructs
* Tasks must have a clear lifecycle
* Cancellation must propagate correctly

Unstructured `ExecutorService` usage is discouraged unless justified.

---

## 5. Game Loop & Determinism (the actual concurrency model — Critical)

jmud uses a **single-writer tick loop**. This is the load-bearing design decision; preserve it:

* One `FixedRateTickScheduler` thread drains all `Tickable`s each tick (default 1000 ms), including every player's `PlayerCommandQueue`.
* Connection reader threads **only parse input and enqueue** a task via `PlayerSession.enqueueCommand(...)`. They never touch game state directly.
* **All mutation of game state (Player, rooms, mobs, effects) happens on the tick thread.** Code running on a reader thread that reads live game state must treat it as a possibly-stale snapshot.
* Do not add locks/synchronization to game state — if you think you need one, you are mutating from the wrong thread.
* Do not add **new blocking file/network I/O** to any code reachable from `tick()`; one slow write stalls the whole world. (Existing saves are being moved to a write-behind queue in issue #179 — route persistence through it once it exists.)

Determinism rules:

* Game state updates must be deterministic; avoid logic tied directly to wall-clock time — use ticks
* Randomness goes through the `CombatRandom`/RNG ports, never bare `Random`/`Math.random()` (seeded determinism is issue #183)

Combat resolution, movement, and effects must produce the same result given the same inputs.

---

## 6. Command Handling

* Commands must be explicit objects: implement `SocketCommand`, register in `SocketCommandRegistry.createDefault()` (see §3.3)
* Parsing and execution must be separated; execution runs on the tick thread via the player's command queue (§5)
* Validation happens before execution
* Game logic goes in `GameActionService` or a domain service so it is testable without sockets

Good:

* `AttackCommand`
* `MoveCommand`

Bad:

* Large `switch` statements on command strings
* New logic added to `SocketClient`

Commands must not:

* Access raw sockets
* Perform blocking I/O inside domain logic

---

## 7. Error Handling & Fault Isolation

* A single player error must never crash the server
* All player-facing errors must be recoverable
* Use domain-specific exceptions

Virtual threads must be isolated so failure affects only the originating player or command.

---

## 8. Modern Java Feature Usage (Java 26)

Agents should prefer modern Java features when they improve clarity:

* Records for immutable value objects
* Pattern matching for control flow
* Sealed interfaces for domain hierarchies (e.g. commands, events)
* Enhanced switch expressions

Do not use new features solely for novelty.

---

## 9. Performance Guidelines

* Avoid premature optimization
* Prefer algorithmic simplicity over micro-optimizations
* Measure before optimizing

Hot paths must be:

* Documented
* Isolated
* Covered by tests

---

## 10. Testing Expectations

* Core game rules must be unit-testable without networking
* Concurrency-sensitive code must have stress or race tests
* Deterministic behavior must be testable

If logic cannot be tested deterministically, the design must be revisited.

### 10.1 End-to-End Smoke Test

`scripts/smoke-test.sh` verifies the real server over telnet: it starts jmud on dedicated test ports, creates a user, runs SCORE/WHO, asserts on output and the server log, cleans up, and exits 0 (pass) / 1 (fail). Transcripts land in `build/smoke-test/`.

* Run it after player-visible changes (login flow, commands, output) — **do not hand-roll ad-hoc `nc`/telnet sessions**; extend the script instead.
* Useful facts encoded there: character creation expects race/class *names* (not menu numbers); the prompt has no trailing newline (don't `^`-anchor patterns that may share its line); commands execute on the next tick (~1s).

---

## 11. Change Safety Rules

Agents must:

* Make one logical change per PR
* Avoid cross-cutting refactors without confirmation
* Preserve save-game compatibility unless explicitly approved
* Keep game data (abilities, spells, items, rooms, effects, etc.) in JSON under `data/` with versioned schemas
* Store non-generic domain data in `data/*` JSON (e.g., races in `data/races/`)
* **Feature completeness**: `docs/content-dod.md` defines what "complete" means per content type; `docs/feature-matrix.md` tracks it per entity/system. Any PR that adds or changes game content must update the affected matrix rows in the same PR; an issue introducing a new content *type* must add its DoD section. Prefer `--validate-data` rules over matrix rows wherever the check is mechanical.
* **Design focus**: `docs/design-focus.md` names the current multi-cycle player-experience goal with exit criteria. While a focus is active, the game-designer works its exit criteria before proposing anything else (see `.claude/agents/game-designer.md` step 3); "Player journey" rows in the matrix track experience quality per the DoD in `docs/content-dod.md`.
* **Areas & cartography (issue #529)**: every room must belong to exactly one `data/areas/*.json` entry, and every new or changed area MUST ship/update its area file with (a) `room_ids`, (b) hand-drawn `ascii_map` art (no `@`, no player marker), (c) `connections` to adjacent areas, and (d) at least one obtainable map item (an item with `map_area_id`, stocked in a shop or placed in a loot table / room). `--validate-data` enforces all of this (rooms assigned exactly once, connections realized by real exits, maps obtainable), so it is mechanical, not honor-system. Maps are the only cartography — never render the player's position on any map.

Breaking changes to game rules must be clearly documented.

---

## 12. Agent Behavior Constraints

Agents must:

* Never introduce data races (mutate game state only on the tick thread — §5)
* Never block platform threads unnecessarily
* Never mix infrastructure and domain logic
* Always consider multiplayer impact
* Verify with `./gradlew build` (the wrapper, never a bare `gradle`) before pushing changes to GitHub; when quality gates exist (`check`, CI workflows, ArchUnit), they are part of the definition of done
* Never write a dependency version from memory — look up the current version on Maven Central (or keep the existing pinned one); this repo has been burned by stale training-data versions
* Never resurrect deleted code or patterns; if a file you expected is gone, check `git log` and the architecture plan before recreating it

When in doubt, choose the **simpler, safer design**.

---

## 13. Autonomous Orchestrator Exception

The multi-agent orchestrator (`.claude/commands/orchestrator.md`) is a **human-authorized, standing workflow**. Launching the loop *is* the authorization.

* When a worker agent is spawned **by the orchestrator** during a loop cycle, the "ask before changes" / STOP-and-confirm expectations (including the root `CLAUDE.md` "Solve Github issue" gate and §11's confirmation rule) do **not** apply.
* This exception is **scoped to orchestrator-spawned cycles only**. Any agent invoked manually, outside the loop, still follows the normal STOP-and-confirm rules.
* The orchestrator still honors its safety controls: the `PAUSE` kill switch, the run lease lock, `max_cycles`, the build gate before merge, and one-logical-change-per-PR (§11).

---

## 14. Guiding Philosophy

> A MUD server is a long-running, shared world.
> Stability, fairness, and predictability matter more than raw speed.

Agents should optimize for **trust**, not tricks.
