# ADR-0010: Paparazzi card snapshots for pull requests

Status: Accepted
Date: 2026-10-10

## Context

- PR screenshots booted an emulator on every UI pull request and captured six
  full-screen shots (cars list, car view, settings × light/dark). Slow,
  flaky, and each capture shows only one state: a reviewer never saw what a
  stale reading, an asleep car or a disconnected link looks like after a
  layout change.
- The release job still needs the emulator: it captures the simulated car
  end to end (real rendering, real navigation) for the release notes and the
  landing page. Whatever replaces the PR capture must not weaken that.
- The two surfaces reviewers actually change are small: the cars-list card
  (`VehicleCard`) and the car-view card (`HeroCard`).

## Decision

- Pull requests render the battery cards AND the full screens as Paparazzi JVM
  snapshots (`app.cash.paparazzi` 2.0.0-alpha05): the two cards in 6 states
  each (fresh, stale with age, asleep, disconnected, reading spinner, no
  reading), the history card in 2 (a charge, and a charge followed by a
  five-day gap — the case where the stats must still name the charge), plus
  the cars list, the car view and Settings in one representative state each (a
  connected car with a fresh reading and seeded history) — light AND dark,
  plus each card state once more at Jelly Star width (3-inch, 480x854 @320dpi
  → 240dp, hand-built via `DeviceConfig.copy()` since no preset goes that
  narrow) in light only: 48 PNGs total. Full screens render one representative
  state; state coverage still comes from the card matrix, width coverage from
  the narrow render.
- `pr-paparazzi.yml` records the snapshots for the AFTER commit (HEAD) and
  the BEFORE commit (HEAD~1, the pushed commit's parent — not the merge
  base), byte-compares them, and uploads only the differing PNGs (plus the PR
  number and both short SHAs) as the `pr-paparazzi` artifact. When nothing
  differs it uploads an `unchanged` marker instead. No emulator, no secrets,
  read-only token.
- `pr-paparazzi-comment.yml` keeps the established untrusted-render →
  trusted-comment-from-main split: it runs from the default branch, validates
  the artifact inline (numeric PR, SHAs matching the run, PNG signatures,
  card-state-theme names), uploads the shots as user attachments with
  `ATTACHMENTS_TOKEN` from the `release` environment, and posts/updates one
  per-SHA comment. Light by default: each changed state shows its light
  image (before/after pair, or after-only when new); the dark image follows
  in a `<details>` block only when the dark render differs too, or on its own
  when only dark differs (a theme-specific change). The narrow Jelly Star
  render follows the same pattern, one collapsed block per state, or on its
  own when only the narrow width differs. A footer in every comment
  points at the manual emulator run (`PR screenshots` workflow).
- No committed goldens: layout changes surface as posted diffs, never as CI
  failures, so `verify.sh` gains no Paparazzi gate. The tradeoff is
  deliberate — a failing pixel gate would block unrelated work on every font
  or library bump, while a posted diff still shows the reviewer exactly what
  changed.
- The emulator stays for the release capture and the demo smoke canary, and
  the old emulator capture stays as a manual fallback: `pr-screenshots.yml`
  runs only on `workflow_dispatch` for reviews that need the real rendered
  app (full screens, real navigation).
- The same render runs locally: `bash .github/scripts/paparazzi-diff.sh`
  (before `HEAD~1`, after `HEAD` by default) records both commits, prints only
  the differing snapshots, and always restores the checkout; `pr-paparazzi.yml`
  calls that script so local and CI never drift.
- Pushes to the default branch record the full set as the
  `paparazzi-main-snapshots` artifact: a baseline reference, not a diff.
- Testability seams on the cards and screens (`internal` visibility, an
  injectable `nowMillis` defaulting to the existing ticking clock or wall-clock
  read, a deterministic spinner override, a fixed Settings version string)
  do not change production behavior: every production call site uses the
  defaults. The fixed version keeps Settings from flagging as changed on
  every commit (the real version names the commit).
- Snapshots wrap the cards in a fixed `lightColorScheme` / `darkColorScheme`,
  never the dynamic color: Paparazzi has no real context for it, so dynamic
  color would render nondeterministically.

## Update (2026-10-16): release screenshots from Paparazzi

The release job no longer boots an emulator for screenshots. It records the
Paparazzi full-screen snapshots (`release-screenshots.sh` names the cars
list, car view and Settings, light and dark, like the release page, README
and landing page expect) and publishes those. The end-to-end check that the
shrunk release build reaches the simulated car stays on the emulator in the
`Release smoke test` job on main; the release job only requires that check to
have passed for the tagged commit (`require-smoke.sh`), so no release waits for
a second emulator run.

## Update (2026-10-16): manual emulator fallback removed

`pr-screenshots.yml` and `pr-screenshots-comment.yml` are deleted: the
Paparazzi full-screen snapshots cover the same screens on every UI pull
request, the release smoke test and the demo canary cover the real app on an
emulator, and the PR comment no longer points at the manual run. The shared
`capture-screenshots.sh` stays for those two.
