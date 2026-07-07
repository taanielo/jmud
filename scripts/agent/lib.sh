# Shared helpers for the orchestrator step scripts (scripts/agent/*.sh).
# Sourced, not executed.
#
# Contract every step script follows (so the orchestrator can stay cheap):
#   - stdout: optional "WARN ..." lines, then exactly ONE "OK ..." or
#     "FAIL reason=<slug> ..." line
#   - detail: appended to .claude/agents/state/<step>.log — read it only on FAIL
#   - result: .claude/agents/state/last-result.json (same schema the worker
#     agents used: { status, output, timestamp })
#   - exit:   0 = OK, non-zero = FAIL
#
# Never echo environment variables or paste anything token/key-shaped into
# results (AGENTS.md: never log security keys/tokens).

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="$REPO_ROOT/.claude/agents/state"
mkdir -p "$STATE_DIR"
STEP_NAME="$(basename "${0%.sh}")"
STEP_LOG="$STATE_DIR/$STEP_NAME.log"

# Fresh log per run — the log is a per-step diagnostic, not an archive.
: >"$STEP_LOG"

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >>"$STEP_LOG"; }

# run <cmd...> — execute with all output captured in the step log; returns the
# command's exit code so callers can `run ... || fail ...`.
run() {
    log "+ $*"
    "$@" >>"$STEP_LOG" 2>&1
}

# Drop lines that look like they carry credentials before they can reach a
# result file, issue body, or PR comment.
scrub() { grep -viE '(api[-_]?key|token|secret|password)[[:space:]]*[=:]' || true; }

# write_result <success|failure> <output-json>
write_result() {
    jq -n --arg status "$1" --argjson output "$2" \
        '{status: $status, output: $output, timestamp: (now | todate)}' \
        >"$STATE_DIR/last-result.json"
}

# ok <output-json> <summary words...> — write success result, print OK line, exit 0
ok() {
    local output="$1"; shift
    write_result success "$output"
    echo "OK $*"
    exit 0
}

# fail <reason-slug> <detail...> — write failure result, print FAIL line, exit 1
fail() {
    local reason="$1"; shift
    log "FAIL $reason: $*"
    write_result failure "$(jq -n --arg r "$reason" --arg d "$*" '{reason: $r, detail: $d}')"
    echo "FAIL reason=$reason detail=$* (log: .claude/agents/state/$STEP_NAME.log)"
    exit 1
}
