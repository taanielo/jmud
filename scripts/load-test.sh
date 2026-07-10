#!/usr/bin/env bash
# Concurrent load / tick-stability test for jmud.
#
# Where scripts/smoke-test.sh verifies end-to-end correctness with a single
# sequential telnet session, this harness exercises the concurrency model: it
# spawns N concurrent telnet clients (each an idle player running a light,
# repeating SCORE command), samples the wizard-only STATS command every few
# seconds to read the tick-loop health metrics, and asserts the tick loop stays
# stable under the load — the tick count keeps climbing, ticks never overrun,
# and per-tick duration stays within budget.
#
# It is an OPTIONAL stress test, not part of normal CI. Run it after a green
# smoke test when you want to check tick stability under realistic load.
#
# Usage:
#   scripts/load-test.sh                                  # 10 clients, 60s
#   scripts/load-test.sh --clients 20 --duration-secs 120
#   LOAD_TELNET_PORT=5555 scripts/load-test.sh --clients 5 --duration-secs 30
#
# Exits 0 (PASS) if every assertion passes, 1 (FAIL) otherwise. Full transcripts,
# the metrics snapshot and the server log are kept under build/load-test/.
#
# Requirements: bash, nc, ./gradlew (Java per gradle toolchain), awk, sed, grep.
#
# --- STATS output format (consumed by this parser) ---------------------------
# The wizard STATS command (StatsCommand.java, issue #345/#350) prints PLAIN
# TEXT, not JSON. The lines this script parses look like:
#
#   === Tick Health ===
#   Total ticks: 142
#   Avg duration: 5.3 ms | Max: 12.4 ms | Overruns: 0 (last 100 ticks)
#   Slowest Tickable: PlayerCommandQueue (543.0 ms / 142 invocations)
#
# We read "Total ticks" (monotonic uptime counter), "Max" (worst single-tick
# duration in the window, ms) and "Overruns" (ticks over budget in the window).
# If StatsCommand.format() changes, update the parse_* helpers below. The format
# is asserted by StatsCommandTest; the metrics themselves by TickMetricsServiceTest.

set -u

# ── argument parsing ─────────────────────────────────────────────────────────
CLIENTS=10
DURATION_SECS=60
TELNET_PORT="${LOAD_TELNET_PORT:-4493}"
SSH_PORT="${LOAD_SSH_PORT:-4494}"

usage() {
    cat >&2 <<'EOF'
Usage: scripts/load-test.sh [--clients N] [--duration-secs S] [--telnet-port PORT]
  --clients N         number of concurrent telnet clients (default 10)
  --duration-secs S   how long to hold the load, in seconds (default 60)
  --telnet-port PORT  telnet port for the test server (default 4493, or $LOAD_TELNET_PORT)
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --clients)        CLIENTS="${2:-}"; shift 2 ;;
        --duration-secs)  DURATION_SECS="${2:-}"; shift 2 ;;
        --telnet-port)    TELNET_PORT="${2:-}"; shift 2 ;;
        -h|--help)        usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage; exit 2 ;;
    esac
done

case "$CLIENTS" in ''|*[!0-9]*) echo "ABORT: --clients must be a positive integer" >&2; exit 2 ;; esac
case "$DURATION_SECS" in ''|*[!0-9]*) echo "ABORT: --duration-secs must be a positive integer" >&2; exit 2 ;; esac
case "$TELNET_PORT" in ''|*[!0-9]*) echo "ABORT: --telnet-port must be a port number" >&2; exit 2 ;; esac
[ "$CLIENTS" -ge 1 ] || { echo "ABORT: --clients must be >= 1" >&2; exit 2; }
[ "$DURATION_SECS" -ge 5 ] || { echo "ABORT: --duration-secs must be >= 5" >&2; exit 2; }

OUT_DIR="build/load-test"
SERVER_LOG="$OUT_DIR/server.log"
METRICS_FILE="$OUT_DIR/metrics.tsv"
RUN_ID="$(date +%s)"
WIZARD_USER="loadwiz${RUN_ID}"
LOAD_PASS="loadtest123"
STARTUP_TIMEOUT=90
SAMPLE_INTERVAL=8

FAILURES=0
CHECKS=0
CLIENT_USERS=()
CLIENT_PIDS=()

cd "$(dirname "$0")/.." || exit 1
mkdir -p "$OUT_DIR"

log()  { printf '%s\n' "$*"; }
pass() { CHECKS=$((CHECKS + 1)); log "  PASS: $*"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); log "  FAIL: $*"; }

