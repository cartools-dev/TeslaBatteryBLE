#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Fail unless the emulator smoke test ("Release smoke test" in android.yml)
# passed for the commit being released. Commits that only touch docs or CI
# plumbing skip the smoke test, so the newest first-parent commit that ran it
# decides: such commits cannot change the app. A skipped run does not count as
# having run: a manual release run on the same commit reports the job as
# skipped, and must not shadow the earlier push run that passed. A run that is
# still queued or in progress blocks the release rather than counting as not run.
#
# Needs GH_TOKEN, GITHUB_REPOSITORY and a checkout with history.
set -euo pipefail

: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
limit=40

for sha in $(git rev-list --first-parent -n "$limit" HEAD); do
  conclusion="$(gh api "repos/$GITHUB_REPOSITORY/commits/$sha/check-runs?per_page=100" \
    --jq '[.check_runs[] | select(.name == "Release smoke test" and .conclusion != "skipped")] | sort_by(.started_at) | last | if . == null then empty elif .status != "completed" then "in_progress" else (.conclusion // empty) end')"
  [ -n "$conclusion" ] || continue
  if [ "$conclusion" = success ]; then
    echo "Release smoke test passed on ${sha:0:7}."
    exit 0
  fi
  echo "::error::Release smoke test on ${sha:0:7} is '$conclusion'; wait for it, fix it or re-run it before releasing" >&2
  exit 1
done
echo "::error::no Release smoke test run in the last $limit commits; run Android CI on main first" >&2
exit 1
