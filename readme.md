# Java MUD (Multi-User Dungeon) Game

Welcome to the Java MUD (Multi-User Dungeon) Game! This project is a simple text-based multiplayer adventure game where players can explore, fight monsters, and interact with each other in a textual environment.

## Overview

This MUD game is implemented in Java and supports both telnet and SSH connections so multiple users can connect and play simultaneously. The game features a simple command-based interface where players can navigate through rooms, interact with objects, and communicate with other players.

## Game Data Persistence

Game data is stored as JSON under `data/`. See `docs/data-schema.md` for the current schema versions and examples.

## Features (TODO)

- Multiple users can connect and play simultaneously.
- Players can explore different rooms and interact with the environment.
- Simple combat system.
- Basic item and inventory management.
- Communication between players.

## Getting Started

### Prerequisites

- Java SDK 25 or higher
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

Ensure you have Java 25 installed and Gradle available on your PATH.

Build the project:

```sh
gradle build
```

Run the server:

```sh
gradle run
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