server_pid() {
    # The test port is on the java command line (--args), so this matches only
    # the instance this script started — never a dev server on 4444.
    pgrep -f -- "--telnet-port $TELNET_PORT" | head -1
}

# ── wizard config: grant the sampling user wizard rights for STATS ───────────
# STATS is wizard-gated (jmud.wizards, empty by default). Rather than fight the
# Gradle daemon's cached environment (which makes JAVA_TOOL_OPTIONS/-D flaky for
# the forked `run` JVM), we set the wizard username directly in the properties
# resource that `./gradlew run` reprocesses onto the classpath, and restore the
# committed file on cleanup — the same backup/restore pattern smoke-test.sh uses
# for the bulletin board.
WIZARD_CONFIG="src/main/resources/jmud.properties"
WIZARD_CONFIG_BACKUP="$OUT_DIR/jmud.properties.bak"

cleanup() {
    local pid user
    # Stop the load clients first so they stop hammering the server.
    for pid in "${CLIENT_PIDS[@]:-}"; do
        [ -n "$pid" ] && kill "$pid" 2>/dev/null
    done
    pid="$(server_pid || true)"
    if [ -n "${pid:-}" ]; then
        kill "$pid" 2>/dev/null
        for _ in 1 2 3 4 5; do
            kill -0 "$pid" 2>/dev/null || break
            sleep 1
        done
        kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
    fi
    # Restore the committed properties file (wizard override was temporary).
    if [ -f "$WIZARD_CONFIG_BACKUP" ]; then
        cp "$WIZARD_CONFIG_BACKUP" "$WIZARD_CONFIG"
    fi
    # Remove every test user this run created (wizard + all load clients).
    rm -f "data/users/$WIZARD_USER.json" "players/$WIZARD_USER.json"
    for user in "${CLIENT_USERS[@]:-}"; do
        [ -n "$user" ] && rm -f "data/users/$user.json" "players/$user.json"
    done
}
trap cleanup EXIT

# ── guard: port must be free ─────────────────────────────────────────────────
if nc -z 127.0.0.1 "$TELNET_PORT" 2>/dev/null; then
    log "ABORT: port $TELNET_PORT already in use (another test run or server?)"
    exit 1
fi

# ── apply temporary wizard config ────────────────────────────────────────────
cp "$WIZARD_CONFIG" "$WIZARD_CONFIG_BACKUP"
sed -i "s/^jmud\.wizards=.*/jmud.wizards=$WIZARD_USER/" "$WIZARD_CONFIG"
if ! grep -q "^jmud.wizards=$WIZARD_USER$" "$WIZARD_CONFIG"; then
    log "ABORT: could not set wizard user in $WIZARD_CONFIG"
    exit 1
fi

# ── start server ─────────────────────────────────────────────────────────────
log "Starting server on telnet:$TELNET_PORT ssh:$SSH_PORT (log: $SERVER_LOG)"
log "Load: $CLIENTS clients, ${DURATION_SECS}s, wizard sampler '$WIZARD_USER'"
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
# nc -z probes above open/close bare TCP connections; the server logs a harmless
# broken-pipe for each. Let that noise flush, then record the log offset so the
# health check only scans what our real sessions caused.
sleep 2
LOG_OFFSET=$(wc -c < "$SERVER_LOG")

# ── session driver (same shape as smoke-test.sh) ─────────────────────────────
# run_session <transcript-file> <line>...
# Sends each line with a 1.5s gap (one tick is 1s; commands execute on the tick),
# strips CR, saves the transcript. timeout is a belt-and-braces bound; QUIT
# closes the connection so nc normally exits on its own.
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

# ── STATS parsers (see format banner at top of file) ─────────────────────────
parse_total_ticks() { grep -oE 'Total ticks: [0-9]+' "$1" | tail -1 | grep -oE '[0-9]+$'; }
parse_max_ms()      { grep -oE 'Max: [0-9.]+ ms'     "$1" | tail -1 | grep -oE '[0-9.]+'; }
parse_overruns()    { grep -oE 'Overruns: [0-9]+'    "$1" | tail -1 | grep -oE '[0-9]+$'; }

# ── baseline: create wizard, read starting tick count ────────────────────────
log "Creating wizard sampler and reading baseline tick count"
BASELINE="$OUT_DIR/wizard-baseline.txt"
run_session "$BASELINE" "$WIZARD_USER" "$LOAD_PASS" "$LOAD_PASS" "human" "warrior" "stats" "quit"

