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
WS_PORT="${SMOKE_WS_PORT:-4493}"
OUT_DIR="build/smoke-test"
SERVER_LOG="$OUT_DIR/server.log"
WS_CLIENT="$OUT_DIR/ws_client.py"
TEST_USER="smoke$(date +%s)"
TEST_ROGUE="rogue$(date +%s)"
TEST_LINK="link$(date +%s)"
TEST_CREA="crea$(date +%s)"
TEST_CREB="creb$(date +%s)"
TEST_DUMMY="dummy$(date +%s)"
TEST_WS="wsock$(date +%s)"
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

# The AUCTION phase lists an item, which the auction repository persists to
# data/auctions/listings.json. Snapshot it (it may not exist yet) so cleanup can
# restore the committed state — or remove a file this test created.
AUCTION_FILE="data/auctions/listings.json"
AUCTION_BACKUP="$OUT_DIR/auctions.listings.bak"
AUCTION_EXISTED=0
if [ -f "$AUCTION_FILE" ]; then cp "$AUCTION_FILE" "$AUCTION_BACKUP"; AUCTION_EXISTED=1; fi

# The AUCTION phase also picks up the iron sword from the training-yard floor,
# which JsonRoomRepository.save() persists straight back to
# data/rooms/training-yard.json (room item state is live, not a static
# template) — so the committed file loses "iron-sword" once this test runs.
# Snapshot it up front so cleanup can restore the committed state.
TRAINING_YARD_ROOM_FILE="data/rooms/training-yard.json"
TRAINING_YARD_ROOM_BACKUP="$OUT_DIR/training-yard.room.bak"
if [ -f "$TRAINING_YARD_ROOM_FILE" ]; then cp "$TRAINING_YARD_ROOM_FILE" "$TRAINING_YARD_ROOM_BACKUP"; fi

log()  { printf '%s\n' "$*"; }
pass() { CHECKS=$((CHECKS + 1)); log "  PASS: $*"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); log "  FAIL: $*"; }

server_pid() {
    # The test ports are on the java command line (--args), so this matches
    # only the instance this script started — never a dev server on 4444.
    pgrep -f -- "--telnet-port $TELNET_PORT" | head -1
}

# Phase 4 runs a second, dedicated server instance launched directly from the
# installDist binary (plain `java`, no Gradle daemon in front of it) on its
# own ports — see the phase 4 comment for why `./gradlew run` can't be used
# for a SIGTERM test.
SHUTDOWN_TELNET_PORT="${SMOKE_SHUTDOWN_TELNET_PORT:-4591}"
SHUTDOWN_SSH_PORT="${SMOKE_SHUTDOWN_SSH_PORT:-4592}"
shutdown_server_pid() {
    pgrep -f -- "--telnet-port $SHUTDOWN_TELNET_PORT" | head -1
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
    pid="$(shutdown_server_pid || true)"
    if [ -n "${pid:-}" ]; then
        kill -9 "$pid" 2>/dev/null
    fi
    rm -f "data/users/$TEST_USER.json" "players/$TEST_USER.json"
    rm -f "data/users/$TEST_ROGUE.json" "players/$TEST_ROGUE.json"
    rm -f "data/users/$TEST_LINK.json" "players/$TEST_LINK.json"
    rm -f "data/users/$TEST_CREA.json" "players/$TEST_CREA.json"
    rm -f "data/users/$TEST_CREB.json" "players/$TEST_CREB.json"
    rm -f "data/users/$TEST_DUMMY.json" "players/$TEST_DUMMY.json"
    rm -f "data/users/$TEST_WS.json" "players/$TEST_WS.json"
    # Restore the committed bulletin board so the smoke test leaves no trace.
    if [ -f "$BOARD_BACKUP" ]; then
        cp "$BOARD_BACKUP" "$BOARD_FILE"
    else
        rm -f "$BOARD_FILE"
    fi
    # Restore/remove the auction listings file so the smoke test leaves no trace.
    if [ "$AUCTION_EXISTED" -eq 1 ] && [ -f "$AUCTION_BACKUP" ]; then
        cp "$AUCTION_BACKUP" "$AUCTION_FILE"
    else
        rm -f "$AUCTION_FILE"
    fi
    # Restore the committed training-yard room state (iron sword picked up above).
    if [ -f "$TRAINING_YARD_ROOM_BACKUP" ]; then
        cp "$TRAINING_YARD_ROOM_BACKUP" "$TRAINING_YARD_ROOM_FILE"
    fi
}
trap cleanup EXIT

