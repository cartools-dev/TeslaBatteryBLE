#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Attach the signed APK built from the tip of main to the "Next release (draft)"
# kept by update-draft-release.sh, replacing the previous one. A draft is only
# visible to accounts with push access, so the public releases page gets no
# preview builds; an update checker that signs in with a token (Obtainium) can
# still see it. Run from a checkout of main with the APK at
# dist/TeslaBatteryBLE-preview.apk.
#
# Needs GH_TOKEN and GITHUB_REPOSITORY.
set -euo pipefail

REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"
SRC="dist/TeslaBatteryBLE-preview.apk"
APK="dist/TeslaBatteryBLE-preview-${SHA:0:7}.apk"

[ -f "$SRC" ] || { echo "missing $SRC" >&2; exit 1; }
cp "$SRC" "$APK"

# Only the newest main commit attaches: an older run that finishes late must not
# put an older build on top of a newer one.
git fetch origin main
if [ "$(git rev-parse origin/main)" != "$SHA" ]; then
  echo "main has moved past ${SHA:0:7}; not attaching a stale build."
  exit 0
fi

DRAFT_ID="$(gh api --paginate "repos/$REPO/releases" \
  --jq '.[] | select(.draft and .name == "Next release (draft)") | .id' | sed -n 1p)"
if [ -z "$DRAFT_ID" ]; then
  echo "No draft release (main is at a release tag); nothing to attach to."
  exit 0
fi

# The new APK goes up first, so the draft is never left without one.
for attempt in 1 2 3; do
  gh api --method POST \
    -H "Content-Type: application/vnd.android.package-archive" \
    "https://uploads.github.com/repos/$REPO/releases/$DRAFT_ID/assets?name=$(basename "$APK")" \
    --input "$APK" > /dev/null && break
  [ "$attempt" -lt 3 ] || { echo "uploading $APK failed after $attempt attempts" >&2; exit 1; }
  sleep $((attempt * 5))
done

gh api "repos/$REPO/releases/$DRAFT_ID/assets" \
  --jq ".[] | select(.name | endswith(\".apk\")) | select(.name != \"$(basename "$APK")\") | .id" \
  | while read -r old; do
      gh api -X DELETE "repos/$REPO/releases/assets/$old"
    done
