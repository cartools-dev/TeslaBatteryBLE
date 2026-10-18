#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Publish the signed APK built from the tip of main as the rolling pre-release
# "main-latest", replacing the previous one, so the releases page shows a single
# preview entry. The tag name never changes; update checkers such as Obtainium
# must detect a new build by the release date (see the README). Run from a
# checkout of main with the release APK at dist/TeslaBatteryBLE-preview.apk.
#
# Needs GH_TOKEN and GITHUB_REPOSITORY.
set -euo pipefail

REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"
TAG="main-latest"
SRC="dist/TeslaBatteryBLE-preview.apk"
APK="dist/TeslaBatteryBLE-preview-${SHA:0:7}.apk"

[ -f "$SRC" ] || { echo "missing $SRC" >&2; exit 1; }
cp "$SRC" "$APK"

# Only the newest main commit publishes: an older run that finishes late must
# not put an older build on top of a newer one.
git fetch origin main
if [ "$(git rev-parse origin/main)" != "$SHA" ]; then
  echo "main has moved past ${SHA:0:7}; not publishing a stale preview."
  exit 0
fi

git fetch --tags origin
DESCRIBE="$(git describe --tags --match 'v[0-9]*' "$SHA" 2>/dev/null || echo "$SHA")"

{
  echo "Preview build of \`main\` at [${SHA:0:7}](https://github.com/$REPO/commit/$SHA) (\`$DESCRIBE\`)."
  echo
  echo "Signed with the release key, so it updates an installed release in place."
  echo "Replaced on every push to \`main\` that passes the tests, static analysis and the emulator smoke test."
} > preview-notes.md

# The old preview goes first, with its tag, so the tag can point at the new commit.
if gh release view "$TAG" --repo "$REPO" > /dev/null 2>&1; then
  gh release delete "$TAG" --repo "$REPO" --yes --cleanup-tag
fi

gh release create "$TAG" "$APK" --repo "$REPO" \
  --target "$SHA" --prerelease --title "Preview $DESCRIBE" --notes-file preview-notes.md