# ── guard: ports must be free ────────────────────────────────────────────────
if nc -z 127.0.0.1 "$TELNET_PORT" 2>/dev/null; then
    log "ABORT: port $TELNET_PORT already in use (another test run or server?)"
    exit 1
fi

# ── start server ─────────────────────────────────────────────────────────────
log "Starting server on telnet:$TELNET_PORT ssh:$SSH_PORT ws:$WS_PORT (log: $SERVER_LOG)"
./gradlew run --console=plain -q \
    --args="--telnet-port $TELNET_PORT --ssh-port $SSH_PORT --ws-port $WS_PORT" \
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
log "Phase 1: create user '$TEST_USER', run SCORE and accept the starter quest"
T1="$OUT_DIR/phase1-new-user.txt"
# A brand-new character should be able to discover, accept and track the level-1
# starter quest (issue #518). QUEST LIST/ACCEPT/STATUS resolve at the Guild Clerk
# but do not require standing in the Courtyard to inspect the contract board.
run_session "$T1" "$TEST_USER" "$TEST_PASS" "$TEST_PASS" "human" "warrior" \
    "score" "inventory" "quest list" "quest accept rat-catcher" "quest status" "quest abandon" \
    "drop all" "get all" "quit"

# Note: the prompt is written without a trailing newline, so command output
# can share a line with it — don't anchor patterns with ^ unless the line is
# known to start fresh.
expect "$T1" "prompt rendered after login"        '\[[0-9]+/[0-9]+hp .*\]'
# Creation prompts describe each race/class and list a class's starting abilities
# (issue #521) so players can make an informed pick instead of choosing blind.
expect "$T1" "race prompt shows a description"     'Versatile and adaptable'
expect "$T1" "class prompt lists starting abilities" 'Starting abilities:'
expect "$T1" "onboarding hint shown at creation"  'WIELD IRON SWORD'
expect "$T1" "onboarding points at the Guild Clerk" 'QUEST LIST at the Guild Clerk'
expect "$T1" "SCORE header present"               '--- Score ---'
expect "$T1" "SCORE shows Level"                  'Level : [0-9]+'
# Core attributes (issue #524): SCORE lists the four derived attributes STR/INT/WIS/AGI.
expect "$T1" "SCORE shows core attributes"        'Attrs : STR [0-9]+  INT [0-9]+  WIS [0-9]+  AGI [0-9]+'
expect "$T1" "SCORE shows AC"                     'AC    : [0-9]+'
# Newbie starting kit (issue #519): a fresh character receives a small gold purse plus
# provisions (bread + waterskin) so they can eat, drink and buy a meal before their first
# fight. Values are data-driven in data/newbie-kit.json.
expect "$T1" "SCORE shows a non-zero starting gold purse" 'Gold  : [1-9][0-9]*'
expect "$T1" "INVENTORY holds starter bread"      'Loaf of Bread'
expect "$T1" "INVENTORY holds starter waterskin"  'Waterskin'
# Bulk item handling (issue #641): DROP ALL empties the unequipped starter pack onto the floor
# in one summarized line, and GET ALL scoops it all back up in one command.
expect "$T1" "DROP ALL drops the whole pack"      'You drop .+\. \([0-9]+ items?\)'
expect "$T1" "GET ALL recovers the whole pack"    'You get .+\. \([0-9]+ items?\)'
expect "$T1" "QUEST LIST shows the contract board" 'Available Contracts'
expect "$T1" "QUEST LIST shows a recommended level column" 'Lvl'
expect "$T1" "QUEST LIST offers the starter quest"  'rat-catcher'
expect "$T1" "starter quest can be accepted"        'Contract accepted: Rat Catcher'
expect "$T1" "QUEST STATUS tracks the starter quest" 'Rat Catcher: 0/5 kills'
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

