# Steadyline web prototype — handoff

> **Desktop direction:** Before extending this prototype into the full desktop
> product study, read [`ARCHITECTURE.md`](./ARCHITECTURE.md) and
> [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md). They are the current
> contract for independent Angular code, Android behavioral parity, shared
> cross-platform artifacts, and desktop-specific interaction design.
> Current parity evidence and product decisions live in
> [`PARITY_MATRIX.md`](./PARITY_MATRIX.md) and [`DECISIONS.md`](./DECISIONS.md).

**Branch:** `prototype/onboarding-web` (22 commits ahead of `master`, none pushed)
**Path:** `prototype/onboarding-ng/`
**Run:** `npm --prefix prototype/onboarding-ng start -- --port 4300`
(or `.claude/launch.json`, entry `onboarding-ng`) · **Build check:** `npx ng build`

This is a design prototype, not the product. `android/` is what ships, and
`AGENTS.md` says do not port code out of `prototype/`. The point of this
prototype is to settle behaviour and layout before Compose work.

---

## Stack

Angular 22 standalone + signals, **zoneless**. Angular Material 22 via
`mat.theme()` — every colour, type role and shape is an M3 system variable
(`--mat-sys-*`); no component writes a raw hex. Icons are **Material Symbols
Rounded** with the `FILL` axis (outlined by default, `.filled` where a glyph
should be solid). Rendered inside a fixed 390×844 phone frame in `app.ts`;
`.phone` is the positioning context so sheets clip to the device.

Everything persists to `localStorage` under `steadyline.*` via
`core/persist.ts`. There is no backend, no account, no network call.

---

## Map

```
src/app/
  app.ts                     phone frame; onboarding vs app shell
  core/persist.ts            signal <-> localStorage, with codecs
  onboarding/
    state.ts                 OnboardingStore — the single settings store
    exam-pack.ts             exam-agnostic pack model + the NEET pack
    class11-subtopics.ts     292 NCERT section headings
    commitments.ts           fixed hours model (school/coaching/meals)
    sequence.ts              per-subject order + "taught up to" cut
    steps/*.ts               8 onboarding screens
  study/
    study-store.ts           sittings, chapter stats, retention read models
    retention.ts             recall, intervals, decay, four states
    demo-data.ts             21 deterministic days of fabricated history
  home/
    app-shell.ts             5-tab M3 navigation bar
    day-planner.ts           THE day plan; Today and Focus both read it
    scheduler.ts             free windows, packing, block types
    day-plan.ts              what to study: learn / practice / revise queues
    today-screen.ts          calendar chrome + timeline
    progress-tab.ts          coverage, pace + rebalance, due queue, time
    settings-screen.ts       settings with sub-pages
  focus/
    focus-store.ts           the session clock; writes the sitting on finish
    focus-screen.ts          idle card + queue, running dial, done
  syllabus/
    syllabus-browser.ts      the read/tick tree (subject tabs, filters)
    organise-screen.ts       IN PROGRESS — see below
    projection.ts            when each chapter lands, what misses the exam
```

---

## What works

**Today** — calendar chrome, day laid into the real free windows between fixed
hours, spread across morning/afternoon/evening, short breaks inside a stretch,
blocks name subtopics. Session sheet logs a sitting; free slots take a topic.

**Focus** (centre tab) — one card that *is* the start button, the day's queue
under it, browse-all mode for anything else. Running shows a dial, `+5`, pause,
end, what's next, and the blocking state. **Finishing a timer writes the
sitting, ticks the subtopic and schedules the next revision** — the timer is
the app's main input, not a thing to log afterwards. Ending early banks the
minutes actually sat.

**Retention** — one tap after a sitting (shaky / okay / solid) sets the next
interval (3, 10, 30, 60 days, bent by recall). Chapters decay past due. The
planner takes revision from the due queue, most decayed first.

**Progress** — coverage ring, pace verdict with a **rebalance** sheet (raise
hours / move target / park the chapters worth least per hour), due-to-revise
with a 7/30/90 range and tap-to-add-to-today, time (14-day bars + heatmap).

**Settings** — grouped sheets, no subtitles. Exam & date, hours & breaks, fixed
hours, appearance, focus & blocking, reminders, export/delete, privacy, about,
developer (demo data).

