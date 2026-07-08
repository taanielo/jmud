#!/usr/bin/env bash
# Orchestrator step: the GUARD — PAUSE kill switch, run-lease LOCK, and
# default-state creation, done deterministically. A skipped cycle (paused or
# lock held) exits in milliseconds instead of burning a full model session on
# hand-computed lock-age arithmetic.
#
# Deviation from the lib.sh contract: on FAIL reason=paused / reason=lock-held
# this script does NOT write last-result.json — those outcomes mean "do
# nothing", and the previous worker's result may still be needed by a resumed
# cycle. The step log is still written.
#
# Usage:   scripts/agent/guard.sh
# Prints:  OK lock=acquired lock_age=<secs-of-replaced-stale-lock|none>
#          FAIL reason=paused | reason=lock-held
set -uo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
cd "$REPO_ROOT"

# 35 min: longer than one 30-min cron interval, so a live run is never
# preempted, but an orphaned lock costs at most one skipped firing (the old
# 60-min threshold made every orphan block two).
LOCK_STALE_SECS=2100

if [ -e "$STATE_DIR/PAUSE" ]; then
    log "PAUSE file present — refusing to run"
    echo "FAIL reason=paused detail=remove .orchestrator/PAUSE to resume"
    exit 1
fi

REPLACED_AGE="none"
if [ -e "$STATE_DIR/LOCK" ]; then
    STARTED_AT="$(jq -r '.started_at // empty' "$STATE_DIR/LOCK" 2>/dev/null || true)"
    STARTED_EPOCH="$(date -ud "$STARTED_AT" +%s 2>/dev/null || echo 0)"
    AGE=$(( $(date -u +%s) - STARTED_EPOCH ))
    if [ -n "$STARTED_AT" ] && [ "$AGE" -ge 0 ] && [ "$AGE" -lt "$LOCK_STALE_SECS" ]; then
        log "LOCK held: age ${AGE}s < ${LOCK_STALE_SECS}s (started_at $STARTED_AT)"
        echo "FAIL reason=lock-held detail=lock is ${AGE}s old, stale at ${LOCK_STALE_SECS}s"
        exit 1
    fi
    # Unreadable/corrupt lock parses to a huge AGE and is treated as stale.
    log "replacing stale LOCK: age ${AGE}s (started_at '${STARTED_AT:-unparseable}')"
    echo "WARN taking over a stale LOCK (age ${AGE}s) — the prior run likely died mid-cycle"
    REPLACED_AGE="$AGE"
fi

jq -n --arg pid "guard-$$" '{pid: $pid, started_at: (now | todate)}' >"$STATE_DIR/LOCK" \
    || { echo "FAIL reason=lock-write detail=could not write $STATE_DIR/LOCK"; exit 1; }

STATE_FILE="$STATE_DIR/orchestrator-state.json"
if [ ! -s "$STATE_FILE" ]; then
    jq -n '{current_issue: null, todo_line: null, stage: "FIND_ISSUE", build_retries: 0,
            cycles_since_last_optimization: 0, blocked_issues: [], parked_pr: null,
            waiting_for_session_reset: false, last_updated: (now | todate)}' >"$STATE_FILE"
    log "created default orchestrator-state.json"
fi

log "lock acquired"
echo "OK lock=acquired replaced_stale_lock_age=$REPLACED_AGE"
exit 0