# ── phase 2j: WebSocket transport (issue #526) ───────────────────────────────
# The WebSocket endpoint (--ws-port) serves the same game, login flow and single-
# writer tick model as telnet; only the wire framing differs (RFC 6455 text frames,
# no telnet IAC bytes). We drive it with a dependency-free Python client (stdlib
# sockets + a minimal handshake/framing implementation) that creates a character,
# runs SCORE/WHO and QUITs. Skipped cleanly when python3 is unavailable.
log "Phase 2j: WebSocket create character, SCORE/WHO, QUIT"
if command -v python3 >/dev/null 2>&1; then
    cat > "$WS_CLIENT" <<'PYEOF'
import base64, os, socket, struct, sys, time

host, port = sys.argv[1], int(sys.argv[2])
lines = sys.argv[3:]

sock = socket.create_connection((host, port), timeout=15)
key = base64.b64encode(os.urandom(16)).decode("ascii")
handshake = (
    "GET / HTTP/1.1\r\n"
    f"Host: {host}:{port}\r\n"
    "Upgrade: websocket\r\n"
    "Connection: Upgrade\r\n"
    f"Sec-WebSocket-Key: {key}\r\n"
    "Sec-WebSocket-Version: 13\r\n"
    "Origin: http://localhost\r\n"
    "\r\n"
)
sock.sendall(handshake.encode("ascii"))

buf = b""
while b"\r\n\r\n" not in buf:
    chunk = sock.recv(4096)
    if not chunk:
        break
    buf += chunk

def send_text(text):
    payload = text.encode("utf-8")
    header = bytearray([0x81])
    mask = os.urandom(4)
    n = len(payload)
    if n <= 125:
        header.append(0x80 | n)
    elif n <= 0xFFFF:
        header.append(0x80 | 126)
        header += struct.pack(">H", n)
    else:
        header.append(0x80 | 127)
        header += struct.pack(">Q", n)
    header += mask
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    sock.sendall(bytes(header) + masked)

raw = bytearray()

def drain(timeout):
    sock.settimeout(timeout)
    try:
        while True:
            data = sock.recv(4096)
            if not data:
                break
            raw.extend(data)
    except socket.timeout:
        pass

drain(1.5)
for line in lines:
    send_text(line + "\n")
    drain(1.5)
drain(2.0)
sock.close()

def parse(data):
    i, n, out = 0, len(data), []
    while i + 2 <= n:
        b0, b1 = data[i], data[i + 1]
        i += 2
        opcode = b0 & 0x0F
        length = b1 & 0x7F
        if length == 126:
            if i + 2 > n:
                break
            length = (data[i] << 8) | data[i + 1]
            i += 2
        elif length == 127:
            if i + 8 > n:
                break
            length = 0
            for k in range(8):
                length = (length << 8) | data[i + k]
            i += 8
        if i + length > n:
            break
        payload = data[i:i + length]
        i += length
        if opcode in (0x0, 0x1):
            out.append(payload.decode("utf-8", "replace"))
    return "".join(out)

sys.stdout.write(parse(raw))
PYEOF
    T2J="$OUT_DIR/phase2j-websocket.txt"
    python3 "$WS_CLIENT" 127.0.0.1 "$WS_PORT" \
        "$TEST_WS" "$TEST_PASS" "human" "warrior" "score" "who" "quit" \
        | tr -d '\r' > "$T2J" 2>/dev/null

    expect "$T2J" "WS creation completes and enters the world" 'Welcome to the realm!'
    expect "$T2J" "WS session reaches the in-world prompt"     '\[[0-9]+/[0-9]+hp .*\]'
    expect "$T2J" "WS SCORE header present"                   '--- Score ---'
    expect "$T2J" "WS WHO lists the WebSocket user"           "$TEST_WS"

    # ── phase 2k: static browser client served on the same port (issue #527) ──
    # A plain GET / on the WebSocket port must return the single-page terminal
    # client from web/, proving the embedded HTTP server serves static assets.
    log "Phase 2k: browser web client served over HTTP GET /"
    T2K="$OUT_DIR/phase2k-webclient.txt"
    python3 - "127.0.0.1" "$WS_PORT" > "$T2K" 2>/dev/null <<'PYEOF'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