---

## In progress: editing the syllabus

The problem: real students don't follow NCERT book order. Coaching drips
modules, covers them in its own sequence, and when time is short they need to
drop things. A hardcoded tree walked top-down is wrong on day one.

**Decision taken: rule-based, no model.** It is topics, an order and a
calendar. AI was considered (see `project-sam`'s `chat-ai` edge function for
the pattern we would copy — proxy, provider fallback, strict JSON schema,
local facts authoritative, validate on return) and deferred. If it comes back
it should do one narrow job: parse a pasted coaching schedule into the same
operations that already exist.

### Done

- `sequence.ts` — `orderedChapters()` and `availableChapters()`. Every queue in
  the app reads through these, so nothing untaught is ever suggested.
- `state.ts` — `orderModes`, `customOrder`, `taughtUpTo`, all persisted.
- `day-plan.ts` / `day-planner.ts` — the learn queue reads `available(subjectId)`
  rather than walking the pack.
- `organise-screen.ts` — subject tabs, order rule, per-chapter include/exclude,
  "taught to here" marker, up/down reordering in *My order*.
- **Draft + approve.** Edits are held in a local `Draft` and committed only on
  Apply, because changing the syllabus silently reshapes tomorrow.
- `projection.ts` — walks the subjects the way the planner does, accumulates
  hours at the pace being kept, and gives every chapter a date. Rows show
  "Reached 22 Dec" or "Misses the exam, 17 Jan".
- Overflow banner: "37 chapters land after 25 Dec — 386h and 294 marks" with a
  one-tap **Drop them**.
- Preview modal: only the deltas that moved, plus a month-by-month calendar of
  where things land, months past the exam marked.

### Not done

- **Add a custom chapter / rename.** Coaching modules that don't map to NCERT
  have no home. Needs a `customChapters` collection merged into the pack read
  path — `exam-pack.ts` currently exposes a frozen `ALL_CHAPTERS`.
- **Multi-select + bulk actions.** Excluding is one row at a time.
- **The projection is optimistic.** It divides hours by a flat daily pace. It
  does not know about weekends, fixed hours, or that revision competes for the
  same minutes, so dates drift early as revision load grows. The honest fix is
  running `day-planner` forward over N days instead of dividing — bigger change,
  worth doing before anyone trusts the dates.
- **Order presets.** "High-yield first" and "quick wins first" were built and
  then removed: the pack divides a subject's hours and marks evenly across its
  chapters (`share / count`), so both sorted a flat list and did nothing. They
  return when the pack carries real per-chapter weights.
- **Syllabus tab and Organise are two views of one tree.** The browser has its
  own filter chips and does not show parked/taught state. They should agree.

---

## Known issues elsewhere

- Only NEET is a real pack. The exam picker still lists SSC/JEE/IBPS as if they
  exist — grey them or build one more.
- Accent palettes: green and rose resolve to near-neon primaries in dark theme.
  Purple is the only one that has been looked at properly.
- Android hardware back from a running Focus session must not kill the timer.
  Can't be shown in the web prototype; note it for the Compose port.
- `demo-data.ts` gives Botany far fewer practice questions than Physics — the
  rotation cursor is not weighted.

---

## Conventions that matter here

- **Spacing is one scale:** 8 inside a component, 12–16 between components, 24
  between groups. Watch for a flex `gap` on a host stacking with element
  padding — that bug made every gap 12 too big once already.
- **Measure, don't eyeball.** Verify with `getBoundingClientRect()` /
  `getComputedStyle` in the browser before claiming something is fixed. Several
  "fixes" this session were wrong until measured — a chip rendering 2px tall, a
  date shifting a day per reload, tags at ragged x positions.
- **Duplicate CSS blocks have bitten this file repeatedly.** When a rule
  "doesn't apply", grep for an older copy further down the same stylesheet.
- **Never invent syllabus data, hours or weightage.** Fetch it, or label it an
  estimate. The pack's `meta.methodology` carries the labels.
- **Check M3 before styling.** m3.material.io renders as an SPA — fetch it with
  the browser tools, not WebFetch. Two rules earned this session: don't force
  content into cards when spacing and headings do the job, and the leading slot
  is a 24dp icon or a 40dp avatar, nothing else.
