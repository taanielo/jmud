#!/usr/bin/env bash
# Orchestrator step: run the full local quality gate. Replaces the
# build-verifier agent. Runs `./gradlew check` (compile + tests + static
# analysis + ArchUnit + coverage as wired) and optionally the end-to-end smoke
# test. On failure, writes an actionable, scrubbed excerpt (~40 lines) to
# .orchestrator/build-error.txt for code-writer's retry.
#
# This is the local gate only; CI (gh pr checks) is enforced later by merge.sh.
#
# Usage:   scripts/agent/verify.sh [--expect-branch <name>] [--smoke]
# Prints:  OK check=pass smoke=<pass|skipped>
set -uo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
cd "$REPO_ROOT"

EXPECT=""
SMOKE=0
while [ $# -gt 0 ]; do
    case "$1" in
        --expect-branch) EXPECT="${2-}"; shift 2 ;;
        --smoke)         SMOKE=1; shift ;;
        *) fail usage "unknown argument: $1" ;;
    esac
done

ERR_FILE="$STATE_DIR/build-error.txt"
BRANCH="$(git branch --show-current)"

# Guard: never "verify" the wrong state and hand back a meaningless PASS.
if [ -n "$EXPECT" ] && [ "$BRANCH" != "$EXPECT" ]; then
    fail branch-mismatch "on '$BRANCH' but orchestrator expected '$EXPECT'"
fi
if [ "$BRANCH" = "main" ] && [ -z "$(git status --porcelain)" ]; then
    fail wrong-branch "on clean main — there is nothing to verify"
fi

# fail_with_excerpt <reason> <output-json> — like fail(), but the result
# carries the error excerpt so code-writer's retry gets actionable feedback.
fail_with_excerpt() {
    write_result failure "$2"
    echo "FAIL reason=$1 details=.orchestrator/build-error.txt (full log: .orchestrator/$STEP_NAME.log)"
    exit 1
}

# 1. Full gradle quality gate (always the wrapper, never bare gradle).
log "+ ./gradlew check --console=plain"
if ! ./gradlew check --console=plain >>"$STEP_LOG" 2>&1; then
    {
        grep -E '(^e: |error:|FAILED|Caused by:|Assertion|Violation|expected:)' "$STEP_LOG" | head -35
        echo '--- log tail ---'
        tail -n 15 "$STEP_LOG"
    } | scrub >"$ERR_FILE"
    fail_with_excerpt check \
        "$(jq -n --rawfile e "$ERR_FILE" '{build: "fail", error_summary: $e}')"
fi

# 2. Optional end-to-end smoke test (player-visible changes).
SMOKE_RESULT=skipped
if [ "$SMOKE" -eq 1 ]; then
    log "+ scripts/smoke-test.sh"
    if ! scripts/smoke-test.sh >>"$STEP_LOG" 2>&1; then
        tail -n 40 "$STEP_LOG" | scrub >"$ERR_FILE"
        fail_with_excerpt smoke \
            "$(jq -n --rawfile e "$ERR_FILE" '{build: "pass", smoke: "fail", error_summary: $e}')"
    fi
    SMOKE_RESULT=pass
fi

rm -f "$ERR_FILE"
ok "$(jq -n --arg s "$SMOKE_RESULT" '{build: "pass", smoke: $s}')" \
   "check=pass smoke=$SMOKE_RESULT"
