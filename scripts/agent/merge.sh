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

# The argument is the ISSUE number, never the PR number (the PR is found from
# the current branch). A PR number here corrupts cycle-log.jsonl — it has
# happened (issue #329 was logged as 330). GitHub numbers issues and PRs in
# one sequence, so a numeric check can't catch it; the resolved URL can.
if [ -n "$ISSUE" ]; then
    [[ "$ISSUE" =~ ^[0-9]+$ ]] || fail usage "issue number must be numeric, got '$ISSUE'"
    ISSUE_URL="$(gh issue view "$ISSUE" --json url -q .url 2>>"$STEP_LOG" || true)"
    case "$ISSUE_URL" in
        */issues/*) : ;;
        */pull/*)   fail usage "#$ISSUE is a pull request, not an issue — pass the issue number (the PR is derived from the current branch)" ;;
        *)          echo "WARN could not verify #$ISSUE is an issue (gh lookup failed) — proceeding, but check cycle-log.jsonl" ;;
    esac
fi

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

# Record the cycle in cycle-log.jsonl with GitHub's real merge timestamp —
# history entries are written here, deterministically, never by the model
# (which has been caught inventing round-number timestamps). Idempotent:
# skips if this PR is already logged.
append_cycle_log() {
    local cycle_log="$STATE_DIR/cycle-log.jsonl"
    grep -q "\"pr\":$PR_NUM[,}]" "$cycle_log" 2>/dev/null && return 0
    local merged_at started_at verify_runs
    merged_at="$(gh pr view "$PR_NUM" --json mergedAt -q '.mergedAt // empty' 2>>"$STEP_LOG")"
    [ -n "$merged_at" ] || merged_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    # started_at = this session's guard time (a resumed cycle logs its last
    # session's start, not the very first attempt — good enough for cadence
    # metrics). verify_runs counts verify.sh invocations this cycle: 1 = clean
    # first pass, >1 = build retries were needed.
    started_at="$(jq -r '.started_at // empty' "$STATE_DIR/LOCK" 2>/dev/null || true)"
    verify_runs="$(cat "$STATE_DIR/verify-runs" 2>/dev/null || true)"
    [[ "$verify_runs" =~ ^[0-9]+$ ]] || verify_runs=null
    jq -cn --argjson p "$PR_NUM" --argjson i "${ISSUE:-null}" --arg m "$merged_at" \
        --arg s "$started_at" --argjson v "$verify_runs" \
        '{issue: $i, pr: $p, merged_at: $m,
          started_at: (if $s == "" then null else $s end), verify_runs: $v}' >>"$cycle_log"
}

# GitHub auto-closes the linked issue only if the PR body carried a closing
# keyword; that has silently failed before (issue #339 stayed open after its
# PR merged). Verify and close explicitly — idempotent, skipped when no issue
# number was passed.
close_linked_issue() {
    [ -n "$ISSUE" ] || return 0
    sleep 3   # give GitHub's closes-keyword automation a moment to run first
    local ist
    ist="$(gh issue view "$ISSUE" --json state -q .state 2>>"$STEP_LOG" || true)"
    if [ "$ist" = "OPEN" ]; then
        if run gh issue close "$ISSUE" --comment "Closed by PR #$PR_NUM (merge.sh: auto-close did not fire)"; then
            echo "WARN issue #$ISSUE was not auto-closed by the merge — closed it explicitly"
        else
            echo "WARN issue #$ISSUE is still open and the explicit close failed — close it manually"
        fi
    fi
}

# Idempotent: a crash after merging must not re-fail the cycle.
if [ "$PR_STATE" = "MERGED" ]; then
    append_cycle_log
    close_linked_issue
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
append_cycle_log
close_linked_issue
back_to_main

ok "$(result_json)" "merged=true pr=$PR_NUM"
