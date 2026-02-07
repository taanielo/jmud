# Architecture Improvement Plan

This document captures a proposed architecture refinement plan for jmud, based on the current codebase. It is intended as a target direction and incremental roadmap, not an immediate refactor mandate.

## Goals

- Preserve deterministic, server-authoritative gameplay.
- Make concurrency boundaries explicit and testable.
- Clarify responsibilities across domain, application, adapters, and infrastructure.
- Reduce coupling between sockets, persistence, and core game logic.

## Proposed Layers and Packages

### Domain (game rules only)

Package prefix: `io.taanielo.jmud.domain.*`

- Entities, value objects, domain services, domain exceptions.
- Sealed command hierarchy and command value objects.
- Repository and clock interfaces live here (ports).

Examples:

- `domain.player`, `domain.world`, `domain.combat`, `domain.effects`, `domain.ability`
- `domain.command`
- `domain.port`
- `domain.exception`

### Application (use cases / orchestration)

Package prefix: `io.taanielo.jmud.app.*`

- Coordinates domain behavior and domain ports.
- No direct socket, JSON, or thread primitives.

Examples:

- `app.session` (login/logout/reconnect)
- `app.command` (command execution pipeline)
- `app.tick` (tick orchestration)
- `app.audit` (audit use cases)

### Adapters (I/O translation only)

Package prefix: `io.taanielo.jmud.adapter.*`

- Translate external input/output to application calls.
- Socket command parsing, output formatting, prompt rendering.

Examples:

- `adapter.socket`
- `adapter.json`
- `adapter.cli` (future)

### Infrastructure (implementations)

Package prefix: `io.taanielo.jmud.infra.*`

- Concrete repository implementations, schedulers, JSON mapping, audit sinks.
- Deterministic RNG provider implementation.

Examples:

- `infra.persistence.json`
- `infra.audit`
- `infra.tick`
- `infra.rng`

### Composition Root (bootstrap)

Package prefix: `io.taanielo.jmud.bootstrap.*`

- Wires together app, infra, and adapters.
- No gameplay logic here.

## Dependency Rules

- `domain` depends on nothing else.
- `app` depends on `domain` only.
- `adapter` depends on `app` (and sometimes `domain` data types).
- `infra` depends on `domain` and `app`; nothing depends on `infra` directly.
- `bootstrap` depends on all and wires them together.

## Targeted Refactors

### 1) Unify command architecture

Current state has two command systems and cross-imports between socket and command packages. Target: one domain command model.

- Introduce `domain.command.Command` (sealed hierarchy).
- Socket parsing creates domain commands (or an app-level `ParsedCommand`).
- `app.command.CommandExecutor` handles routing and execution.
- Retire `io.taanielo.jmud.command.*` once ported.

### 2) Split SocketClient responsibilities

`SocketClient` currently handles authentication, command dispatch, game action orchestration, auditing, and persistence updates.

Create application services:

- `app.session.PlayerSessionService`
- `app.command.CommandExecutionService`
- `app.action.GameActionService` (relocate existing service)
- `app.audit.AuditRecorder`

Socket adapter should only:

- Read input.
- Parse into commands.
- Call application services.
- Render output.

### 3) Move composition root out of socket adapter

`GameContext` is currently in socket package and wires infra dependencies.

Target:

- `bootstrap.Bootstrap` (or `bootstrap.GameBootstrap`) wires dependencies.
- `adapter.socket.SocketServer` receives a constructed app façade.

### 4) Deterministic RNG boundary

`ThreadLocalCombatRandom` makes replay/testing harder.

Target:

- `domain.port.RandomSource` (or `domain.port.CombatRandom`).
- App injects RNG seeded per command/tick.
- Infra provides deterministic implementation.

## Incremental Migration Roadmap

1) Introduce new package skeletons (`domain`, `app`, `adapter`, `infra`, `bootstrap`) with zero behavior change.
2) Extract app services from `SocketClient`, starting with authentication and command dispatch.
3) Implement domain command model, update socket parsing, and remove legacy command package.
4) Move `GameContext` to bootstrap and stop wiring infra in socket packages.
5) Add deterministic RNG port + infra implementation, update combat/effect/ability usage.

## Open Decisions

- Package-only refactor vs Gradle multi-module split (`domain`, `app`, `adapter`, `infra`).
- Compatibility constraints: keep `io.taanielo.jmud.core.*` for now or rename all.

