# PrepTracker (working name, was "Steadyline") — handoff

Written at the end of a session that built Focus Lock (app-blocking), the
Insights/analytics screen, a 45-day dummy-data seeder, and did a partial
rename. Read this before touching anything. **Nothing described here is
committed** — see "Git state" below before you do anything destructive.

---

## What the product is

A study planner for Indian competitive-exam aspirants (SSC CGL is the current
beachhead — the syllabus tree already exists for it). Its claim is arithmetic,
not content: the syllabus needs *N* hours, your calendar supplies *M*, and the
gap between them is the only number that matters. It contains no notes,
questions or solutions — the user brings their own books.

## Naming — currently unresolved, do not treat as settled

The package is `com.exam.assistant` (renamed from `com.steadyline` in an
earlier session). The **visible app name is now "PrepTracker"** — a
deliberate placeholder, not a final decision. A real-name search this session
ruled out every generic English candidate (Pathframe, Syllabase, Coverline,
Ondeck, Studygraph, Examframe, Grindframe — all clash with existing apps/
companies) and every Hindi/Sanskrit candidate common in the Indian
exam-prep space (Manzil, Tayyari, Parikshetra-family, Nirantar, Abhyaskaal,
Gatikaal — all already used by real competitors). The one surviving,
unclaimed candidate from that pass was **Kadamtaal** (कदमताल — step +
rhythm), which also happens to describe the app's core mechanic (daily
consistency streak). Not chosen yet — surface this the next time naming comes
up rather than re-running the same search.

Internal identifiers still say "Steadyline": class names
(`SteadylineNavHost`, `SteadylineApp`, `SteadylineBottomBar`,
`SteadylineTheme`), the `Theme.Steadyline` in `themes.xml`, and the Gradle
project name. These are invisible to the user and were left alone
deliberately — renaming them is a mechanical, low-value, high-diff chore, not
something to do incidentally while chasing a UI bug.

---

## Repo layout

```
android/      the app. Kotlin + Compose. This ships.
prototype/    throwaway. Web mockups that settled the design.
  web-app/        interactive version — best reference. `sh serve.sh`
  original-html/  first static screens
  mockups/        hi-fi artboards, older palette — layout only
  next-port/      abandoned Next.js port. Dead.
tokens.json          design tokens exported from prototype/web-app/sam-tokens.css
syllabus_cgl.json    SSC CGL tree — 4 sections, 615 nodes, 634 hrs
tools/gen_theme.py   regenerates the Kotlin palette from tokens.json
ARCHITECTURE.md      the contract for android/
AGENTS.md            which directory ships, and what not to touch
```

`prototype/` is a reference for **visual intent only**. Do not polish it, fix
its bugs, deploy it, or port code out of it. If a screen there looks broken,
that does not matter. (`prototype/web-app/insights.html` was added this
session too — untracked, same rule applies.)

---

## Current state — well past Phase 0

The old build-order table (Phase 0 skeleton → Phase 5) is obsolete. All six
feature screens are built out, not placeholders:

