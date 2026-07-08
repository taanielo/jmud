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
log "Phase 2d: create rogue '$TEST_ROGUE', EXAMINE/PICK the chest, and SNEAK"
T2D="$OUT_DIR/phase2d-rogue-pick.txt"
run_session "$T2D" "$TEST_ROGUE" "$TEST_PASS" "$TEST_PASS" "human" "rogue" \
    "examine a treasure chest" "pick a treasure chest" "sneak" "sneak" "quit"

expect "$T2D" "EXAMINE reports the chest is locked" 'It is locked.'
expect "$T2D" "PICK attempt resolves"               'pick the lock|fail to pick|trap'
expect "$T2D" "SNEAK toggles stealth on then off"   'fade into the shadows'
expect "$T2D" "SNEAK reveals on second toggle"      'emerge from stealth'

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
    exit 1
fi
