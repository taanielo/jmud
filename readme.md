# Java MUD (Multi-User Dungeon) Game

Welcome to the Java MUD (Multi-User Dungeon) Game! This project is a simple text-based multiplayer adventure game where players can explore, fight monsters, and interact with each other in a textual environment.

## Overview

This MUD game is implemented in Java and supports both telnet and SSH connections so multiple users can connect and play simultaneously. The game features a simple command-based interface where players can navigate through rooms, interact with objects, and communicate with other players.

## Game Data Persistence

Game data is stored as JSON under `data/`. See `docs/data-schema.md` for the current schema versions and examples.

## Operations

Start/stop, backup/restore, SSH host-key management, log locations, tick-health monitoring, and common failure guidance: see [`docs/runbook.md`](docs/runbook.md).

## Features (TODO)

- Multiple users can connect and play simultaneously.
- Players can explore different rooms and interact with the environment.
- Simple combat system.
- Basic item and inventory management.
- Communication between players.

## Getting Started

### Prerequisites

- Java SDK 26 or higher
- A telnet client (most operating systems include a built-in telnet client)
- An SSH client (OpenSSH is commonly available)

### Connecting to the Server

To connect to the server, you can use either telnet or SSH. Open a terminal and type:

```sh
telnet 127.0.0.1 4444
```

Or connect via SSH:

```sh
ssh -p 2222 <username>@localhost
```

SSH uses the game user registry for password authentication. New users are created on first login (unless disabled).
Telnet is unencrypted and is intended for local development only by default.

Replace `localhost` with the server's IP address and adjust the ports if you changed the defaults.

## Running with Gradle

Ensure you have Java 26 installed. Use the committed Gradle wrapper (`./gradlew`) — never a bare `gradle` — so everyone builds with the same Gradle version.

Build the project:

```sh
./gradlew build
```

Run the server:

```sh
./gradlew run
```

## Configuration

Game configuration lives in `src/main/resources/jmud.properties`. You can override any key with a JVM system property (for example `-Djmud.tick.interval.ms=250`).

Available keys:

- `jmud.prompt.format`
- `jmud.effects.enabled`
- `jmud.tick.interval.ms`
- `jmud.output.ansi.enabled` (default ANSI colors for new players)

Players can toggle ANSI output in-game with `ANSI on|off|toggle|status`.

### Ports

Override the telnet and SSH ports via CLI args or environment variables:

- `--telnet-port <port>` or `JMUD_TELNET_PORT`
- `--ssh-port <port>` or `JMUD_SSH_PORT`

You can also configure hosts and Telnet enablement:

- `--telnet-enabled <true|false>` or `JMUD_TELNET_ENABLED` (default: true)
- `--telnet-host <host>` or `JMUD_TELNET_HOST` (default: 127.0.0.1)
- `--ssh-host <host>` or `JMUD_SSH_HOST` (default: 0.0.0.0)

SSH host keys are stored at `data/ssh/hostkey.ser`.

### Authentication Security

Authentication settings are configured in `jmud.properties`:

- `jmud.auth.allow_new_users`
- `jmud.auth.max_attempts`
- `jmud.auth.attempt_window_seconds`
- `jmud.auth.lockout_seconds`
- `jmud.auth.pbkdf2.iterations`

### Logging

jmud logs to both the console and `jmud.log` using Log4j2.

Each log line includes a `correlationId` field (MDC key) that is populated while a player command executes on the tick thread. This makes server log lines joinable with the audit JSONL on the same field — any log line emitted during command handling will carry the same `correlationId` as the corresponding audit entry.

**Default layout** (human-readable pattern, active automatically):

```
2024-01-01 12:00:00.000 [tick-thread] abc-123 Player sparky attacked goblin.
```

**JSON layout** (NDJSON / JSON-Lines, one object per line):

Activate by passing `-Dlog4j2.configurationFile=log4j2-json.xml` to the JVM at startup:

```sh
./gradlew run --args="..." -Dlog4j2.configurationFile=log4j2-json.xml
# or for the jar:
java -Dlog4j2.configurationFile=log4j2-json.xml -jar jmud.jar
```

Each line is a JSON object with the fields `timestamp`, `level`, `thread`, `logger`, `message`, `correlationId`, and `thrown` (when an exception is present). The `correlationId` field is absent for log lines emitted outside a command scope.

## Testing

### End-to-end smoke test

`scripts/smoke-test.sh` starts the server on dedicated test ports, drives a scripted telnet session (character creation, login, and a sweep of player commands), asserts on the output and the server log, cleans up after itself, and exits `0` (PASS) / `1` (FAIL). Run it after any player-visible change:

```sh
scripts/smoke-test.sh
# override the ports if 4491/4492 are busy:
SMOKE_TELNET_PORT=5555 scripts/smoke-test.sh
```

Full transcripts and the server log are kept under `build/smoke-test/`.

### Concurrent load / tick-stability test

`scripts/load-test.sh` is an optional stress test (not part of normal CI). Where the smoke test checks correctness with a single sequential session, this harness spawns N concurrent telnet clients under sustained load, samples the wizard-only `STATS` tick-health metrics every few seconds, and asserts the tick loop stays stable — the tick count keeps climbing (no stalls), ticks never overrun their budget, and per-tick duration stays under 1.5× the nominal 1000 ms budget. Run it after a green smoke test:

```sh
scripts/load-test.sh                                  # 10 clients, 60s
scripts/load-test.sh --clients 20 --duration-secs 120
LOAD_TELNET_PORT=5555 scripts/load-test.sh --clients 5 --duration-secs 30
```

It grants a temporary wizard user (restored on exit) so it can read `STATS`, prints a `LOAD TEST PASSED: ...` / `FAILED` summary with the measured tick metrics, and exits `0` / `1`. Transcripts, the parsed metrics (`metrics.tsv`) and the server log are kept under `build/load-test/`.

## Basics of Java Telnet Socket Connection

In this game, telnet is used as a simple protocol to handle text-based user interactions. The `ServerSocket` class in Java is used to create a server socket that listens on a specified port. When a client connects, a new `Socket` instance is created to handle the connection.

## Basics of MUD (Multi-User Dungeon)

A MUD is a real-time multiplayer text-based game, typically set in a fantasy world. Players can:
- Explore various locations (rooms).
- Solve puzzles and complete quests.
- Interact with other players and NPCs (non-player characters).
- Collect items and manage an inventory.
- Engage in combat with monsters.

The core of the game is based on players typing commands to interact with the game world. Commands typically include actions like `move`, `look`, `take`, `drop`, `attack`, and `talk`.
