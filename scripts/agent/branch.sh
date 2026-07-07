#!/usr/bin/env bash
# Orchestrator step: put the repo on a clean feature branch built from latest
# main. Replaces the branch-manager agent. Never destroys unrecoverable work:
# dirty trees are stashed (never dropped), branches with unpushed commits are
# resumed, and a diverged main fails loudly (--ff-only) instead of merging.
#
# Usage:   scripts/agent/branch.sh <issue-number> <issue-title...>
# Prints:  OK branch=<name> stashed=<0|1> autosave_stashes=<n> resumed=<0|1>
set -uo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
cd "$REPO_ROOT"

[ $# -ge 2 ] || fail usage "expected: branch.sh <issue-number> <issue-title...>"
ISSUE="$1"; shift
[[ "$ISSUE" =~ ^[0-9]+$ ]] || fail usage "issue number must be numeric, got '$ISSUE'"
TITLE="$*"

# Branch name: feat/issue-<N>-<slug>, slug = kebab-cased title, first 6 words.
SLUG="$(printf '%s' "$TITLE" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' \
    | sed -e 's/^-//' -e 's/-$//' | cut -d- -f1-6)"
[ -n "$SLUG" ] || fail usage "could not derive a slug from title '$TITLE'"
BRANCH="feat/issue-$ISSUE-$SLUG"

# 1. Protect stray changes — unconditional stash so nothing is ever lost.
#    Reviewing/dropping stashes stays a human decision; scripts never do it.
STASHED=0
if [ -n "$(git status --porcelain)" ]; then
    run git stash push -u -m "branch-manager autosave $(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        || fail stash "could not stash dirty working tree"
    STASHED=1
fi

# Surface autosave accumulation every run so it gets noticed, not buried.
AUTOSAVES="$(git stash list | grep -c 'branch-manager autosave' || true)"
if [ "$AUTOSAVES" -ge 2 ]; then
    git stash list --format='%gd %ci %s' | grep 'branch-manager autosave' >>"$STEP_LOG"
    echo "WARN $AUTOSAVES 'branch-manager autosave' stashes have piled up — a human should review them (git stash list)"
fi

# 2. Sync main.
run git fetch origin                  || fail fetch "git fetch origin failed"
run git checkout main                 || fail checkout-main "could not checkout main"
run git pull --ff-only origin main    || fail pull "main diverged from origin: ff-only pull refused"

# 3. Create the branch, or reuse a leftover from a crashed cycle.
RESUMED=0
if git rev-parse --verify --quiet "$BRANCH" >/dev/null; then
    run git checkout "$BRANCH" || fail checkout "could not checkout existing $BRANCH"
    AHEAD="$(git rev-list --count origin/main..HEAD)"
    if [ "$AHEAD" -eq 0 ]; then
        # Stale leftover with nothing on it — rebuild it on fresh main.
        run git reset --hard origin/main || fail reset "could not reset stale $BRANCH onto origin/main"
    else
        # Unpushed commits: keep them and resume. Never reset --hard here.
        RESUMED=1
        echo "WARN reusing $BRANCH with $AHEAD unpushed commit(s) — resuming a prior cycle"
    fi
else
    run git checkout -b "$BRANCH" origin/main || fail branch "could not create $BRANCH"
fi

ok "$(jq -n --arg b "$BRANCH" --argjson i "$ISSUE" '{branch: $b, issue: $i}')" \
   "branch=$BRANCH stashed=$STASHED autosave_stashes=$AUTOSAVES resumed=$RESUMED"
