#!/usr/bin/env bash
# End-to-end telnet smoke test for jmud.
#
# Starts the server on dedicated test ports, drives a scripted telnet session
# (new-user creation + existing-user login), asserts on the output, cleans up
# after itself, and exits 0 (PASS) or 1 (FAIL).
#
# Designed for agents and CI: run it, check the exit code, read the last lines.
# Full transcripts and the server log are kept under build/smoke-test/ for
# debugging; nothing is printed unless it matters.
#
# Usage:
#   scripts/smoke-test.sh                 # full run (starts its own server)
#   SMOKE_TELNET_PORT=5555 scripts/smoke-test.sh
#
# Requirements: bash, nc, ./gradlew (Java per gradle toolchain).

set -u

TELNET_PORT="${SMOKE_TELNET_PORT:-4491}"
SSH_PORT="${SMOKE_SSH_PORT:-4492}"
OUT_DIR="build/smoke-test"
SERVER_LOG="$OUT_DIR/server.log"
TEST_USER="smoke$(date +%s)"
TEST_ROGUE="rogue$(date +%s)"
TEST_PASS="smoketest123"
STARTUP_TIMEOUT=90
FAILURES=0
CHECKS=0

cd "$(dirname "$0")/.." || exit 1
mkdir -p "$OUT_DIR"

# The BOARD/NOTE phase posts a real note to the training-yard bulletin board, which the
# notes repository persists to data/boards/training-yard.json. Snapshot it up front so
# cleanup can restore the committed state regardless of how the phase ends.
BOARD_FILE="data/boards/training-yard.json"
BOARD_BACKUP="$OUT_DIR/training-yard.board.bak"
if [ -f "$BOARD_FILE" ]; then cp "$BOARD_FILE" "$BOARD_BACKUP"; fi

log()  { printf '%s\n' "$*"; }
pass() { CHECKS=$((CHECKS + 1)); log "  PASS: $*"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); log "  FAIL: $*"; }

server_pid() {
    # The test ports are on the java command line (--args), so this matches
    # only the instance this script started — never a dev server on 4444.
    pgrep -f -- "--telnet-port $TELNET_PORT" | head -1
}

cleanup() {
    local pid
    pid="$(server_pid || true)"
    if [ -n "${pid:-}" ]; then
        kill "$pid" 2>/dev/null
        for _ in 1 2 3 4 5; do
            kill -0 "$pid" 2>/dev/null || break
            sleep 1
        done
        kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
    fi
    rm -f "data/users/$TEST_USER.json" "players/$TEST_USER.json"
    rm -f "data/users/$TEST_ROGUE.json" "players/$TEST_ROGUE.json"
    # Restore the committed bulletin board so the smoke test leaves no trace.
    if [ -f "$BOARD_BACKUP" ]; then
        cp "$BOARD_BACKUP" "$BOARD_FILE"
    else
        rm -f "$BOARD_FILE"
    fi
}
trap cleanup EXIT

# ── guard: ports must be free ────────────────────────────────────────────────
if nc -z 127.0.0.1 "$TELNET_PORT" 2>/dev/null; then
    log "ABORT: port $TELNET_PORT already in use (another test run or server?)"
    exit 1
fi

# ── start server ─────────────────────────────────────────────────────────────
log "Starting server on telnet:$TELNET_PORT ssh:$SSH_PORT (log: $SERVER_LOG)"
./gradlew run --console=plain -q \
    --args="--telnet-port $TELNET_PORT --ssh-port $SSH_PORT" \
    > "$SERVER_LOG" 2>&1 &

waited=0
until nc -z 127.0.0.1 "$TELNET_PORT" 2>/dev/null; do
    sleep 1
    waited=$((waited + 1))
    if [ "$waited" -ge "$STARTUP_TIMEOUT" ]; then
        log "ABORT: server did not open port $TELNET_PORT within ${STARTUP_TIMEOUT}s"
        tail -20 "$SERVER_LOG"
        exit 1
    fi
done
log "Server up after ${waited}s"
# The nc -z probes above open/close bare TCP connections; the server logs a
# harmless broken-pipe for each. Let that noise flush, then record the log
# offset so the health check only scans what our real sessions caused.
sleep 2
LOG_OFFSET=$(wc -c < "$SERVER_LOG")

