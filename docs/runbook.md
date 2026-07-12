# jmud Operations Runbook

This document covers day-to-day operations: starting and stopping the server,
configuration, data backup and restore, SSH host-key management, log locations,
tick-health monitoring, and first-response guidance for common failures.

See also: `readme.md` for prerequisites and configuration key reference.

---

## Table of Contents

1. [Start / Stop](#1-start--stop)
2. [Configuration](#2-configuration)
3. [Data — backup and restore](#3-data--backup-and-restore)
4. [Corrupt player-file recovery](#4-corrupt-player-file-recovery)
5. [SSH host key](#5-ssh-host-key)
6. [Logs and audit trail](#6-logs-and-audit-trail)
7. [Tick health](#7-tick-health)
8. [Common failures](#8-common-failures)
9. [Automated data backups](#9-automated-data-backups)
10. [Validating game data (--validate-data)](#10-validating-game-data----validate-data)

---

## 1. Start / Stop

### Development (Gradle wrapper)

```sh
# Build and run in one step; output goes to the terminal.
./gradlew run

# Pass CLI args (port overrides, etc.)
./gradlew run --args="--telnet-port 4444 --ssh-port 2222 --ws-port 8080"

# Pass JVM system-property overrides alongside Gradle run
./gradlew run -Djmud.tick.interval.ms=500
```

The wrapper (`./gradlew`) pins the Gradle version. Never use a bare `gradle`
command — the versions differ and can produce unexpected behaviour.

Stop: `Ctrl-C` sends SIGINT, which triggers the JVM shutdown hook and a clean
shutdown (the tick loop drains its current tick, open connections are closed,
and the audit sink flushes).

### Production (distribution archive)

After `./gradlew build`, the assembled distribution lives under
`build/distributions/`:

```
build/distributions/
  jmud-1.0-SNAPSHOT.tar
  jmud-1.0-SNAPSHOT.zip
```

Extract to your preferred location and run the start script:

```sh
tar -xf build/distributions/jmud-1.0-SNAPSHOT.tar -C /opt/
/opt/jmud-1.0-SNAPSHOT/bin/jmud
```

Pass CLI arguments and JVM flags using the `JAVA_OPTS` environment variable or
by appending them to the script invocation:

```sh
JAVA_OPTS="-Djmud.tick.interval.ms=500 -Dlog4j2.configurationFile=log4j2-json.xml" \
  /opt/jmud-1.0-SNAPSHOT/bin/jmud --telnet-port 4444 --ssh-port 2222
```

The `data/` directory is resolved relative to the **current working directory**
at startup. Run the binary from the directory that contains your `data/` tree,
or symlink `data` to the correct path.

### Example systemd unit

```ini
[Unit]
Description=jmud MUD server
After=network.target

[Service]
Type=simple
User=jmud
WorkingDirectory=/opt/jmud
ExecStart=/opt/jmud-1.0-SNAPSHOT/bin/jmud --telnet-port 4444 --ssh-port 2222
Environment=JAVA_OPTS=-Dlog4j2.configurationFile=log4j2-json.xml
KillSignal=SIGTERM
TimeoutStopSec=10
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Reload and start:

```sh
sudo systemctl daemon-reload
sudo systemctl enable jmud
sudo systemctl start jmud
sudo systemctl status jmud
```

Stop cleanly (waits for `TimeoutStopSec`):

```sh
sudo systemctl stop jmud
```

---

## 2. Configuration

Configuration has three layers, applied in this precedence order (highest first):

1. **JVM system properties** (`-Djmud.tick.interval.ms=250`)
2. **CLI arguments / environment variables** (ports/hosts only)
3. **`src/main/resources/jmud.properties`** (the defaults)

### CLI arguments and environment variables (ports and hosts)

| Purpose | CLI argument | Environment variable | Default |
|---|---|---|---|
| Telnet port | `--telnet-port <n>` | `JMUD_TELNET_PORT` | `4444` |
| Telnet host | `--telnet-host <h>` | `JMUD_TELNET_HOST` | `127.0.0.1` |
| Telnet enabled | `--telnet-enabled <bool>` | `JMUD_TELNET_ENABLED` | `true` |
| SSH port | `--ssh-port <n>` | `JMUD_SSH_PORT` | `2222` |
| SSH host | `--ssh-host <h>` | `JMUD_SSH_HOST` | `0.0.0.0` |
| WebSocket port | `--ws-port <n>` | `JMUD_WS_PORT` | `8080` |
| WebSocket host | `--ws-host <h>` | `JMUD_WS_HOST` | `127.0.0.1` |
| WebSocket enabled | `--ws-enabled <bool>` | `JMUD_WS_ENABLED` | `true` |
| WebSocket allowed origins | `--ws-allowed-origins <csv>` | `JMUD_WS_ALLOWED_ORIGINS` | *(unset → permissive)* |
| Web client root | `--ws-web-root <dir>` | `JMUD_WS_WEB_ROOT` | `web` |

The WebSocket endpoint (issue #526) serves a browser client the same game, login
flow and single-writer tick model as telnet — only the wire framing differs
(RFC 6455 text frames; no telnet control bytes). It binds to loopback by default,
exactly like telnet, and speaks plain `ws://`, which is **unencrypted**. For a
public deployment, terminate TLS (`wss://`) at a reverse proxy (nginx/Caddy) in
front of jmud and restrict the browser `Origin` with `--ws-allowed-origins`
(comma-separated); leaving it unset is permissive and appropriate only for the
loopback-bound default. Password masking is handled by the browser client, so the
transport never emits telnet IAC echo-control bytes.

The same port also serves the static browser client (issue #527): a plain
`GET /` returns the single-page terminal from the `web/` directory (override with
`--ws-web-root`), while `GET /ws` (the WebSocket upgrade) is the game stream. So
`--ws-port 8080` on the default loopback bind gives a complete, playable URL at
`http://127.0.0.1:8080/`. The client is dependency-free (no build step, no CDN):
it renders ANSI colors, keeps the input line below the scrollback, masks password
prompts, remembers command history, and offers one-click reconnect (which resumes
via the linkdead path, issue #343) without an auto-reconnect loop. The page has a
WebSocket-URL field, so the same `web/` assets can also be hosted from any static
host and pointed at a remote `wss://…/ws` endpoint.

### `jmud.properties` keys (select important ones)

See `src/main/resources/jmud.properties` for the full list with defaults.
Every key can be overridden with a JVM system property of the same name.

| Key | Purpose |
|---|---|
| `jmud.tick.interval.ms` | Tick budget in milliseconds (default `1000`) |
| `jmud.prompt.format` | Player prompt template |
| `jmud.output.ansi.enabled` | Default ANSI colour for new players |
| `jmud.effects.enabled` | Enable/disable status effects |
| `jmud.healing.enabled` | Enable/disable passive HP regen |
| `jmud.combat.rng` | `seeded` (deterministic) or `threadlocal` |
| `jmud.world.seed` | Explicit RNG seed (blank = random, logged at boot) |
| `jmud.audit.enabled` | Enable/disable the audit JSONL sink |
| `jmud.audit.path` | Audit file path (default `logs/audit.jsonl`) |
| `jmud.metrics.enabled` | Enable/disable JMX metric registration |
| `jmud.auth.allow_new_users` | Allow new accounts to be created on login |
| `jmud.auth.max_attempts` | Failed-login attempts before lockout |
| `jmud.auth.lockout_seconds` | Lockout duration in seconds |
| `jmud.auth.pbkdf2.iterations` | PBKDF2 iteration count for password hashing |

Cross-reference: `readme.md` § Configuration has further detail and examples.

---

## 3. Data — backup and restore

### What lives in `data/`

`data/` **is the entire database**. There is no external database engine.

| Subdirectory | Type | Backed by git? |
|---|---|---|
| `rooms/` | World content (room definitions) | Yes |
| `items/` | World content (item definitions) | Yes |
| `mobs/` | World content (mob definitions) | Yes |
| `attacks/` | World content (attack definitions) | Yes |
| `races/` | World content (race definitions) | Yes |
| `classes/` | World content (class definitions) | Yes |
| `skills/` | World content (skills/spells) | Yes |
| `quests/` | World content (quest definitions) | Yes |
| `characters/` | World content (NPC definitions) | Yes |
| `shops/` | World content (shop definitions) | Yes |
| `users/` | Runtime state (auth credentials) | No |
| `players/` | Runtime state (player character state) | No |
| `banks/` | Runtime state (bank account balances) | No |
| `ssh/hostkey.pem` | SSH host key | No |

Content directories (rooms, items, mobs, etc.) are versioned in git and do not
need separate backup — use `git pull` to restore them.

Runtime-state directories (`users/`, `players/`, `banks/`) and the SSH host key
**must be backed up separately**.

### Backup procedure

Player saves use write-then-atomic-rename (`<name>.json.tmp` → `<name>.json`),
so any `*.json` file on disk is a complete and consistent snapshot. A live
filesystem copy is therefore safe without stopping the server.

Verified procedure:

```sh
# Snapshot runtime state while the server is running
BACKUP_DIR="/var/backups/jmud/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
cp -r data/users data/players data/banks data/ssh "$BACKUP_DIR/"
echo "Backup written to $BACKUP_DIR"
```

Verify the backup captured the expected files:

```sh
ls -lR "$BACKUP_DIR"
```

You can run this from cron or a scheduled task. Retain at least 24 hours of
hourly snapshots, and keep daily snapshots for 30 days.

### Restore procedure

The following steps were verified end-to-end in a dev environment.

1. Stop the server (so it cannot write new state during the restore):

   ```sh
   sudo systemctl stop jmud
   # or Ctrl-C if running under ./gradlew run
   ```

2. Move the current runtime state aside in case the restore is incorrect:

   ```sh
   mv data/users  data/users.broken
   mv data/players data/players.broken
   mv data/banks  data/banks.broken
   mv data/ssh    data/ssh.broken
   ```

3. Copy the backup in:

   ```sh
   BACKUP_DIR="/var/backups/jmud/20260704-120000"   # adjust to your snapshot
   cp -r "$BACKUP_DIR/users"   data/
   cp -r "$BACKUP_DIR/players" data/
   cp -r "$BACKUP_DIR/banks"   data/
   cp -r "$BACKUP_DIR/ssh"     data/
   ```

4. Verify the directory contents look correct, then start the server:

   ```sh
   ls data/players/ data/users/ data/banks/
   sudo systemctl start jmud
   ```

5. Confirm a player can log in and that their character state matches the
   snapshot timestamp. Remove the `.broken` directories once satisfied:

   ```sh
   rm -rf data/users.broken data/players.broken data/banks.broken data/ssh.broken
   ```

---

## 4. Corrupt player-file recovery

**Critical**: act before the affected player logs in. If the player connects
before you restore their file, the server creates a fresh character for them
and saves it, overwriting the corrupt file permanently.

### Observed load-failure behaviour

When `JsonPlayerRepository.loadPlayer()` encounters a corrupt JSON file (e.g.
truncated write, disk error, manual edit mistake), it catches the
`IOException`/`JsonParseException`, logs an ERROR, and returns
`Optional.empty()`:

```
ERROR Failed to load player <username>  [exception detail]
```

The login flow's `orElseGet()` branch then creates a brand-new default
character for that username. On the player's next save, the corrupt file is
overwritten by the new empty character.

**The player loses all progress if no backup exists.**

### Recovery path

1. Identify the corrupt file from the `ERROR` log line — it will name the
   username.

2. Stop the server immediately, or at minimum act before the player reconnects.

3. Inspect the file:

   ```sh
   cat data/players/<username>.json
   ```

   Common causes:
   - Truncated file (partial write, disk-full): visible as cut-off JSON.
   - Manual edit error: look for syntax mistakes.
   - A `<username>.json.tmp` left over from a crashed save: if the `.tmp` is
     intact, rename it:

     ```sh
     mv data/players/<username>.json.tmp data/players/<username>.json
     ```

4. If the file is unrecoverable, restore from the most recent backup (see
   §3 Restore procedure).

5. Restart the server.

---

## 5. SSH host key

### Location

```
data/ssh/hostkey.pem
```

The file is written in the standard OpenSSH PEM format
(`-----BEGIN OPENSSH PRIVATE KEY-----`) and contains the server's host key
pair (Ed25519 when available, otherwise RSA-3072). It can be inspected with
standard tooling, e.g. `ssh-keygen -l -f data/ssh/hostkey.pem`.

### Legacy `hostkey.ser` cleanup

Older versions stored the host key as a Java-serialized `data/ssh/hostkey.ser`
file. On startup the server now deletes a leftover `hostkey.ser` automatically
(logging a one-line notice) and generates a fresh PEM key at
`data/ssh/hostkey.pem`. This is a one-time host key rotation: SSH clients will
see a **host key changed** warning once and must refresh their known-hosts
entry (see below). If you find a stray `hostkey.ser` in old backups, it can be
deleted — it is never read or written anymore.

### What happens if it is deleted

On the next startup, sshd generates a fresh key pair and writes a new
`hostkey.pem`. Existing SSH clients will see a **host key changed** warning and
refuse to connect until the known-hosts entry is updated or removed:

```sh
ssh-keygen -R "[localhost]:2222"
```

Telnet connections are unaffected.

### Rotation procedure

1. Stop the server.
2. Delete the key file:

   ```sh
   rm data/ssh/hostkey.pem
   ```

3. Start the server — a new key is generated automatically.
4. Notify users to remove the old known-hosts entry:

   ```sh
   ssh-keygen -R "[<host>]:<ssh-port>"
   ```

---

## 6. Logs and audit trail

### Application log (`jmud.log`)

Log4j2 writes to both the console and `jmud.log` in the current working
directory. The default pattern is:

```
2026-07-04 12:00:00.000 [tick-thread] <correlationId> <message>
```

`correlationId` is a UUID populated while a player command executes on the tick
thread. Log lines emitted outside a command context leave this field blank.

**JSON log mode** (NDJSON / JSON-Lines, one object per line) — useful for log
aggregators (Loki, Splunk, etc.):

```sh
# Gradle run
./gradlew run -Dlog4j2.configurationFile=log4j2-json.xml

# Distribution binary
JAVA_OPTS=-Dlog4j2.configurationFile=log4j2-json.xml /opt/jmud-1.0-SNAPSHOT/bin/jmud
```

Each line contains the fields `timestamp`, `level`, `thread`, `logger`,
`message`, `correlationId`, and `thrown` (only when an exception is present).

Log4j2 does not rotate `jmud.log` automatically. Add a `logrotate` entry if
you need rotation:

```
/opt/jmud/jmud.log {
    daily
    rotate 14
    compress
    missingok
    notifempty
    copytruncate
}
```

### Audit JSONL (`logs/audit-YYYY-MM-DD.jsonl`)

The audit sink writes one JSON object per line to a daily-rotating file under
the `logs/` directory. The default path prefix is `logs/audit.jsonl`; the sink
appends the date automatically (e.g. `logs/audit-2026-07-04.jsonl`).

Each entry records: event type, subject (player username), room ID, outcome,
extra context, a timestamp, and a `correlationId`. The `correlationId` matches
the one in `jmud.log` for the same command execution, allowing cross-referencing.

Relevant `jmud.properties` keys:

```properties
jmud.audit.enabled=true
jmud.audit.path=logs/audit.jsonl
jmud.audit.queue_size=2048
```

**Retention advice**: keep audit files for at least 90 days. If the audit queue
fills (2048 entries default), excess entries are dropped and a WARN is logged:

```
WARN Audit queue full, dropping entry
```

If this occurs frequently, either increase `jmud.audit.queue_size` or ensure
the disk is not saturated (see §8 Common failures).

---

## 7. Tick health

### What a tick-overrun WARN means

The tick loop runs every `jmud.tick.interval.ms` milliseconds (default 1000 ms)
on a single virtual thread. If processing all tickables takes longer than the
budget, you will see:

```
WARN Tick overran: <N> ms (budget <M> ms, <K> tickables)
```

On the **next tick** after an overrun, the scheduler identifies and logs the
slowest tickable at DEBUG level:

```
DEBUG Tick slowest tickable: <class.name> (<N> ms)
```

Occasional overruns (e.g. on startup or during a GC pause) are normal.
Sustained overruns indicate a performance problem.

### JMX metrics

When `jmud.metrics.enabled=true` (the default), Micrometer registers the
following JMX MBeans under the `metrics` domain:

| Metric name | Type | Description |
|---|---|---|
| `jmud.tick.duration` | Timer | Wall-clock duration of each tick (percentiles + histogram) |
| `jmud.tick.overruns` | Counter | Number of ticks that exceeded the budget |
| `jmud.tick.tickables` | Gauge | Number of registered tickables |
| `jmud.players.online` | Gauge | Number of currently connected players |
| `jmud.command.queue.size.total` | Gauge | Total pending player commands across all players |

Connect with JConsole or any JMX client:

```sh
jconsole <pid>
# or
jconsole service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi  # if remote JMX is configured
```

Navigate to **MBeans → metrics → jmud.tick.duration** to see percentiles.

To expose remote JMX, add to `JAVA_OPTS`:

```
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9999
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

Use authentication and TLS in any production environment.

### First-response checklist for tick overruns

1. **Check recurrence**: a single overrun is usually benign (GC, JIT warmup).
   Monitor `jmud.tick.overruns` over the next few minutes.

2. **Identify the slow tickable**: enable DEBUG logging
   (`rootLogger.level=DEBUG` in `log4j2.properties`) and look for the
   "Tick slowest tickable" line logged after the second overrun.

3. **Check online player count**: high `jmud.players.online` with many active
   combat encounters drives up tick time. Review `jmud.command.queue.size.total`
   for an unexpectedly deep command backlog.

4. **Check disk I/O**: sustained overruns may indicate that persistence writes
   are blocking. Run `iostat -x 1` or check `jmud.log` for save-related
   WARN/ERROR lines.

5. **Increase the tick budget temporarily**: raise `jmud.tick.interval.ms`
   (e.g. to `2000`) and restart. This buys time to diagnose without affecting
   gameplay severely.

6. **Heap pressure**: run `jconsole` → Memory tab; if the heap is near capacity
   and GC is running frequently, increase `-Xmx`.

---

## 8. Common failures

### Port already in use

Symptom:

```
ERROR Failed to bind telnet server on port 4444
java.net.BindException: Address already in use
```

Resolution:

```sh
# Find the occupying process
ss -tlnp | grep 4444
# or
lsof -i :4444

# Kill it (replace <pid>)
kill <pid>

# Or start jmud on a different port
./gradlew run --args="--telnet-port 4445"
```

### Wrong Java version

Symptom at startup:

```
UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java Runtime
```

Resolution:

```sh
java -version   # must be 26 or higher
```

Install the correct JDK and ensure it is first on `PATH`:

```sh
export JAVA_HOME=/path/to/jdk-26
export PATH=$JAVA_HOME/bin:$PATH
java -version
```

The Gradle wrapper respects `JAVA_HOME` — no additional configuration needed.

### Disk full — behaviour on save

When the disk is full, `JsonPlayerRepository.savePlayer()` fails mid-write.
Because saves use a temp file followed by an atomic rename, the **existing
player file is not corrupted** — the temp file (`<name>.json.tmp`) fails before
the rename is attempted. The error is logged:

```
ERROR Failed to save player <username>
io.taanielo.jmud.core.world.repository.RepositoryException: Failed to save player <username>
  Caused by: java.io.IOException: No space left on device
```

The player's in-memory state continues running, but **subsequent saves are
also lost** until disk space is freed.

Resolution:

1. Free disk space (clear old logs, archive audit files, etc.):

   ```sh
   df -h .
   du -sh logs/* | sort -rh | head -20
   ```

2. Restart the server after freeing space so a clean save is written immediately
   on login.

3. If the server cannot be restarted immediately, the player's state is preserved
   in memory for the current session but will be lost on disconnect. Inform the
   player if possible.

### Audit sink queue full

Symptom in log:

```
WARN Audit queue full, dropping entry
```

This means the background audit writer cannot keep up with the rate of events.
Common causes: disk I/O saturation, the audit file path being on a slow
network mount, or a very high player-command rate.

Resolution: increase `jmud.audit.queue_size` (default 2048), move the
`jmud.audit.path` to local fast storage, or reduce load.

---

## 9. Automated data backups

`scripts/backup-data.sh` creates a compressed, timestamped archive of the entire
`data/` tree and prunes archives older than a configurable number of days.

### What it archives

The script archives the entire `data/` directory, which includes all game content
(rooms, items, mobs, races, classes, skills, attacks, quests, shops, banks) as
well as the runtime-state directories (`users/`, `banks/`). Player save files
live in `players/` at the project root and are **not** included in the archive —
use a separate backup step for those if needed (see §3 Backup procedure).

### Running the script

```sh
# Run from the project root with default 14-day retention
./scripts/backup-data.sh

# Override retention period (keep last 30 days)
./scripts/backup-data.sh --retain-days 30
```

Archives are written to `backups/` in the project root (excluded from git).
Each archive is named `data-YYYY-MM-DD-HHmmSS.tar.gz`.

### Crontab example

The script header contains a ready-to-use crontab line. To install it:

```sh
crontab -e
```

Then add (adjust the path and retention as needed):

```
0 3 * * * cd /opt/jmud && ./scripts/backup-data.sh --retain-days 14 >> /var/log/jmud-backup.log 2>&1
```

This runs every day at 03:00 and retains the last 14 daily archives.

### Restore from archive

```sh
# Stop the server first to prevent concurrent writes
sudo systemctl stop jmud

# Move existing data aside in case you need to revert
mv data data.old

# Restore
tar xzf backups/data-YYYY-MM-DD-HHmmSS.tar.gz

# Verify then start
ls data/
sudo systemctl start jmud
```

If the restore looks correct, remove the old data:

```sh
rm -rf data.old
```

---

## 10. Validating game data (--validate-data)

The `--validate-data` startup flag instructs jmud to scan every JSON file in the
`data/` tree and the `players/` directory, report per-domain counts, and exit
without starting any servers. It does not require a running game instance.

### Usage

```sh
# Via Gradle (recommended for development)
./gradlew run --args='--validate-data'

# Via the distribution binary
/opt/jmud-1.0-SNAPSHOT/bin/jmud --validate-data
```

### Output

On success (all files parse cleanly):

```
  [OK]   rooms         12 file(s)
  [OK]   items         47 file(s)
  ...

Data validation PASSED: 134 file(s) across 13 domain(s)
```

On failure (one or more files are broken):

```
  [OK]   rooms         12 file(s)
  [FAIL] items         1 error(s) / 47 file(s)
         /opt/jmud/data/items/broken-sword.json
         -> ... parse error detail ...
  ...

Data validation FAILED: 1 error(s) in 134 file(s) scanned
```

The exit code is `0` on success and `1` on failure. CI runs this step
automatically for every pull request so broken data is caught before deployment.

### When to run it

- After manually editing any JSON file in `data/`
- Before deploying a new content update
- As a pre-flight check after restoring from a backup (see §9)

### What it validates

The validator checks every `*.json` file in the following domains (all relative
to `data/`): `rooms`, `items`, `mobs`, `attacks`, `skills`, `classes`, `races`,
`shops`, `quests`, `banks`, `users`, `characters`, plus all files under
`players/`. A file that produces no parse error is considered valid.
