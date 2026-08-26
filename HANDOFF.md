# Steadyline — handoff

Written at the end of the session that moved this from a web prototype to an
Android skeleton. Read this before touching anything.

---

## What the product is

A study planner for Indian competitive-exam aspirants. Its claim is arithmetic,
not content: the syllabus needs *N* hours, your calendar supplies *M*, and the
gap between them is the only number that matters. It contains no notes,
questions or solutions — the user brings their own books.

The one screen that carries the idea is the **cushion**: a gap stated as a single
number, a bar showing have-vs-missing, and three concrete ways to close it.

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

`prototype/` is a reference for **visual intent only**. Do not polish it, fix its
bugs, deploy it, or port code out of it. If a screen there looks broken, that
does not matter.

---

## Current state

### android/ — Phase 0 complete

11 Gradle modules, 25 Kotlin files. Builds clean; `:domain:test` passes.

| Module | Contents |
|---|---|
| `:app` | Application, MainActivity, NavHost, AppContainer, Routes, bottom bar |
| `:domain` | Scheduler + tests. Plain Kotlin/JVM — no Android import |
| `:core:common` | AppDispatchers |
| `:core:design` | Palette (generated), AppColors, Tokens, Type, Theme |
| `:core:data` | SettingsStore, PlanStore, RemoteConfig |
| `:feature:` ×6 | onboarding, home, syllabus, focus, progress, settings |

**Real:** the scheduler (6 tests pinning it to the web numbers), the theme
system, the shell, startup sequencing.

**Placeholder:** all six feature screens. Five print their name. Settings has a
working theme switch and a palette strip so the theme layer is visible.

### prototype/web-app — feature-complete, frozen

Every screen wired: splash → onboarding (5 steps) → home, plus syllabus, focus,
progress, more, settings, account, rebalance, paywall, privacy/terms. Real focus
timer, light/dark/system theme that persists.

Still demo data there: `todayBlocks()` returns a fixed array — the daily plan is
**not** generated from the syllabus. Only SSC CGL has a real tree.

Was deployed to Netlify; auto-deploy is now off and the site is frozen.

---

## Hard rules

- **Never start an Android emulator.** This Mac cannot run it; its AVDs point at
  system images that are not installed. Every attempt ends in a boot timeout.
- **Never suggest enabling Developer options or wireless debugging.** It breaks
  banking apps on the device.