# ── session driver ───────────────────────────────────────────────────────────
# run_session <transcript-file> <line>...
# Sends each line with a 1.5s gap (one tick is 1s; commands execute on the
# tick), strips CR, saves the transcript. timeout is a belt-and-braces bound;
# QUIT closes the connection so nc normally exits on its own.
run_session() {
    local transcript="$1"; shift
    {
        sleep 1.5
        for line in "$@"; do
            printf '%s\r\n' "$line"
            sleep 1.5
        done
        sleep 2
    } | timeout 60 nc 127.0.0.1 "$TELNET_PORT" | tr -d '\r' > "$transcript"
}

# expect <transcript> <description> <grep-pattern>
expect() {
    local transcript="$1" desc="$2" pattern="$3"
    if grep -qE -- "$pattern" "$transcript"; then
        pass "$desc"
    else
        fail "$desc (pattern not found: $pattern)"
    fi
}

# ── phase 1: new-user creation, SCORE, QUIT ─────────────────────────────────
# Creation flow expects race/class NAMES (see data/races, data/classes).
log "Phase 1: create user '$TEST_USER', run SCORE"
T1="$OUT_DIR/phase1-new-user.txt"
run_session "$T1" "$TEST_USER" "$TEST_PASS" "$TEST_PASS" "human" "warrior" "score" "quit"

# Note: the prompt is written without a trailing newline, so command output
# can share a line with it — don't anchor patterns with ^ unless the line is
# known to start fresh.
expect "$T1" "prompt rendered after login"        '\[[0-9]+/[0-9]+hp .*\]'
expect "$T1" "SCORE header present"               '--- Score ---'
expect "$T1" "SCORE shows Level"                  'Level : [0-9]+'
expect "$T1" "SCORE shows AC"                     'AC    : [0-9]+'
if [ -f "data/users/$TEST_USER.json" ]; then
    pass "user file persisted (data/users/$TEST_USER.json)"
else
    fail "user file not persisted"
fi

# ── phase 2: existing-user login, WHO, QUIT ──────────────────────────────────
log "Phase 2: re-login as '$TEST_USER', run WHO"
T2="$OUT_DIR/phase2-relogin.txt"
run_session "$T2" "$TEST_USER" "$TEST_PASS" "who" "quit"

expect "$T2" "re-login reaches prompt"            '\[[0-9]+/[0-9]+hp .*\]'
expect "$T2" "WHO lists the test user"            "$TEST_USER"

# ── phase 2b: item durability / REPAIR command ───────────────────────────────
# The REPAIR command (issue #271) is served by a blacksmith NPC in the armory.
# A brand-new character starts in the training-yard with no gold, so rather than
# scripting a fragile buy/navigate/fight-until-broken flow we assert the command
# is wired up and gives its expected "no blacksmith here" guidance when invoked
# away from a forge. The break-in-combat and repair maths are covered by the
# ItemDurabilityService unit tests.
log "Phase 2b: REPAIR command wiring"
T2B="$OUT_DIR/phase2b-repair.txt"
run_session "$T2B" "$TEST_USER" "$TEST_PASS" "repair iron sword" "quit"

expect "$T2B" "REPAIR without a blacksmith is handled" 'no blacksmith here'

# ── phase 2c: locked container is visible and rogue-gated ────────────────────
# The training-yard holds a locked treasure chest (issue #277). A locked
# container shows "(locked)" in LOOK and only a rogue may PICK it; a warrior is
# turned away. Pick success/trap rolls are random, so downstream we assert the
# command is wired and produces one of its known outcomes rather than a fixed
# result — the success/trap maths are covered by the ContainerLockingService and
# GameActionService unit tests.
log "Phase 2c: locked chest visible; warrior cannot PICK"
T2C="$OUT_DIR/phase2c-warrior-pick.txt"
run_session "$T2C" "$TEST_USER" "$TEST_PASS" "look" "pick a treasure chest" "quit"

expect "$T2C" "locked chest shown in LOOK"        'treasure chest \(locked\)'
expect "$T2C" "warrior blocked from PICK"         'rogues'

# ── phase 2d: rogue examines and PICKs the locked chest, and SNEAKs ───────────
log "Phase 2d: create rogue '$TEST_ROGUE', EXAMINE/PICK the chest, SNEAK, and STEAL"
T2D="$OUT_DIR/phase2d-rogue-pick.txt"
run_session "$T2D" "$TEST_ROGUE" "$TEST_PASS" "$TEST_PASS" "human" "rogue" \
    "examine a treasure chest" "pick a treasure chest" "sneak" "sneak" \
    "steal training dummy" "quit"

