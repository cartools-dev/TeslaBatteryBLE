#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Publish the signed APK built from the tip of main as the rolling pre-release
# "main-latest", replacing the previous one, so the releases page shows a single
# preview entry. The tag name never changes; update checkers such as Obtainium
# must detect a new build by the release date (see the README). Run from a
# checkout of main with the release APK at dist/TeslaBatteryBLE-preview.apk.
#
# Deleting and recreating the release resets its date, which update checkers rely
# on, and notifies release watchers once per push.
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

# A run publishes only when its commit descends from the one the current preview
# was built from. That rejects an older run that finishes late, and it does not
# depend on this run being the very tip of main, so one failed push does not
# stop the next from publishing.
git fetch origin main
current="$(gh release view "$TAG" --repo "$REPO" --json targetCommitish --jq .targetCommitish 2>/dev/null || true)"
if [ -n "$current" ] && git cat-file -e "$current^{commit}" 2>/dev/null \
  && ! git merge-base --is-ancestor "$current" "$SHA"; then
  echo "$TAG is built from ${current:0:7}, which ${SHA:0:7} does not descend from; not publishing an older preview."
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

# The old preview is already gone, so retry: a failed create must not leave the
# releases page without a preview until the next push.
for attempt in 1 2 3; do
  gh release create "$TAG" "$APK" --repo "$REPO" \
    --target "$SHA" --prerelease --title "Preview $DESCRIBE" --notes-file preview-notes.md && exit 0
  sleep $((attempt * 5))
done
echo "creating $TAG failed after 3 attempts" >&2
exit 1