- **Delivery is: build the APK, reveal it in Finder.** Nothing else.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd android && ./gradlew :app:assembleDebug
open -R app/build/outputs/apk/debug/app-debug.apk
```

`gradle-wrapper.properties` points at the `-all` distribution because that is the
one in the local Gradle cache; the `-bin` URL times out.

Claim only what the build proves — it compiles, tests pass, APK size. Never that
a screen "works" without having seen it.

---

## Decisions and why

**Native Android, not Flutter or CMP.** India is ~92–95% Android, iOS ~4%.
Cross-platform would tax every build to serve 4%. App size is a measured
retention lever — uninstall probability rises with each few MB — and Flutter
ships a rendering engine in every binary. `:domain` is a plain Kotlin/JVM module,
so a KMP path stays open without a rewrite. Flutter was scaffolded and then
deleted; it is in git history if wanted.

**Nothing blocks the first frame except the theme.** Syllabus parsing (47KB, 817
nodes), remote config and analytics are all off the critical path. Startup reads
theme and plan-presence with `async` in parallel — neither depends on the other.

**System splash, not a splash Activity.** A splash Activity adds an entire extra
Activity launch to cold start.

**Palette generated, not typed.** `tools/gen_theme.py` reads `tokens.json`, itself
exported from the prototype CSS. Two hand-maintained copies of 45 colours drift.

**AppColors as a composition local.** Material's `ColorScheme` has no slot for
`surfaceTinted`, `warningRow`, `hairline` or the tag tints. Now in Android does
the same thing, so this is the sanctioned pattern.

**Threading through an injected `AppDispatchers`.** Inline `Dispatchers.IO` hides
where work happens and makes tests depend on real threads. No custom pools —
`Dispatchers.IO` is already elastic and shared.

---

## Open decisions

**DI is unresolved.** Currently a manual `AppContainer`. The argument for a
framework — agents get a convention they already know, instead of each inventing
a wiring style — is stronger than the manual case. Hilt fails at build time,
Koin fails at runtime. Ravi raised Koin; no decision taken.

**Known over-building, agreed but not yet fixed:**

- `SteadylineBottomBar` and `ClickableTab` are hand-rolled. M3's `NavigationBar`
  / `NavigationBarItem` already provide touch targets, ripple, indicator,
  semantics and insets. Both files should go.
- All 11 `build.gradle.kts` files repeat `compileSdk`, `minSdk`, `jvmTarget`.
  Should be `build-logic` convention plugins, NIA-style.
- Routes are strings. `navigation-compose` 2.8+ supports type-safe
  `@Serializable` routes.

**Reuse from M3 rather than build:** `TopAppBar`, `SingleChoiceSegmentedButtonRow`,
`ListItem`, `Card`, `LinearProgressIndicator`, `ModalBottomSheet`, `SnackbarHost`.

**Genuinely custom** — no M3 equivalent: the cushion gauge, the three-level
syllabus tree, circular tri-state ticks (`Checkbox` is square and has no partial
state).

---

## Build order

| Phase | Contents | Status |
|---|---|---|
| 0 | Modules, theme, nav shell, splash, AppContainer, dispatchers | done |
| 1 | Onboarding — 5 steps | next |
| 2 | Home + the real scheduler | |
| 3 | Syllabus — subject tabs, three-level tree | |
| 4 | Focus + Rebalance | |
| 5 | Progress, More, Settings | |

**Phase 2 is the only real risk.** Everything else is layout already agreed in the
prototype. The generated day plan is the unproven idea — if it is not convincing,
no later phase saves it.

---

## Market context

Searched during this session; sources in the transcript.

- India is ~92–95% Android; mid-range is ~48–52% of volume, 4GB RAM typical at
  entry level. Jank on mid-range devices disproportionately drives uninstalls.
- 70–80% of Indian edtech users churn in the first month.
- JEE + NEET are ~70% of the UG entrance-prep market, but those students already
  sit inside coaching institutes that schedule their day — the planner's value is
  lower there.
- UPSC has the best product fit: adult, self-funded, no institution scheduling
  them, 2–4 year horizon. Roughly 504 candidates coached per one selected.
- SSC CGL is the current beachhead because the syllabus tree already exists.
  Price floor is brutal — competitors' yearly passes discount to ₹399 and lower,
  so the prototype's ₹399 is priced *at* the discounted competitor rate.

Implication for engineering: **absence of jank matters far more than animation
polish.** 60fps on a ₹12,000 phone beats any transition. The audience is anxious
and time-pressured; flourish reads as wasting their time.

---

## Working with Ravi

Ten years as a mobile architect. Precise about wording; corrections are usually
right and worth taking at face value.

What went wrong in the session that produced this, so it is not repeated:

- **Eleven parallel surfaces existed at once** — two mockup sets with identical
  filenames, a Next port, a canvas viewer, a deployed site, three syllabus
  variants. "This screen" became unanswerable. Most of them were created by me.
  Keep the surface count low.
- **Ask which file or URL before building anything visual.** Guessing was
  expensive, repeatedly.
- **A screenshot conveys visual intent; prose does not.** The cleanest transfer
  all session was a pasted Reddit screenshot → the threaded syllabus tree, built
  right first time. When something looks wrong, ask for a screenshot.
- **A question is not a request to build.** Answer it and stop.
- **State the interpretation before expensive work**, so it can be corrected
  cheaply.
