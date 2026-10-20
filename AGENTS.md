# Agent guide

TeslaBatteryBLE: Android app + pure-JVM Tesla BLE protocol library.
License: AGPL-3.0-only (`LICENSE`, `THIRD_PARTY_NOTICES.md`).
Master plan: `docs/master-plan.md` — read it first and update its checkboxes when work lands.

## Layout

- `app/` — Android UI (Compose), BLE integration, foreground service. minSdk 27.
- `protocol/` — Tesla BLE protocol (Kotlin/JVM, Wire protos): framing, keys, pairing, sessions, commands. Knows nothing of the battery app, so it can ship as a library. **No Android dependencies allowed.**
- `core/` — battery domain (history, health, the app's own records) on top of `protocol`. **No Android dependencies allowed.**
- `tools/go-fixtures/` — fixture generator run against `teslamotors/vehicle-command`; `expected.txt` is diffed in CI.
- `docs/` — master plan, ADRs, protocol notes.

## Commands

- `.\gradlew :protocol:test :core:test` — protocol and battery-domain tests. Must pass before any commit touching `protocol` or `core`.
- `.\gradlew :app:assembleDebug` — debug APK.
- `.\gradlew :app:lintDebug` — Android lint (once wired into CI).
- `.\gradlew ktlintCheck detektMain detektTest` — formatting and static analysis on main and test sources of every module, exactly what the CI static job runs besides Android lint (`.github/scripts/verify.sh static`). Needs JDK 21; on a machine with a newer default JDK set `org.gradle.java.home` in `~/.gradle/gradle.properties`. Run it before every push: a clean `detektMain` alone misses test-source findings, and a red static job costs a round trip. To save time while iterating, name one module's tasks (`:core:detektMain :core:detektTest`).
- CI (`.github/workflows/android.yml`): core tests, debug build, release smoke test (`assembleSmoke -PdemoCar=true`: the minified release build plus the simulated car, driven on an emulator), Go fixture diff, rolling preview release (tag releases take their screenshots from the Paparazzi full-screen snapshots and require a green smoke test for the tagged commit). `pr-paparazzi.yml` (UI PRs) renders the card snapshots (light + dark) and diffs them against the PR's merge base with main without secrets; `pr-paparazzi-comment.yml` posts the diffs as PR comments from the default branch. `website-preview.yml` (`website/**` PRs) captures without secrets; `website-preview-comment.yml` posts the results as PR comments from the default branch.
- `actionlint.yml` (Workflow lint): actionlint, with shellcheck on `run:` steps, plus shellcheck on `.github/scripts/*.sh`, whenever `.github/workflows/**` or `.github/scripts/**` changes. It never builds the app.

## Conventions

- Kotlin official style; match the surrounding code. Commits and PR titles use Conventional Commits prefixes (`feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `ci`, ...) with an imperative subject (`fix: retry the handshake after a stale session`).
- Protocol changes require test vectors derived from the Go implementation. Never hand-edit `expected.txt`.
- New and touched user-visible text lives in `strings.xml` (`stringResource`, or `@StringRes` ids in enums and models), not as literals in Kotlin; use `%1$s`/`%1$d` placeholders rather than concatenation and plurals for counts. Format dates and times locale-aware (`DateFormat.getBestDateTimePattern`) and honour `DateFormat.is24HourFormat(context)`, never a hard-coded `HH:mm` pattern.
- Keep shared lists in alphabetical order so parallel PRs conflict less: add new entries to `strings.xml` (and other resource files such as `themes.xml`), registries and similar flat lists in their sorted position, never appended at the end. Keep an existing section or group's own order if it has one (for example per-screen comment groups), and sort within it. Do not re-sort an existing file inside a feature change; a one-time sort is its own `refactor:` PR.
- Never log VINs, private keys, session keys, or decrypted payloads.
- `protocol` and `core` stay multiplatform-ready: no Android, no new `java.*`/`javax.*` imports in portable logic — platform capabilities go through seams per ADR-0007.
- New source files get `SPDX-License-Identifier: AGPL-3.0-only`.
- User-visible changes update the README; architecture decisions get an ADR in `docs/adr/`. Release notes are generated from commits at tag time; there is no hand-edited changelog, so pull requests never collide on one.
- Pre-1.0: no back-compat or migration code for states that only exist on old betas or dev machines; change formats freely and document the reset (re-pair / reinstall). The policy ends at the first normal (non-prerelease) release (ADR-0004, issue #123); from then on formats are versioned and changes ship importers or an in-app reset notice.
- Merge PRs with a merge commit after rebasing the branch on `main`: `gh pr merge <n> --merge`. Never rebase-merge — history keeps PR provenance and reverts stay one command.
- Specs, plans, and product docs never name competitor products; competitive research lives in `docs/research/` only.

## GitHub comments

- Post agent comments as `github-actions[bot]` through the `Agent comment` workflow instead of the maintainer account:
  `body_b64="$(printf '%s' "comment" | base64 -w0)"; gh workflow run agent-comment.yml -f pr=<n> -f body_b64="$body_b64"`
- Pushes, merges, and commits stay on the maintainer account.

## Keep code inside the static-analysis limits

detekt runs at its default thresholds (`config/detekt/detekt.yml` only sets `MaxLineLength` to 140). The limits that keep failing are cyclomatic complexity per function, function length, functions per class, parameters per function, return count and magic numbers. Design to stay under them instead of fixing after a red CI run:

- Extract a helper or a small class as soon as a function grows branches; do not wait for the limit.
- Group related parameters into a data class; name numeric constants.
- Never baseline a new finding (`detekt-baseline-*.xml` only shrinks).
- Two PRs that are each green can still break `main` together when both grow the same function; after rebasing on a merged PR that touched your files, re-run the checks.

## Do not

- Commit secrets, keystores, or `local.properties`.
- Create accounts, spend money, or submit to stores without the owner's explicit go-ahead.
- Mix unrelated refactors into a feature/fix change.
- Touch uncommitted working-tree changes that are not yours.