sock = socket.create_connection((host, port), timeout=15)
sock.sendall(f"GET / HTTP/1.1\r\nHost: {host}:{port}\r\nConnection: close\r\n\r\n".encode("ascii"))
buf = b""
sock.settimeout(5)
try:
    while True:
        chunk = sock.recv(4096)
        if not chunk:
            break
        buf += chunk
except socket.timeout:
    pass
sock.close()
sys.stdout.write(buf.decode("utf-8", "replace"))
PYEOF
    expect "$T2K" "GET / returns HTTP 200"                   '^HTTP/1.1 200 OK'
    expect "$T2K" "GET / serves HTML content type"          'Content-Type: text/html'
    expect "$T2K" "GET / serves the jmud web client page"   '<title>jmud'
else
    log "  SKIP: python3 not available; WebSocket phase skipped"
fi

# ── phase 2b: item durability / REPAIR command ───────────────────────────────
# The REPAIR command (issue #271) is served by a blacksmith NPC in the armory.
# A brand-new character starts in the training-yard away from any forge, so rather
# than scripting a fragile buy/navigate/fight-until-broken flow we assert the command
# is wired up and gives its expected "no blacksmith here" guidance when invoked
# away from a forge. The break-in-combat and repair maths are covered by the
# ItemDurabilityService unit tests.
log "Phase 2b: REPAIR command wiring"
T2B="$OUT_DIR/phase2b-repair.txt"
run_session "$T2B" "$TEST_USER" "$TEST_PASS" "repair iron sword" "quit"

expect "$T2B" "REPAIR without a blacksmith is handled" 'no blacksmith here'

# ── phase 2b2: STATS command is wizard-gated ─────────────────────────────────
# The STATS command (issue #345) reports tick-loop health and is restricted to
# wizards (config key jmud.wizards, empty by default). A regular test user is not
# a wizard, so STATS must be denied. The metrics aggregation and output format are
# covered by TickMetricsServiceTest / StatsCommandTest unit tests.
log "Phase 2b2: STATS is wizard-gated (non-wizard denied)"
T2B2="$OUT_DIR/phase2b2-stats.txt"
run_session "$T2B2" "$TEST_USER" "$TEST_PASS" "stats" "quit"

expect "$T2B2" "STATS denied for non-wizard" 'restricted to wizards'

# ── phase 2b3: TRAIN LIST works on day one (issue #516) ──────────────────────
# A fresh character starts in the training-yard with the Master Trainer present and
# starting practice points. TRAIN LIST must show the class's advanced abilities — for a
# warrior that is skill.second-wind (level 3) / skill.taunt (level 5). This guards against
# the regression where every class ability was auto-granted at creation, leaving the
# trainer with nothing to teach. Per issue #522 the status column is level-gated: at
# level 1 both warrior abilities are above the character's level, so they appear as
# "requires level N" rather than immediately trainable.
log "Phase 2b3: TRAIN LIST shows learnable entries for a fresh warrior"
T2B3="$OUT_DIR/phase2b3-train.txt"
run_session "$T2B3" "$TEST_USER" "$TEST_PASS" "train list" "quit"

expect "$T2B3" "TRAIN LIST shows the trainer header"     'Trainable Abilities'
expect "$T2B3" "TRAIN LIST reports practice points"      'Practice Points: [1-9]'
expect "$T2B3" "TRAIN LIST lists a level-gated skill"    'skill\.(second-wind|taunt) .*requires level [0-9]'

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