expect "$T2D" "EXAMINE reports the chest is locked" 'It is locked.'
expect "$T2D" "PICK attempt resolves"               'pick the lock|fail to pick|trap'
expect "$T2D" "SNEAK toggles stealth on then off"   'fade into the shadows'
expect "$T2D" "SNEAK reveals on second toggle"      'emerge from stealth'
# The training dummy carries no gold, so STEAL resolves cleanly to its no-gold outcome.
expect "$T2D" "STEAL command executes"              'nothing worth stealing|lift .* gold|caught'

# ── phase 2e: consensual duel (DUEL/ACCEPT) command wiring ───────────────────
# Duels (issue #317) require two players connected at once, which the sequential
# single-session harness cannot coordinate; the full fight-to-near-death flow and
# its loot/XP suppression are covered by GameActionServicePlayerDuelTest. Here we
# assert both commands are registered and give their expected guidance when used
# solo: DUEL against an absent target, and ACCEPT with no pending challenge.
log "Phase 2e: DUEL/ACCEPT command wiring"
T2E="$OUT_DIR/phase2e-duel.txt"
run_session "$T2E" "$TEST_USER" "$TEST_PASS" "duel nobody" "accept" "quit"

expect "$T2E" "DUEL against an absent player is handled" 'no one here by that name to duel'
expect "$T2E" "ACCEPT with no pending challenge is handled" 'no pending duel challenge'

# ── phase 2f: bulletin board (BOARD/NOTE) ────────────────────────────────────
# New players spawn in the training-yard, whose committed board carries a welcome
# note. We read it (BOARD), pin our own note (NOTE POST), confirm it appears, then
# remove it (NOTE DELETE 2) so the persisted board returns to its committed state.
log "Phase 2f: BOARD/NOTE bulletin board"
T2F="$OUT_DIR/phase2f-board.txt"
run_session "$T2F" "$TEST_USER" "$TEST_PASS" \
    "board" "note post Smoke test note" "board" "note delete 2" "quit"

expect "$T2F" "BOARD shows the welcome note"        'Ancient Herald'
expect "$T2F" "NOTE POST is confirmed"              'note has been posted'
expect "$T2F" "posted note appears on the board"    'Smoke test note'
expect "$T2F" "NOTE DELETE removes the note"        'has been removed from the board'

# ── phase 2g: explored-room minimap (MAP) ────────────────────────────────────
# New players spawn in the training-yard (exits: north→armory, east→courtyard,
# south→sparring-pit). We walk to a few adjacent rooms so they enter the player's
# explored set, return to the yard, then run MAP. Explored neighbours render as
# '#', the current room as '@', and unexplored exits as '.'.
log "Phase 2g: MAP minimap of explored rooms"
T2G="$OUT_DIR/phase2g-map.txt"
run_session "$T2G" "$TEST_USER" "$TEST_PASS" \
    "north" "south" "east" "west" "map" "quit"

expect "$T2G" "MAP renders a header"                'Map of your surroundings:'
expect "$T2G" "MAP marks the current room"          '@'
expect "$T2G" "MAP marks an explored neighbour"     '#'
expect "$T2G" "MAP shows a legend"                  'Legend:'

# ── phase 2h: ignore list (IGNORE) ───────────────────────────────────────────
# A full cross-player TELL mute needs two simultaneous connections (like duels),
# which the sequential smoke harness cannot script. We instead exercise the whole
# IGNORE sub-command surface in one session: empty list, ADD, list, REMOVE, CLEAR.
log "Phase 2h: IGNORE mute list"
T2H="$OUT_DIR/phase2h-ignore.txt"
run_session "$T2H" "$TEST_USER" "$TEST_PASS" \
    "ignore" "ignore add Spammer" "ignore" "ignore remove Spammer" "ignore clear" "quit"

expect "$T2H" "IGNORE reports an empty list"        'not ignoring anyone'
expect "$T2H" "IGNORE ADD confirms the mute"        'now ignoring Spammer'
expect "$T2H" "IGNORE lists the muted player"       'spammer'
expect "$T2H" "IGNORE REMOVE confirms un-mute"      'no longer ignoring Spammer'
expect "$T2H" "IGNORE CLEAR is handled"             'ignore list (has been cleared|is already empty)'

