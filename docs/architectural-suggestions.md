# Architectural Suggestions for jmud

## 1. Break Up the `SocketClient` God Class (Critical)

`SocketClient.java` is **1157 lines** mixing I/O, authentication, game logic (combat, items, abilities, death), auditing, and messaging. This is the single biggest structural problem.

**Suggested decomposition:**

- **`PlayerSession`** — manages the lifecycle of a connected player (authentication state, connected flag, the current `Player` reference)
- **`GameActionService`** — domain-level use cases: `attack()`, `useAbility()`, `getItem()`, `dropItem()`, `quaffItem()`, `resolveDeathIfNeeded()`. Takes a `Player` and returns results without touching sockets
- **`TelnetConnection`** — raw I/O: byte reading, IAC handling, output stream writing (extract from the current `SocketClient` I/O code)
- Move command handlers to delegate to `GameActionService` instead of calling back into `SocketClient`

This directly addresses the AGENTS.md rule: *"Infrastructure must not leak into domain logic."*

---

## 2. Fix the Player Concurrency Model (Critical)

The current design has **data races** between the tick thread and command threads:

- `Player.effects` is a mutable `ArrayList` (`Player.java:86`) returned directly (`Player.java:107`), mutated from both tick and command threads
- `EffectInstance.remainingTicks`/`stacks` are mutated in-place without synchronization
- `PlayerRespawnTicker.scheduled`/`remainingTicks` are non-volatile fields accessed cross-thread
- No coordination between tick-based mutations and command-based mutations

**Suggested approach — command queue per player:**

```
Command Thread              Tick Thread
     |                          |
     v                          v
  [Player Command Queue]  ->  Single-threaded
                               processing per tick
```

- Each player gets a thread-confined mutable state, processed by a single thread
- Commands from the socket are enqueued as actions, executed on the tick
- This eliminates all concurrent mutation — aligns with AGENTS.md's *"prefer confinement"* and *"prefer message passing over shared mutation"*

**Alternatively**, make `Player` truly immutable:
- `Player.effects()` must return `List.copyOf(effects)` (fixes the mutable leak)
- Replace mutable `EffectInstance` with immutable snapshots, rebuilt each tick
- Use an atomic `replacePlayer()` with compare-and-swap semantics

---

## 3. Centralize Dependency Wiring (High)

Currently dependencies are constructed ad-hoc:
- `SocketServer` constructor manually builds ~10 shared services
- Each `SocketClient` creates its own `CombatEngine`, `AbilityEngine`, etc.
- `JsonEffectRepository` is instantiated **5 separate times** across `SocketClient` (lines 174, 188, 595, 1091, 1112)
- Command handlers are duplicated per-client despite being stateless

**Suggested approach:**

Create a `GameContext` (or `ServiceRegistry`) object built once at startup:

```java
public record GameContext(
    RoomService roomService,
    CombatEngine combatEngine,
    AbilityEngine abilityEngine,
    EffectRepository effectRepository,
    PlayerRepository playerRepository,
    // ...
) {}
```

Constructed once in `SocketServer`, passed to each `SocketClient`. Shared command registry instead of per-client instances.

---

## 4. Delete Dead Code (Medium)

Three command systems exist; only one is used:

| System | Location | Status |
|--------|----------|--------|
| `SocketCommand*` | `core/server/socket/` | **Active** |
| `CommandRegistry` | `command/` | Partially used (SAY, QUIT delegation only) |
| `CommandHandler`/`Command` | `core/command/` | **Completely dead** — never instantiated |

Also dead: `ClientContext.java`, the entire `core/command/` package.

The `character/` package (`Character`/`BaseCharacter`/`PlayerCharacter`/`Stats`/`BasicStats`) duplicates `Player`/`PlayerVitals` and appears to be an abandoned parallel model. Decide which is canonical and remove the other.

---

## 5. Decompose the `Player` God Class (Medium)

`Player` has a **12-parameter constructor** and mixes:
- Authentication (`User`)
- Stats/vitals (`PlayerVitals`)
- Effects system (`List<EffectInstance>`)
- Inventory (`List<Item>`)
- Abilities (`List<AbilityId>`)
- Character identity (race, class, level, experience)
- UI preferences (`promptFormat`, `ansiEnabled`)
- Death state (`dead` flag)

**Suggested decomposition:**

- `PlayerIdentity` — username, race, class, level, experience
- `PlayerCombatState` — vitals, effects, dead flag
- `PlayerInventory` — items
- `PlayerPreferences` — promptFormat, ansiEnabled
- `Player` becomes an aggregate root that composes these

---

## 6. Add Room-Scoped Messaging (Medium)

`MessageBroadcasterImpl` broadcasts to **all connected clients** globally. In a MUD, most messages (say, combat, movement) should be scoped to the current room.

**Suggested approach:**

```java
public interface MessageBroadcaster {
    void broadcastToRoom(RoomId room, Message message, Username exclude);
    void broadcastGlobal(Message message, Username exclude);
}
```

Use `RoomService.playerLocations` to resolve which clients are in a given room.

---

## 7. Fix the `RoomService` Overload (Medium)

`RoomService` (319 lines) handles location tracking, rendering, movement, item management, and corpse spawning.

**Suggested split:**

- `PlayerLocationService` — tracks where players are, handles `enter()`/`leave()`/`move()`
- `RoomRenderer` — builds room descriptions with exits/items/occupants
- `RoomItemService` — manages item pickup, drop, transient items, corpse spawning

---

## 8. Unify Repository Error Handling (Low)

- Most repositories throw checked domain exceptions (`AttackRepositoryException`, `RepositoryException`, etc.)
- `PlayerRepository` silently swallows `IOException` in `JsonPlayerRepository.savePlayer()` — callers cannot detect failed saves, leading to silent data loss
- Each repository domain has its own exception class (6+ copies of identical structure)

**Suggested approach:**

- Single `RepositoryException` shared across all repository interfaces
- `PlayerRepository` should throw it like all others, not swallow errors

---

## 9. Fix the Buffer Bug in Telnet Reading (Low)

`SocketCommand.readString()` scans the full 1024-byte buffer instead of only the bytes actually read. This can read stale data and `bytes[i + 1]` can throw `ArrayIndexOutOfBoundsException` when `i == bytes.length - 1`. Pass the byte count from `input.read(bytes)` and use it as the scan limit.

---

## 10. Resource Leak: Disconnected Clients (Low)

`SocketClient.close()` never calls `clientPool.remove(this)`. Disconnected clients accumulate in the `SocketClientPool.clients` list indefinitely. The `remove()` method exists but is never called.

---

## Priority Roadmap

| Phase | Changes | Why first |
|-------|---------|-----------|
| **1** | Fix Player concurrency (#2), fix buffer bug (#9), fix client pool leak (#10) | Correctness bugs — can crash or corrupt state |
| **2** | Extract `GameActionService` from `SocketClient` (#1), centralize wiring (#3) | Unblocks all further refactoring |
| **3** | Decompose `Player` (#5), split `RoomService` (#7), delete dead code (#4) | Cleaner domain model |
| **4** | Room-scoped messaging (#6), unified exceptions (#8) | Feature enablement |
