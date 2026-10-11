# Angular to Android parity contract

Updated: 2026-09-01

`android/` is the product. `prototype/onboarding-ng/` is read-only visual and
behavioral evidence. Android implements the same student decisions in native
Compose; it does not copy TypeScript or ship web code.

## Visual contract

- Mobile reference viewport: 390 x 844 CSS pixels.
- Font: the Material 3 Roboto scale. Android uses the platform Roboto family;
  no network font and no bundled duplicate.
- Minimum interactive target: 48 dp.
- Shape scale: 4, 8, 12, 16, and 28 dp, plus full/pill.
- Page surfaces and every stock Material component consume the same complete
  `ColorScheme`; feature code does not declare colors.
- User copy, accessibility labels, errors, and formatted messages live in each
  module's `res/values/strings.xml`.
- Grey and Slate change only the Angular surface-container family. System
  follows the Android system appearance.

### Measured primary roles

| Accent | Light primary | Light container | Dark primary | Dark container |
|---|---:|---:|---:|---:|
| Blue | `#005CBB` | `#D7E3FF` | `#ABC7FF` | `#00458F` |
| Purple | `#7D00FA` | `#ECDCFF` | `#D5BAFF` | `#5F00C0` |
| Green | `#026E00` | `#77FF61` | `#02E600` | `#015300` |
| Amber | `#964900` | `#FFDCC7` | `#FFB787` | `#723600` |
| Rose | `#BA005C` | `#FFD9E1` | `#FFB1C5` | `#8F0045` |

Secondary, tertiary, inverse, fixed, neutral, outline, error, and all surface
container roles are encoded in `core/design`; primary-only theming is not
considered parity.

## Onboarding contract

The native flow has seven screens and a persistent six-segment setup
indicator. Back returns to the previous decision; the first screen exits only
when the host explicitly permits it. Completion creates the durable attempt,
preferences, availability, syllabus target, and first plan before navigating
to Today.

1. **Appearance** — five accent radios, System/Light/Dark/Grey/Slate radios,
   immediate preview, Continue.
2. **Coaching** — school/college, named coaching institutes, online providers,
   and self-study. The result changes taught-to and commitment defaults.
3. **Commitments** — editable recurring rows, start/end, days, add/remove,
   waking window, validation, and calculated free time.
4. **Target date** — the NEET exam date with a native Material
   date picker and local-date storage.
5. **Study hours** — shape presets, weekday/weekend half-hour sliders, weekly
   total, and study spot including custom text.
6. **NCERT scope** — all 79 rationalised Class 11 and 12 chapters are selected
   by default; the student can include or exclude whole subjects or individual
   chapters without deleting static syllabus data.
7. **Plan review** — learn/revise/slack split, date and weekly capacity,
   Edit plan, and Start day 1.

## Application shell

- Destinations: Today, Syllabus, Focus, Progress, Settings.
- Selected navigation uses the filled form of one icon family; unselected icons
  are outlined. Labels remain visible.
- The shell preserves a running Focus session across destination changes.
- Organise is a child route opened from Today/Syllabus, not a sixth bottom tab.

## Feature outcomes

### Today

Date strip, selected-day summary, chronological availability timeline, fixed
commitments, learn/revision/practice blocks, breaks/free windows, now marker,
start/continue, add-to-free-window picker, reschedule/skip, and Edit plan.

### Syllabus and Organise

Subject tabs, all/due/class filters, target progress, expandable hierarchy,
covered state, Start, taught-to boundary, included/parked state, book/custom
order, custom chapters/topics, seven-day projection, milestones, overflow, and
draft -> preview -> apply editing.

### Focus

Idle queue, Up next, browse all, configurable duration, running timer,
pause/resume, extend, finish early, completion/recall, next revision, reload
recovery, and Android-native distraction blocking with permission-safe fallback.

### Progress

Coverage, subject detail, study hours/days, planned versus actual, pace and
finish forecast, 7/30/90-day revision queues, charts/heatmap, methodology-aware
empty states, and rebalance actions that update repositories.

### Settings

Exam/date, hours/breaks, commitments, appearance, focus, reminders, export,
delete/reset, privacy, about, and clearly labeled demo data. Export has a
versioned schema; destructive actions require confirmation.

## Verification gates

Parity is complete only when all gates pass:

1. no visible Kotlin string literals outside resource-backed formatting;
2. no feature-owned raw colors or text sizes;
3. complete M3 role and type contract tests pass;
4. domain and Android unit tests pass;
5. Android lint passes or every baseline issue is documented;
6. debug APK builds and is newer than all changed source files;
7. screenshots are captured from the native app and compared screen by screen;
8. behavior is checked on a physical device where Android-only capabilities
   matter. Build output alone is never called visual verification.