# ── phase 3: server health ───────────────────────────────────────────────────
# Scan only log content produced after startup (see LOG_OFFSET above).
# Broken-pipe writes to just-disconnected clients are demoted to warnings —
# they can occur legitimately when a tick broadcast races a disconnect.
SESSION_LOG="$OUT_DIR/server-session-window.log"
tail -c "+$((LOG_OFFSET + 1))" "$SERVER_LOG" > "$SESSION_LOG"
PIPE_NOISE=$(grep -cE 'Broken pipe|Socket is closed|Error sending message|Error writing' "$SESSION_LOG" || true)
[ "${PIPE_NOISE:-0}" -gt 0 ] && log "  WARN: $PIPE_NOISE write-to-closed-socket line(s) in server log (tick vs disconnect races - known wart, see issues #180/#182)"
ERRORS=$(grep -iE 'exception|severe|\bERROR\b' "$SESSION_LOG" | grep -cvE 'Broken pipe|Socket is closed|Error sending message|Error writing' || true)
if [ "${ERRORS:-0}" -eq 0 ]; then
    pass "no unexpected errors/exceptions in server log"
else
    fail "server log contains $ERRORS unexpected error line(s) — see $SERVER_LOG"
    grep -iE 'exception|severe|\bERROR\b' "$SESSION_LOG" | grep -vE 'Broken pipe|Socket is closed|Error sending message|Error writing' | head -5
fi

# ── phase 4: graceful shutdown on SIGTERM ────────────────────────────────────
# SIGTERM (the default kill signal) must trigger ShutdownCoordinator's orderly
# sequence: stop accepting connections, notify clients, stop the tick scheduler,
# save online players, flush audit, clear the tick registry — then the JVM exits
# on its own. This must be the last phase: it terminates the server. We keep a
# player connected across the shutdown so the online-player save path runs, send
# SIGTERM (never kill -9), wait for the process to exit unaided, then assert the
# log shows the sequence both started and completed.
log "Phase 4: graceful shutdown on SIGTERM"
SHUTDOWN_PID="$(server_pid || true)"
if [ -z "${SHUTDOWN_PID:-}" ]; then
    fail "server not running before shutdown test"
else
    SHUTDOWN_OFFSET=$(wc -c < "$SERVER_LOG")
    # Hold a live session open across the shutdown so an online player is saved.
    # The server closes the connection when it shuts down, so nc exits on its own.
    T4="$OUT_DIR/phase4-shutdown.txt"
    {
        sleep 1.5
        printf '%s\r\n' "$TEST_USER"
        sleep 1.5
        printf '%s\r\n' "$TEST_PASS"
        sleep 30   # idle, holding the connection until the server shuts down
    } | timeout 45 nc 127.0.0.1 "$TELNET_PORT" | tr -d '\r' > "$T4" &
    NC_PID=$!
    sleep 6   # let the login land on a tick before we pull the plug

    kill "$SHUTDOWN_PID" 2>/dev/null   # SIGTERM — the graceful path
    exited=0
    for _ in $(seq 1 15); do
        kill -0 "$SHUTDOWN_PID" 2>/dev/null || { exited=1; break; }
        sleep 1
    done
    wait "$NC_PID" 2>/dev/null

    if [ "$exited" -eq 1 ]; then
        pass "server exited on its own after SIGTERM (no kill -9 needed)"
    else
        fail "server did not exit within 15s of SIGTERM — forcing kill -9"
        kill -9 "$SHUTDOWN_PID" 2>/dev/null
    fi

    # The JVM process is gone, but when running under `./gradlew run` its
    # stdout is relayed to us through the Gradle daemon's client protocol,
    # which can lag slightly behind the OS-level process exit. Give that
    # relay a moment to catch up before trusting the log file is complete.
    sleep 3

    # Scan only the log produced from SIGTERM onward.
    SHUTDOWN_LOG="$OUT_DIR/server-shutdown-window.log"
    tail -c "+$((SHUTDOWN_OFFSET + 1))" "$SERVER_LOG" > "$SHUTDOWN_LOG"
    expect "$SHUTDOWN_LOG" "shutdown sequence started"   'Shutdown sequence starting'
    expect "$SHUTDOWN_LOG" "shutdown sequence completed" 'Shutdown sequence complete'
fi

# ── summary ──────────────────────────────────────────────────────────────────
log ""
if [ "$FAILURES" -eq 0 ]; then
    log "SMOKE TEST PASSED ($CHECKS checks)"
    exit 0
else
    log "SMOKE TEST FAILED ($FAILURES of $CHECKS checks failed)"
    log "--- transcript phase 1 (tail) ---"; tail -15 "$T1"
    log "--- transcript phase 2 (tail) ---"; tail -15 "$T2"
    log "--- server log (tail) ---";         tail -15 "$SERVER_LOG"
    if [ -n "${SHUTDOWN_LOG:-}" ] && [ -f "$SHUTDOWN_LOG" ]; then
        log "--- shutdown log window (tail) ---"; tail -15 "$SHUTDOWN_LOG"
    fi
    exit 1
fi