# ── phase 2d2: worded combat output (issue #525) ─────────────────────────────
# Classic-MUD worded damage replaces numeric combat output: striking the 100-HP
# training dummy in the training-yard reports a damage-tier verb ("You scratch the
# Training Dummy!") and, because the dummy survives, a condition phrase describing
# its remaining health in words instead of an HP total. The verb/condition bucket
# maths are covered exhaustively by DamageVerbTableTest/TargetConditionTableTest;
# here we assert the words reach a real client and carry no numbers. Combat
# state persists across reconnects (FLEE is the only way out and it relocates
# the player to a random exit, which would corrupt later phases' assumption
# that $TEST_USER is in the training-yard), so this uses its own throwaway
# character that no later phase touches again.
log "Phase 2d2: worded damage verb + condition line when attacking the training dummy"
T2D2="$OUT_DIR/phase2d2-worded-combat.txt"
run_session "$T2D2" "$TEST_DUMMY" "$TEST_PASS" "$TEST_PASS" "human" "warrior" \
    "kill training dummy" "quit"

expect "$T2D2" "strike reports a worded damage verb"        'You .+ the Training Dummy!'
expect "$T2D2" "strike reports the target condition in words" \
    'The Training Dummy (is in|has|looks) '

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

# ── phase 2g: buy and READ a hand-drawn area map (issue #529) ────────────────
# The MAP command was retired: cartography is now map ITEMS bought from shops or
# found as loot, showing an area's hand-drawn paths and NEVER the player's
# position. New players spawn in the training-yard; the armory (north) stocks the
# starter town map. We walk north, buy it, and READ it, then assert the area art
# renders with no player-position marker present anywhere in the transcript.
log "Phase 2g: buy and READ a hand-drawn area map"
T2G="$OUT_DIR/phase2g-map.txt"
run_session "$T2G" "$TEST_USER" "$TEST_PASS" \
    "north" "buy map of greystone" "read town-map" "south" "quit"

expect "$T2G" "starter map can be purchased"        'You buy a Map of Greystone Town'
expect "$T2G" "READ renders the area title"         'Greystone Town'
expect "$T2G" "READ renders hand-drawn paths"       'Training Yard'
# Map items show fixed cartography, never the reader's position (issue #529).
if grep -q '@' "$T2G"; then
    fail "map art must not contain a player-position marker"
else
    pass "map art contains no position marker"
fi

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

# ── phase 2i: Auction House (AUCTION SELL/LIST) ──────────────────────────────
# The Auction House (issue #357) sits in the courtyard (east of the training-yard,
# alongside the bank). A new warrior spawns in the training-yard carrying nothing,
# so we pick up the iron sword lying there, walk east to the courtyard, list it,
# then LIST the auctions and assert the listing appears. Buying to an offline
# seller and expiry-return are covered by AuctionService / AuctionExpiryTicker unit
# tests. The cleanup trap restores/removes data/auctions/listings.json.
log "Phase 2i: AUCTION SELL then LIST at the Auction House"
T2I="$OUT_DIR/phase2i-auction.txt"
run_session "$T2I" "$TEST_USER" "$TEST_PASS" \
    "get iron sword" "east" "auction sell iron sword 100" "auction list" "quit"

expect "$T2I" "AUCTION SELL confirms the listing"   'You list Iron Sword for 100 gold'
expect "$T2I" "AUCTION LIST shows the listed item"  'Iron Sword'
expect "$T2I" "AUCTION LIST shows the asking price" '100'

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