if ! grep -q 'restricted to wizards' "$BASELINE" && grep -q 'Tick Health' "$BASELINE"; then
    pass "wizard STATS is accessible to the sampler"
else
    fail "wizard STATS was not accessible — cannot sample tick metrics"
    log "--- baseline transcript (tail) ---"; tail -20 "$BASELINE"
    log ""
    log "LOAD TEST FAILED (setup)"
    exit 1
fi

START_TICKS="$(parse_total_ticks "$BASELINE")"
START_TICKS="${START_TICKS:-0}"
log "Baseline tick count: $START_TICKS"

# ── spawn N concurrent load clients ──────────────────────────────────────────
# Each client creates a fresh character then loops SCORE every ~3 ticks for the
# run duration, keeping the tick thread under moderate, sustained load without
# stalling the test. A generous timeout bounds a wedged client.
spawn_client() {
    local idx="$1"
    local user="load${RUN_ID}c${idx}"
    CLIENT_USERS+=("$user")
    local transcript="$OUT_DIR/client-$idx.txt"
    {
        sleep 1.5
        printf '%s\r\n' "$user"       # new username
        sleep 1.5
        printf '%s\r\n' "$LOAD_PASS"  # password
        sleep 1.5
        printf '%s\r\n' "$LOAD_PASS"  # confirm password
        sleep 1.5
        printf '%s\r\n' "human"       # race (name, not menu number)
        sleep 1.5
        printf '%s\r\n' "warrior"     # class
        sleep 2
        local deadline=$(( $(date +%s) + DURATION_SECS ))
        while [ "$(date +%s)" -lt "$deadline" ]; do
            printf '%s\r\n' "score"
            sleep 3
        done
        printf '%s\r\n' "quit"
        sleep 1
    } | timeout $((DURATION_SECS + 90)) nc 127.0.0.1 "$TELNET_PORT" | tr -d '\r' > "$transcript" &
    CLIENT_PIDS+=($!)
}

log "Spawning $CLIENTS concurrent clients"
i=1
while [ "$i" -le "$CLIENTS" ]; do
    spawn_client "$i"
    i=$((i + 1))
done

# ── sample tick health while the load runs ───────────────────────────────────
# Each STATS sample is a full wizard login/logout and takes ~8-10s, so the loop
# self-paces at roughly one sample per SAMPLE_INTERVAL without an extra sleep.
: > "$METRICS_FILE"
printf 'sample\ttotal_ticks\tmax_ms\toverruns\n' >> "$METRICS_FILE"

START_TIME="$(date +%s)"
END_TIME=$((START_TIME + DURATION_SECS))
SAMPLE_NUM=0
MAX_TICK_MS=0
MAX_OVERRUNS=0
PREV_TICKS="$START_TICKS"
MONOTONIC_OK=1
SAMPLES_TAKEN=0
LAST_TICKS="$START_TICKS"

take_sample() {
    SAMPLE_NUM=$((SAMPLE_NUM + 1))
    local t="$OUT_DIR/stats-sample-$SAMPLE_NUM.txt"
    run_session "$t" "$WIZARD_USER" "$LOAD_PASS" "stats" "quit"

    local ticks maxms overruns
    ticks="$(parse_total_ticks "$t")"
    maxms="$(parse_max_ms "$t")"
    overruns="$(parse_overruns "$t")"
    if [ -z "$ticks" ] || [ -z "$maxms" ] || [ -z "$overruns" ]; then
        log "  WARN: sample $SAMPLE_NUM did not yield parseable STATS output (see $t)"
        return
    fi

    SAMPLES_TAKEN=$((SAMPLES_TAKEN + 1))
    LAST_TICKS="$ticks"
    printf '%d\t%s\t%s\t%s\n' "$SAMPLE_NUM" "$ticks" "$maxms" "$overruns" >> "$METRICS_FILE"
    log "  sample $SAMPLE_NUM: ticks=$ticks max_ms=$maxms overruns=$overruns"

    # Track worst-case duration and overruns across the run.
    MAX_TICK_MS="$(awk -v a="$MAX_TICK_MS" -v b="$maxms" 'BEGIN{print (b>a)?b:a}')"
    [ "$overruns" -gt "$MAX_OVERRUNS" ] && MAX_OVERRUNS="$overruns"
    # Tick count must never go backwards.
    if [ "$ticks" -lt "$PREV_TICKS" ]; then
        MONOTONIC_OK=0
        log "  WARN: tick count went backwards ($PREV_TICKS -> $ticks)"
    fi
    PREV_TICKS="$ticks"
}

