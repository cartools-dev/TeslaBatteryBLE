<!-- SPDX-License-Identifier: AGPL-3.0-only -->
# CI overview

Moved out of `AGENTS.md`; the workflows in `.github/workflows/` are the source of truth.

- CI (`.github/workflows/android.yml`): core tests, debug build, release smoke test (`assembleSmoke -PdemoCar=true`: the minified release build plus the simulated car, driven on an emulator), Go fixture diff, rolling preview release (tag releases take their screenshots from the Paparazzi full-screen snapshots and require a green smoke test for the tagged commit). `pr-paparazzi.yml` (UI PRs) renders the card snapshots (light + dark) and diffs them against the PR's merge base with main without secrets; `pr-paparazzi-comment.yml` posts the diffs as PR comments from the default branch. `website-preview.yml` (`website/**` PRs) captures without secrets; `website-preview-comment.yml` posts the results as PR comments from the default branch.
- `actionlint.yml` (Workflow lint): actionlint, with shellcheck on `run:` steps, plus shellcheck on `.github/scripts/*.sh`, whenever `.github/workflows/**` or `.github/scripts/**` changes. It never builds the app.
- `exploratory.yml` (daily 03:41 UTC, and manual dispatch with a monkey seed): builds the smoke APK, runs `.github/scripts/explore.sh` on the emulator (scripted walk, font scale, rotation, process death, seeded monkey run) and files one deduplicated `exploratory-finding` issue per crash, ANR or unformatted value. It skips days when nothing outside the docs, website, fastlane and screenshot-workflow paths changed since the last successful run. Judged from the UI tree and logcat only.