# ── phase 3b: linkdead reconnect (issue #343) ────────────────────────────────
# A dropped connection keeps the player "linkdead" in the world for a grace period
# (jmud.linkdead.timeout_ticks, default 30) so a reconnecting client reattaches to the
# LIVE session instead of reloading from disk. We create a character, reach the in-world
# prompt, then kill nc abruptly (never QUIT) to simulate a dropped link; the server marks
# the session linkdead. Well within the timeout we reconnect as the same user and confirm
# the session resumes (prompt + SCORE) and that the server reattached rather than logging in.
#
# Runs AFTER the phase-3 health scan on purpose: an abrupt client drop legitimately logs an
# "Error receiving" line (a pre-existing disconnect wart, see issues #180/#182), which the
# strict phase-3 error scan would otherwise flag.
log "Phase 3b: linkdead reconnect"
T3B_A="$OUT_DIR/phase3b-linkdead-drop.txt"
T3B_B="$OUT_DIR/phase3b-reconnect.txt"
LINKDEAD_LOG_OFFSET=$(wc -c < "$SERVER_LOG")
{
    sleep 1.5
    printf '%s\r\n' "$TEST_LINK"
    sleep 1.5
    printf '%s\r\n' "$TEST_PASS"
    sleep 1.5
    printf '%s\r\n' "$TEST_PASS"
    sleep 1.5
    printf '%s\r\n' "human"
    sleep 1.5
    printf '%s\r\n' "warrior"
    sleep 30   # stay in-world, holding the link until we pull the plug
} | timeout 60 nc 127.0.0.1 "$TELNET_PORT" | tr -d '\r' > "$T3B_A" &
LINK_NC_PID=$!
sleep 12                              # let creation finish and the character settle in-world
kill -9 "$LINK_NC_PID" 2>/dev/null    # abrupt drop -> server marks the session linkdead
wait "$LINK_NC_PID" 2>/dev/null
sleep 3                               # a few ticks pass while linkdead, well under the timeout

run_session "$T3B_B" "$TEST_LINK" "$TEST_PASS" "score" "quit"

expect "$T3B_B" "reconnect reaches the in-world prompt" '\[[0-9]+/[0-9]+hp .*\]'
expect "$T3B_B" "reconnect resumes SCORE"               '--- Score ---'

LINKDEAD_LOG="$OUT_DIR/server-linkdead-window.log"
tail -c "+$((LINKDEAD_LOG_OFFSET + 1))" "$SERVER_LOG" > "$LINKDEAD_LOG"
expect "$LINKDEAD_LOG" "server marked the dropped session linkdead" 'went linkdead'
expect "$LINKDEAD_LOG" "server reattached the reconnecting client"  'reattached to linkdead session'

# ── phase 3c: mid-creation world isolation (issues #253/#512) ────────────────
# A player still answering the race/class prompts must not be in the world yet:
# no room occupancy (invisible to LOOK), no WHO entry, and no game broadcasts or
# prompts interrupting the creation flow. World entry happens only when creation
# completes (SocketClient.finishCharacterCreation).
log "Phase 3c: mid-creation player is isolated from the world"
T3C_A="$OUT_DIR/phase3c-observer.txt"
T3C_B="$OUT_DIR/phase3c-creation.txt"

# B: new user who parks at the race prompt for ~20s, then completes creation.
{
    sleep 1.5
    printf '%s\r\n' "$TEST_CREB"; sleep 1.5
    printf '%s\r\n' "$TEST_PASS"; sleep 1.5
    printf '%s\r\n' "$TEST_PASS"
    sleep 20                      # parked at the race prompt while A observes
    printf '%s\r\n' "human";   sleep 1.5
    printf '%s\r\n' "warrior"; sleep 3
    printf '%s\r\n' "quit";    sleep 1
} | timeout 60 nc 127.0.0.1 "$TELNET_PORT" | tr -d '\r' > "$T3C_B" &
CREB_PID=$!

sleep 8   # B is authenticated and parked at the race prompt

# A: existing user; observes the world and talks while B is mid-creation.
# A finishes well inside B's 20s parked window, so everything A sees (and says)
# happens while B is still choosing a race.
run_session "$T3C_A" "$TEST_USER" "$TEST_PASS" \
    "look" "who" "gossip smoke-creation-leak-check" "quit"

wait "$CREB_PID" 2>/dev/null

if grep -q "$TEST_CREB" "$T3C_A"; then
    fail "mid-creation player is visible to LOOK/WHO"
else
    pass "mid-creation player hidden from LOOK and WHO"
fi