log "Sampling tick health every ~${SAMPLE_INTERVAL}s for ${DURATION_SECS}s"
while [ "$(date +%s)" -lt "$END_TIME" ]; do
    take_sample
done
# One final sample after the load window closes for the end tick count.
take_sample
END_TICKS="$LAST_TICKS"

# ── wait for clients to finish ───────────────────────────────────────────────
log "Load window complete; waiting for clients to disconnect"
for pid in "${CLIENT_PIDS[@]:-}"; do
    [ -n "$pid" ] && wait "$pid" 2>/dev/null
done

# ── assertions ───────────────────────────────────────────────────────────────
TICK_DELTA=$((END_TICKS - START_TICKS))

if [ "$SAMPLES_TAKEN" -ge 1 ]; then
    pass "collected $SAMPLES_TAKEN tick-health sample(s)"
else
    fail "no parseable STATS samples were collected"
fi

# Tick count grew by at least the load duration (nominal 1 tick/sec). The wall
# time from baseline to the final sample exceeds DURATION_SECS, so a healthy loop
# clears this comfortably; a stalled loop (ticks slower than 1s) falls short.
if [ "$TICK_DELTA" -ge "$DURATION_SECS" ]; then
    pass "tick count advanced $TICK_DELTA ticks over ${DURATION_SECS}s (>= ${DURATION_SECS})"
else
    fail "tick count only advanced $TICK_DELTA ticks over ${DURATION_SECS}s (expected >= ${DURATION_SECS}) — tick stall?"
fi

if [ "$MONOTONIC_OK" -eq 1 ]; then
    pass "tick count increased monotonically across samples"
else
    fail "tick count regressed between samples — see $METRICS_FILE"
fi

# Some jitter under load is fine; runaway growth is not (budget is 1000 ms/tick).
if awk -v v="$MAX_TICK_MS" 'BEGIN{exit !(v < 1500)}'; then
    pass "max tick duration ${MAX_TICK_MS} ms < 1500 ms"
else
    fail "max tick duration ${MAX_TICK_MS} ms >= 1500 ms — tick loop degraded under load"
fi

if [ "$MAX_OVERRUNS" -eq 0 ]; then
    pass "no tick overruns (0)"
else
    fail "tick loop overran its budget $MAX_OVERRUNS time(s) under load"
fi

# ── server health (same demotion rules as smoke-test.sh phase 3) ─────────────
# Broken-pipe writes to just-disconnected clients are demoted to warnings — they
# can occur legitimately when a tick broadcast races a disconnect, and this test
# connects/disconnects many clients.
SESSION_LOG="$OUT_DIR/server-session-window.log"
tail -c "+$((LOG_OFFSET + 1))" "$SERVER_LOG" > "$SESSION_LOG"
PIPE_NOISE=$(grep -cE 'Broken pipe|Socket is closed|Error sending message|Error writing|Error receiving' "$SESSION_LOG" || true)
[ "${PIPE_NOISE:-0}" -gt 0 ] && log "  WARN: $PIPE_NOISE write/read-to-closed-socket line(s) in server log (tick vs disconnect races - known wart, see issues #180/#182)"
ERRORS=$(grep -iE 'exception|severe|\bERROR\b' "$SESSION_LOG" | grep -cvE 'Broken pipe|Socket is closed|Error sending message|Error writing|Error receiving' || true)
if [ "${ERRORS:-0}" -eq 0 ]; then
    pass "no unexpected errors/exceptions in server log"
else
    fail "server log contains $ERRORS unexpected error line(s) — see $SERVER_LOG"
    grep -iE 'exception|severe|\bERROR\b' "$SESSION_LOG" | grep -vE 'Broken pipe|Socket is closed|Error sending message|Error writing|Error receiving' | head -5
fi

# ── summary ──────────────────────────────────────────────────────────────────
log ""
SUMMARY="$CLIENTS clients, ${DURATION_SECS}s, $TICK_DELTA ticks, max-tick-ms=$MAX_TICK_MS, overruns=$MAX_OVERRUNS"
if [ "$FAILURES" -eq 0 ]; then
    log "LOAD TEST PASSED: $SUMMARY ($CHECKS checks)"
    exit 0
else
    log "LOAD TEST FAILED ($FAILURES of $CHECKS checks failed): $SUMMARY"
    log "--- metrics ($METRICS_FILE) ---"; cat "$METRICS_FILE"
    log "--- server log (tail) ---";       tail -20 "$SERVER_LOG"
    exit 1
fi