| Feature | State |
|---|---|
| Onboarding | done |
| Home / Today | rebuilt into a real calendar timeline (this session's predecessor) |
| Syllabus | rebuilt as a subject-card tree browser (this session's predecessor) |
| Focus | real focus timer **plus Focus Lock** — app-blocking during a session |
| Insights (bottom tab says "Progress", in-app title "Insights") | full analytics: consistency heatmap, study-time chart, plan-vs-actual, syllabus coverage, forecast, subject breakdown, revision stats, study pattern |
| Settings | theme, hours, focus length, study spot, clear-plan, **and now a dummy-data seeder** |

### Focus Lock (new this session's predecessor, extended this session)

App-blocking during a study session. Deliberately built on
`PACKAGE_USAGE_STATS` (Usage Access) + `SYSTEM_ALERT_WINDOW` (display over
other apps) rather than AccessibilityService/VPN/DeviceAdmin — smallest
reliable implementation for "block distracting apps while studying."

- `FocusLockService` — foreground service, polls `UsageStatsManager`, launches
  `BlockingActivity` over a blocked app.
- Real bug found and fixed: Android 11+ package visibility. Without a
  `<queries>` block in the manifest, `queryIntentActivities` silently returned
  only the Play Store and Sim Toolkit, making the app picker look empty. Fixed
  with a `<queries><intent><action MAIN/><category LAUNCHER/></intent></queries>`
  declaration.
- Setup flow renders inline inside the Focus tab (`Modifier.weight(1f)` in a
  `Column`), not as a full-screen `Dialog` — that was an earlier mistake,
  corrected on user report.
- String externalization for Hindi localization is **partially done**. Fixed:
  `FocusLockCard`, `ExplainStep`, `PermissionsStep`, `PermissionRow`. **Not
  done**: `AppPickerStep` in
  [FocusLockScreen.kt](android/feature/focus/src/main/kotlin/com/exam/assistant/feature/focus/FocusLockScreen.kt)
  and all of
  [BlockingActivity.kt](android/app/src/main/kotlin/com/exam/assistant/focuslock/BlockingActivity.kt).
  The `focus_lock_*` and `focus_lock_blocking_*` string resources already
  exist in both modules' `strings.xml` — only the Kotlin consumption is
  missing. This was explicitly requested ("as next step is localization and
  inclusion of hindi") and should be finished before Hindi strings are added,
  or the audit has to be redone.

### Insights / analytics (new this session)

`computeInsights()` in
[StudyAnalytics.kt](android/domain/src/main/kotlin/com/exam/assistant/domain/StudyAnalytics.kt)
is a comprehensive pure-Kotlin analytics layer — streaks, an 84-day heatmap,
pace forecast, revision stats, subject-by-subject coverage, plan-vs-actual —
that existed untracked at the start of this session (apparently written in an
earlier session and never wired up or committed) and is now fully connected
to `ProgressScreen.kt`.

Three real UI bugs fixed this session in `ConsistencyCard`
([ProgressScreen.kt](android/feature/progress/src/main/kotlin/com/exam/assistant/feature/progress/ProgressScreen.kt)):
1. Month labels were hardcoded `"Jun" / "Jul" / "Aug"` regardless of the
   actual date range — now computed from `data.heatmapDays`.
2. Heatmap week-columns had no `Modifier.weight(1f)`, so they packed to the
   left at a fixed 12dp instead of spreading across the card — fixed with
   `weight(1f)` + `aspectRatio(1f)` tiles.
3. Tapping a zero-minute day showed nothing (gated on `minutes > 0`) — now
   always shows the date, with an "No study logged" fallback for empty days.

### Dummy-data seeder (new this session)

Settings → Data → "Preview: fill in last 45 days" backfills 45 days of fake
completed study sessions against the real syllabus, so the app looks used for
demos. Pure logic in
[SeedHistory.kt](android/domain/src/main/kotlin/com/exam/assistant/domain/SeedHistory.kt)
(`generateBackfillHistory`, 7 passing unit tests), wired through
`SettingsDetailViewModel.confirmSeed()`, written via the new
`StudySessionStore.upsertAll()` (single read-merge-write instead of looping
`upsert()`).

`confirmSeed()` originally had **no error handling** — any exception mid-flow
silently killed the coroutine, left the button permanently stuck on
"Generating history…", and persisted nothing. This is almost certainly the
cause of the first "no data" bug report and has been fixed with a try/catch,
an `.coerceAtLeast(2f)` floor on hours, an empty-sessions guard, and a
corresponding error dialog (`seedError` state).

**This is unverified.** See "Open problem" below.

---

## Open problem — do not claim this is fixed

User reported (after the `ConsistencyCard` fixes and the `confirmSeed()`
error-handling fix were built and pushed to Firebase App Distribution) that
Insights still showed "80% data missing" — heatmap empty, `1 / 424 topics`,
`0m` studied. But the screenshot evidence supplied for that report visibly
matched the *pre-fix* code (hardcoded "Jun/Jul/Aug" labels, which the current
code cannot produce) and was timestamped hours before the fix was pushed.

**Status: genuinely unknown whether the current build fixes it.** No emulator
exists on this Mac (hard rule, see below) — verification has been limited to
`./gradlew :app:assembleDebug` (compiles) and `./gradlew :domain:test` (pure
logic passes, including 7 new `SeedHistoryTest` cases). Real device behavior
has not been confirmed since the fixes went in.

Next session should not re-diagnose from scratch. Instead:

1. Confirm the user has installed the **latest** Firebase App Distribution
   build (app ID `1:512302699182:android:928a6a3203840267db133f`, tester
   `mishravi2270@gmail.com`) — not an older cached install.
2. Have them tap "Preview: fill in last 45 days" fresh and screenshot
   immediately after, plus the Insights tab.
3. If `seedError` dialog appears, that's a real, now-visible exception —
   read its message, it will point at the actual defect directly.
4. If no error but Insights is still empty, the bug is downstream of
   `confirmSeed()` — check `ProgressViewModel.refresh()` is actually being
   re-invoked on tab revisit (it's wired via `LaunchedEffect(Unit)`, which
   *should* re-run per Nav-Compose recomposition semantics with
   `saveState`/`restoreState`, but this has not been empirically confirmed
   either) and that the leaf-key scheme used by `SeedHistory.kt`
   (`t1_${sectionIndex}_$topicIndex`, recursively suffixed) actually matches
   what `SyllabusRepository.tier1Sections()` / `computeInsights()` produce on
   a real device — they matched by code inspection, not by running it.

---

## Hard rules

- **Never start an Android emulator.** This Mac cannot run it; its AVDs point
  at system images that are not installed. Every attempt ends in a boot
  timeout.
- **Never run `adb` or suggest enabling Developer options / wireless
  debugging.** It breaks banking apps on the device.
- **Delivery is: build the APK, push to Firebase App Distribution.** The
  emulator restriction means UI verification is the user's job, done on
  their own device, every time.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd android && ./gradlew :app:assembleDebug && ./gradlew :domain:test
firebase appdistribution:distribute app/build/outputs/apk/debug/app-debug.apk \
  --app 1:512302699182:android:928a6a3203840267db133f \
  --testers "mishravi2270@gmail.com" \
  --release-notes "..."
```

Note the working directory for that Firebase command is `android/`, not repo
root — the path in `--app`'s companion APK argument is relative to cwd and
this has already caused one failed push this session.

`gradle-wrapper.properties` points at the `-all` distribution because that is
the one in the local Gradle cache; the `-bin` URL times out.

Claim only what the build proves — it compiles, tests pass, APK size. Never
that a screen "works" without having seen a screenshot from the user's actual
device.

---

## Git state at handoff

Everything described above is **uncommitted**. `git status` shows the same
set of modified/untracked files it did at the start of this session — no
"commit push" instruction was given, so nothing was staged. Do not assume any
of this is safe from being lost to an unrelated `git checkout` or `git
clean`; if picking this back up in a new session, check `git status` first
and confirm the working tree still matches what's described here before
trusting this document over the code.

Untracked files of note: `android/domain/.../SeedHistory.kt`,
`.../StudyAnalytics.kt` (and their tests), `docs/`, and
`prototype/web-app/insights.html`.

---

## Decisions and why (carried from the Phase-0 handoff, still true)

**Native Android, not Flutter or CMP.** India is ~92–95% Android, iOS ~4%.
App size is a measured retention lever — uninstall probability rises with
each few MB — and Flutter ships a rendering engine in every binary. `:domain`
is a plain Kotlin/JVM module, so a KMP path stays open without a rewrite.

**Manual `AppContainer`, not a DI framework.** Every store is `by lazy`,
constructed once, shared through the single `AppContainer` instance — this
was re-verified this session while investigating the "is data
fragmented across screens" question: it is not, there is exactly one
`StudySessionStore` / `SyllabusStore` instance for the whole process. DI
choice itself is still marked unresolved in the original handoff (Hilt fails
at build time, Koin fails at runtime, no decision taken) — that has not
changed.

**Threading through an injected `AppDispatchers`.** Inline `Dispatchers.IO`
hides where work happens and makes tests depend on real threads.

**Palette generated, not typed.** `tools/gen_theme.py` reads `tokens.json`.

---

## Market context (unchanged from prior handoff)

- India is ~92–95% Android; mid-range is ~48–52% of volume, 4GB RAM typical
  at entry level. Jank on mid-range devices disproportionately drives
  uninstalls.
- 70–80% of Indian edtech users churn in the first month.
- UPSC has the best product fit: adult, self-funded, no institution
  scheduling them, 2–4 year horizon.
- SSC CGL is the current beachhead because the syllabus tree already exists.
  Price floor is brutal — competitors discount yearly passes to ₹399 and
  lower.

Implication for engineering: absence of jank matters far more than animation
polish.

---

## Working with Ravi

Precise about wording; corrections are usually right and worth taking at
face value. Communicates in dense, typo-heavy bursts — decode intent, don't
ask for restatement unless genuinely ambiguous.

What this session reinforced:

- **A stale screenshot looks identical to a live bug.** When a report and a
  fix arrive close together, check the screenshot's own evidence (does it
  show code that still exists?) before re-diagnosing. This session wasted a
  round-trip because a resurfaced screenshot from before a fix was mistaken
  for a fresh test.
- **"Is X shared across screens" is a reasonable thing to actually verify**,
  not just assert — re-read `AppContainer.kt` instead of asserting singleton
  behavior from memory.
- **No emulator, ever.** Every verification claim in this codebase must be
  qualified: compiles / unit-tests-pass, not "works." The user's device is
  the only source of truth for UI behavior, and that loop is slow — minimize
  guesses that cost a round-trip.
- **Ask which file or URL before building anything visual** — inherited rule
  from the prior session, still correct, still worth restating since naming
  and screen-identity confusion is exactly what caused problems before.
