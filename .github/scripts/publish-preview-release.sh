#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Publish the signed APK built from the tip of main as a pre-release named
# preview-<sha>, then prune the older previews. Every preview is its own release
# (not one moving tag) so update checkers such as Obtainium see a new release for
# every build. Run from a checkout of main with the release APK at
# dist/TeslaBatteryBLE-preview.apk.
#
# Needs GH_TOKEN and GITHUB_REPOSITORY.
set -euo pipefail

REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"
KEEP="${PREVIEW_KEEP:-5}"
APK="dist/TeslaBatteryBLE-preview.apk"
TAG="preview-${SHA:0:7}"

[ -f "$APK" ] || { echo "missing $APK" >&2; exit 1; }

# Only the newest main commit publishes: an older run that finishes late must
# not put an older build on top of a newer one.
git fetch origin main
if [ "$(git rev-parse origin/main)" != "$SHA" ]; then
  echo "main has moved past ${SHA:0:7}; not publishing a stale preview."
  exit 0
fi

if gh release view "$TAG" --repo "$REPO" > /dev/null 2>&1; then
  echo "$TAG is already published."
  exit 0
fi

git fetch --tags origin
DESCRIBE="$(git describe --tags --match 'v[0-9]*' "$SHA" 2>/dev/null || echo "$SHA")"
cp "$APK" "dist/TeslaBatteryBLE-${TAG}.apk"

{
  echo "Preview build of \`main\` at [${SHA:0:7}](https://github.com/$REPO/commit/$SHA) (\`$DESCRIBE\`)."
  echo
  echo "Signed with the release key, so it updates an installed release in place."
  echo "Published automatically after the tests, static analysis and the emulator smoke test pass; only the newest $KEEP previews are kept."
} > preview-notes.md

gh release create "$TAG" "dist/TeslaBatteryBLE-${TAG}.apk" --repo "$REPO" \
  --target "$SHA" --prerelease --title "Preview $DESCRIBE" --notes-file preview-notes.md

# Drop the older previews (newest first, keep the first $KEEP).
gh api --paginate "repos/$REPO/releases" \
  --jq '.[] | select((.tag_name | startswith("preview-")) and (.draft | not)) | [.created_at, .tag_name] | @tsv' \
  | sort -r | sed -n "$((KEEP + 1)),\$p" | cut -f2 \
  | while read -r old; do
      gh release delete "$old" --repo "$REPO" --yes --cleanup-tag
    done
