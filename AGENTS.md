# AGENTS.md

This file defines how Codex agents must operate in this **Java MUD (Multi-User Dungeon)** project.

The goal is to ensure **correctness under concurrency**, **predictable game behavior**, and **long-term evolvability**, while taking advantage of **modern Java (Java 25)** features, including **virtual threads**.

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

* Player connections
* Command handling
* Blocking I/O (sockets, persistence)

Rules:

* One virtual thread per player or command is acceptable
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

## 5. Game Loop & Determinism

* Game state updates must be deterministic
* Avoid time-based logic tied directly to wall-clock time
* Use explicit ticks or events where ordering matters

Combat resolution, movement, and effects must produce the same result given the same inputs.

---

## 6. Command Handling

* Commands must be explicit domain objects
* Parsing and execution must be separated
* Validation happens before execution

Good:

* `AttackCommand`
* `MoveCommand`

Bad:

* Large `switch` statements on command strings

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

## 8. Java 25 Feature Usage

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

---

## 11. Change Safety Rules

Agents must:

* Make one logical change per PR
* Avoid cross-cutting refactors without confirmation
* Preserve save-game compatibility unless explicitly approved
* Keep game data (abilities, spells, items, rooms, effects, etc.) in JSON under `data/` with versioned schemas
* Store non-generic domain data in `data/*` JSON (e.g., races in `data/races/`)

Breaking changes to game rules must be clearly documented.

---

## 12. Agent Behavior Constraints

Codex agents must:

* Never introduce data races
* Never block platform threads unnecessarily
* Never mix infrastructure and domain logic
* Always consider multiplayer impact
* Run tests before pushing changes to GitHub

When in doubt, choose the **simpler, safer design**.

---

## 13. Guiding Philosophy

> A MUD server is a long-running, shared world.
> Stability, fairness, and predictability matter more than raw speed.

Agents should optimize for **trust**, not tricks.