# Everything B received before "Welcome to the realm!" is creation-flow output:
# it must contain no game prompt and no broadcast/mob text.
PRE_CREATION=$(sed '/Welcome to the realm!/,$d' "$T3C_B")
if printf '%s' "$PRE_CREATION" | grep -qE '\[[0-9]+/[0-9]+hp |wanders|smoke-creation-leak-check'; then
    fail "game output leaked into the character-creation flow"
else
    pass "no game output during character creation"
fi
expect "$T3C_B" "creation completes and enters the world" 'Welcome to the realm!'
expect "$T3C_B" "post-creation prompt rendered"           '\[[0-9]+/[0-9]+hp .*\]'

# ── phase 4: graceful shutdown on SIGTERM ────────────────────────────────────
# SIGTERM (the default kill signal) must trigger ShutdownCoordinator's orderly
# sequence: stop accepting connections, notify clients, stop the tick scheduler,
# save online players, flush audit, clear the tick registry — then the JVM exits
# on its own.
#
# This cannot be tested against the main server instance above: that instance
# runs via `./gradlew run`, which executes inside the Gradle Daemon's forked
# worker process. That worker does not install the JVM's normal default
# SIGTERM disposition (shutdown hooks run, then exit) the way a plain `java`
# process does, so a directly-signalled Gradle worker exits immediately
# without ever invoking registered shutdown hooks — the hook code silently
# never runs. Real deployments (Docker/Kubernetes/systemd) invoke jmud as a
# plain `java` process, so to test the real behavior we build the install
# distribution and launch that binary directly, bypassing Gradle entirely for
# this one process.
log "Phase 4: graceful shutdown on SIGTERM"
SHUTDOWN_LOG="$OUT_DIR/server-shutdown.log"
JMUD_BIN="build/install/jmud/bin/jmud"

log "+ ./gradlew installDist --console=plain -q"
if ! ./gradlew installDist --console=plain -q > "$OUT_DIR/installDist.log" 2>&1; then
    fail "./gradlew installDist failed — see $OUT_DIR/installDist.log"
elif [ ! -x "$JMUD_BIN" ]; then
    fail "installDist did not produce an executable $JMUD_BIN"
elif nc -z 127.0.0.1 "$SHUTDOWN_TELNET_PORT" 2>/dev/null; then
    fail "port $SHUTDOWN_TELNET_PORT already in use — cannot run shutdown phase"
else
    "$JMUD_BIN" --telnet-port "$SHUTDOWN_TELNET_PORT" --ssh-port "$SHUTDOWN_SSH_PORT" \
        > "$SHUTDOWN_LOG" 2>&1 &

    waited=0
    until nc -z 127.0.0.1 "$SHUTDOWN_TELNET_PORT" 2>/dev/null; do
        sleep 1
        waited=$((waited + 1))
        if [ "$waited" -ge "$STARTUP_TIMEOUT" ]; then
            fail "shutdown-test server did not open port $SHUTDOWN_TELNET_PORT within ${STARTUP_TIMEOUT}s"
            break
        fi
    done

    SHUTDOWN_PID="$(shutdown_server_pid || true)"
    if [ -z "${SHUTDOWN_PID:-}" ]; then
        fail "shutdown-test server not running after startup"
    else
        # Hold a live session open across the shutdown so an online player is
        # saved. The server closes the connection when it shuts down, so nc
        # exits on its own.
        T4="$OUT_DIR/phase4-shutdown.txt"
        {
            sleep 1.5
            printf '%s\r\n' "$TEST_USER"
            sleep 1.5
            printf '%s\r\n' "$TEST_PASS"
            sleep 30   # idle, holding the connection until the server shuts down
        } | timeout 45 nc 127.0.0.1 "$SHUTDOWN_TELNET_PORT" | tr -d '\r' > "$T4" &
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

        expect "$SHUTDOWN_LOG" "shutdown sequence started"   'Shutdown sequence starting'
        expect "$SHUTDOWN_LOG" "shutdown sequence completed" 'Shutdown sequence complete'
    fi
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
