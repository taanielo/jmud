#!/usr/bin/env bash
# Prints the next eligible issue for the autonomous loop, deterministically:
# the LOWEST-numbered open issue with the given label (default: architecture)
# whose dependencies are all closed.
#
# Dependencies are declared in the issue body as "Depends on: #N[, #M]" or
# "Do after: #N" lines. Issue numbers encode priority (created in phase order),
# and `gh issue list` returns newest-first by default — this script exists so
# no agent ever has to remember to re-sort.
#
# Output (stdout):  "<number>\t<title>"   exit 0
# No eligible issue:  nothing             exit 1
#
# Usage:
#   scripts/next-issue.sh                 # next architecture issue
#   scripts/next-issue.sh enhancement     # next issue with another label
#   NEXT_ISSUE_EXCLUDE="191 193" scripts/next-issue.sh   # skip blocked issues

set -u
LABEL="${1:-architecture}"
EXCLUDE=" ${NEXT_ISSUE_EXCLUDE:-} "

cd "$(dirname "$0")/.." || exit 1

ISSUES_JSON="$(gh issue list --label "$LABEL" --state open --json number,title,body --limit 100)" || exit 1

mapfile -t NUMBERS < <(jq -r 'sort_by(.number) | .[].number' <<<"$ISSUES_JSON")
[ "${#NUMBERS[@]}" -eq 0 ] && exit 1

for NUM in "${NUMBERS[@]}"; do
    case "$EXCLUDE" in *" $NUM "*) continue ;; esac
    BODY="$(jq -r --argjson n "$NUM" '.[] | select(.number == $n) | .body' <<<"$ISSUES_JSON")"
    # Collect #N references from "Depends on:" / "Do after:" lines only.
    DEPS="$(grep -oE '(Depends on|Do after):[^\n]*' <<<"$BODY" | grep -oE '#[0-9]+' | tr -d '#' | sort -u)"
    ELIGIBLE=1
    for DEP in $DEPS; do
        STATE="$(gh issue view "$DEP" --json state --jq .state 2>/dev/null)"
        if [ "$STATE" != "CLOSED" ]; then
            ELIGIBLE=0
            break
        fi
    done
    if [ "$ELIGIBLE" -eq 1 ]; then
        jq -r --argjson n "$NUM" '.[] | select(.number == $n) | "\(.number)\t\(.title)"' <<<"$ISSUES_JSON"
        exit 0
    fi
done
exit 1
