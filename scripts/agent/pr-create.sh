#!/usr/bin/env bash
# Orchestrator step: stage the whitelisted paths, commit, push, and open (or
# reuse) the PR for the current feature branch. Replaces the pr-creator agent.
# Idempotent: safe to re-run after a crash (skips empty commits, reuses an
# already-pushed branch or open PR).
#
# Staging is by whitelist, never `git add -A`, so stray/runtime files can't be
# committed. Anything left unstaged is reported as a WARN, not silently added.
#
# Usage:   scripts/agent/pr-create.sh <issue-number> "<conventional commit title>"
#          e.g. scripts/agent/pr-create.sh 260 "feat(combat): add parry skill"
# Prints:  OK pr=<number> url=<url>
set -uo pipefail
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
cd "$REPO_ROOT"

[ $# -eq 2 ] || fail usage "expected: pr-create.sh <issue-number> \"<commit title>\""
ISSUE="$1"
TITLE="$2"
[[ "$ISSUE" =~ ^[0-9]+$ ]] || fail usage "issue number must be numeric, got '$ISSUE'"
[[ "$TITLE" =~ ^(feat|fix|refactor|docs|test|chore|perf|build|ci)(\(.+\))?!?:\  ]] \
    || fail usage "commit title must be Conventional Commits format, got '$TITLE'"

BRANCH="$(git branch --show-current)"
[ "$BRANCH" = "main" ] && fail on-main "refusing to commit and open a PR from main"
[ -n "$BRANCH" ] || fail detached "not on a branch (detached HEAD)"

# 1. Stage by whitelist. .orchestrator/ is gitignored, but reset it
#    defensively anyway — runtime state must never be committed.
for p in src data docs scripts build.gradle settings.gradle gradle .claude/agents .claude/commands; do
    [ -e "$p" ] && run git add -- "$p"
done
git reset -q -- .orchestrator 2>/dev/null || true

LEFTOVER="$(git status --porcelain | grep -Ev '^[MADRC][ MD] ' || true)"
if [ -n "$LEFTOVER" ]; then
    printf 'not staged (outside whitelist):\n%s\n' "$LEFTOVER" >>"$STEP_LOG"
    echo "WARN $(wc -l <<<"$LEFTOVER") path(s) outside the staging whitelist left uncommitted — see the step log"
fi

# 2. Commit — skip cleanly if there is nothing staged (resumed cycle).
if git diff --cached --quiet; then
    echo "WARN nothing newly staged (resumed cycle?) — continuing to push/PR"
else
    run git commit -m "$TITLE" -m "Closes #$ISSUE" \
        -m "Co-Authored-By: Claude <noreply@anthropic.com>" \
        || fail commit "git commit failed"
fi

# 3. Push (safe to re-run).
run git push -u origin "$BRANCH" || fail push "git push -u origin $BRANCH failed"

# 4. Open the PR, or reuse an open one.
PR_JSON="$(gh pr view --json number,url,state 2>>"$STEP_LOG" || true)"
if [ -z "$PR_JSON" ] || [ "$(jq -r '.state // empty' <<<"$PR_JSON")" != "OPEN" ]; then
    run gh pr create --base main --head "$BRANCH" --title "$TITLE" --body "Closes #$ISSUE

🤖 Generated with [Claude Code](https://claude.com/claude-code)" \
        || fail pr-create "gh pr create failed"
    PR_JSON="$(gh pr view --json number,url,state 2>>"$STEP_LOG")" \
        || fail pr-view "PR created but could not read it back"
fi

PR_NUM="$(jq -r .number <<<"$PR_JSON")"
PR_URL="$(jq -r .url <<<"$PR_JSON")"
ok "$(jq -n --argjson p "$PR_NUM" --arg u "$PR_URL" --argjson i "$ISSUE" \
        '{pr_number: $p, pr_url: $u, issue: $i}')" \
   "pr=$PR_NUM url=$PR_URL"
