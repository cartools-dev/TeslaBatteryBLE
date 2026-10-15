#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Merge a Dependabot patch PR once everything is green and nothing changed
# visually. There is deliberately no branch protection, so this script waits
# for the checks itself instead of relying on auto-merge, then requires the
# PR-paparazzi run for the same head to hold zero after-images. Fails safe
# (exit 1, a human looks) on any red check, on visual drift, on timeout, or
# when an expected check or artifact never appears.
#
# Usage: dependabot-automerge.sh <pr-number> <head-sha>
set -euo pipefail

PR="${1:?usage: dependabot-automerge.sh <pr-number> <head-sha>}"
HEAD_SHA="${2:?usage: dependabot-automerge.sh <pr-number> <head-sha>}"
RUN_ID="${GITHUB_RUN_ID:?GITHUB_RUN_ID is not set}"

# Minutes to wait for checks to appear, then to complete.
APPEAR_TIMEOUT_MIN=10
COMPLETE_TIMEOUT_MIN=90
POLL_SECONDS=60

# Check runs on the head commit that do not belong to this workflow run:
# waiting on our own run would deadlock, so it never gates the merge.
foreign_runs() {
    gh api "repos/$GITHUB_REPOSITORY/commits/$HEAD_SHA/check-runs?per_page=100" \
        --jq '[.check_runs[] | select(.html_url | contains("/runs/'"$RUN_ID"'/") | not) | {status: .status, conclusion: .conclusion}]'
}

deadline=$((SECONDS + APPEAR_TIMEOUT_MIN * 60))
while [ "$(foreign_runs | jq 'length')" -eq 0 ]; do
    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "::error::no checks appeared on PR #$PR within $APPEAR_TIMEOUT_MIN minutes; leaving it for a human"
        exit 1
    fi
    sleep "$POLL_SECONDS"
done

deadline=$((SECONDS + COMPLETE_TIMEOUT_MIN * 60))
while [ "$(foreign_runs | jq '[.[] | select(.status != "completed")] | length')" -gt 0 ]; do
    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "::error::checks on PR #$PR did not complete within $COMPLETE_TIMEOUT_MIN minutes; leaving it for a human"
        exit 1
    fi
    sleep "$POLL_SECONDS"
done

bad="$(foreign_runs | jq -r '[.[] | select(.conclusion != "success" and .conclusion != "skipped" and .conclusion != "neutral") | .conclusion] | join(", ")')"
if [ -n "$bad" ]; then
    echo "::error::PR #$PR has non-green checks ($bad); leaving it for a human"
    exit 1
fi

# No visual drift: the PR-paparazzi run runs on every pull request, so its
# artifact must exist for this head with zero after-images (any redraw posts
# a diff for review instead of merging).
run_id="$(gh api "repos/$GITHUB_REPOSITORY/actions/workflows/pr-paparazzi.yml/runs?head_sha=$HEAD_SHA&event=pull_request&per_page=1" \
    --jq '.workflow_runs[0].id // empty')"
if [ -z "$run_id" ]; then
    echo "::error::no PR-paparazzi run for $HEAD_SHA; leaving PR #$PR for a human"
    exit 1
fi
artifact_id="$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/$run_id/artifacts" \
    --jq '[.artifacts[] | select(.name == "pr-paparazzi" and (.expired | not))] | sort_by(.created_at) | last | .id // empty')"
if [ -z "$artifact_id" ]; then
    echo "::error::no pr-paparazzi artifact for $HEAD_SHA; leaving PR #$PR for a human"
    exit 1
fi
tmp="$(mktemp -d)"
cleanup() { rm -rf "$tmp"; }
trap cleanup EXIT
gh api "repos/$GITHUB_REPOSITORY/actions/artifacts/$artifact_id/zip" > "$tmp/shots.zip"
after_count="$(unzip -l "$tmp/shots.zip" | grep -c 'after/[^ ]*\.png' || true)"
if [ "$after_count" -gt 0 ]; then
    echo "::error::PR #$PR changes $after_count snapshot image(s); leaving it for a human"
    exit 1
fi

if [ "$(gh pr view "$PR" --jq '.state')" != "OPEN" ]; then
    echo "PR #$PR is no longer open; nothing to do"
    exit 0
fi
gh pr merge "$PR" --merge
