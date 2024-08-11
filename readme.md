# Java MUD (Multi-User Dungeon) Game

Welcome to the Java MUD (Multi-User Dungeon) Game! This project is a simple text-based multiplayer adventure game where players can explore, fight monsters, and interact with each other in a textual environment.

## Overview

This MUD game is implemented in Java and uses telnet socket connections to allow multiple users to connect and play simultaneously. The game features a simple command-based interface where players can navigate through rooms, interact with objects, and communicate with other players.

## Features (TODO)

- Multiple users can connect and play simultaneously.
- Players can explore different rooms and interact with the environment.
- Simple combat system.
- Basic item and inventory management.
- Communication between players.

## Getting Started

### Prerequisites

- Java SDK 17 or higher
- A telnet client (most operating systems include a built-in telnet client)

### Connecting to the Server

To connect to the server, you can use any telnet client. Open a terminal and type:

```sh
telnet localhost 4444
```

Replace `localhost` with the server's IP address and `4444` with the port number the server is listening on.

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