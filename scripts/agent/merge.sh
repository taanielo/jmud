#!/usr/bin/env bash
# Orchestrator step: gate on GitHub CI, then squash-merge the current branch's
# PR and return to a clean main. Replaces the pr-merger agent.
#
# Gates (never bypassed — no admin/force merge, ever):
#   - PR must be open and mergeable (conflicts => FAIL reason=conflicts, park it)
#   - GitHub CI checks must be green (`gh pr checks --watch`); local green from
#     verify.sh is only a pre-check. Falls back to local-green ONLY if GitHub
#     reports no checks configured after a re-check.
#
# Usage:   scripts/agent/merge.sh [<issue-number>]
# Prints:  OK merged=true pr=<number>
set -uo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
cd "$REPO_ROOT"

ISSUE="${1-}"
CI_WAIT_SECS=900   # give up on CI after ~15 min

PR_JSON="$(gh pr view --json number,url,state,mergeable 2>>"$STEP_LOG")" \
    || fail no-pr "no PR found for the current branch"
PR_NUM="$(jq -r .number <<<"$PR_JSON")"
PR_STATE="$(jq -r .state <<<"$PR_JSON")"
MERGEABLE="$(jq -r .mergeable <<<"$PR_JSON")"

result_json() {
    jq -n --argjson p "$PR_NUM" --argjson i "${ISSUE:-null}" \
        '{merged: true, pr_number: $p, issue: $i}'
}

back_to_main() {
    run git checkout main                || fail checkout-main "could not checkout main after merge"
    run git pull --ff-only origin main   || fail pull "ff-only pull of main failed after merge"
}

# Idempotent: a crash after merging must not re-fail the cycle.
if [ "$PR_STATE" = "MERGED" ]; then
    back_to_main
    ok "$(result_json)" "merged=true pr=$PR_NUM (was already merged)"
fi
[ "$PR_STATE" = "OPEN" ]         || fail pr-state "PR #$PR_NUM is $PR_STATE, expected OPEN"
[ "$MERGEABLE" = "CONFLICTING" ] && fail conflicts "PR #$PR_NUM has merge conflicts — never force-merged; park it"

# CI gate. gh exit codes: 0 = all green; non-zero = failed/pending/no checks.
CHECKS_FILE="$STATE_DIR/ci-checks.txt"
ci_watch() {
    timeout "$CI_WAIT_SECS" gh pr checks "$PR_NUM" --watch --fail-fast \
        >"$CHECKS_FILE" 2>&1
}
ci_failed_names() { grep -iE '\bfail' "$CHECKS_FILE" | scrub | head -5; }

if ! ci_watch; then
    RC=$?
    cat "$CHECKS_FILE" >>"$STEP_LOG"
    [ "$RC" -eq 124 ] && fail ci-timeout "CI still running after ${CI_WAIT_SECS}s — not merging"
    if grep -qi 'no checks reported' "$CHECKS_FILE"; then
        # Workflow may simply not have triggered yet — re-check once.
        sleep 45
        if ! ci_watch; then
            RC=$?
            cat "$CHECKS_FILE" >>"$STEP_LOG"
            [ "$RC" -eq 124 ] && fail ci-timeout "CI still running after ${CI_WAIT_SECS}s — not merging"
            if grep -qi 'no checks reported' "$CHECKS_FILE"; then
                echo "WARN no CI checks reported after re-check — falling back to the local-green gate"
            else
                fail ci "CI checks failed: $(ci_failed_names)"
            fi
        fi
    else
        fail ci "CI checks failed: $(ci_failed_names)"
    fi
fi

run gh pr merge "$PR_NUM" --squash --delete-branch || fail merge "gh pr merge --squash failed"
back_to_main

ok "$(result_json)" "merged=true pr=$PR_NUM"
