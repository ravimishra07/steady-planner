# NEET Planner — Production V1 Plan

**Status:** Draft for execution · **Created:** 2026-10-07 · **Owner:** Ravi
**Shipping code:** `android/` only. `prototype/` stays visual reference.
**Supersedes:** nothing. Complements `docs/ANDROID_RELEASE_READINESS_PLAN.md`
(deleted in the working tree on 2026-10-07; recover it with
`git show HEAD:docs/ANDROID_RELEASE_READINESS_PLAN.md`). Its P0 work is landed
in commit `0dcfd5a`. Its device, signing and Play gates are carried into §12 here.

This file is the single contract for taking the app from today's state to a paid
production release on Google Play. It covers four things:

- product changes driven by student research;
- correctness fixes for every metric the student sees;
- monetisation;
- release operations.

Each item has an ID, a priority and acceptance criteria. An item is done only
when its acceptance criteria are met **and** verified on a physical device where
behaviour depends on the device (ARCHITECTURE.md §10: no emulator, no adb).

**Priority labels**

| Label | Meaning |
|---|---|
| **P0** | Ship blocker: wrong numbers, data loss, broken core loop, policy risk |
| **P1** | Required for V1: the core student value is incomplete without it |
| **P2** | V1 polish: quality, accessibility, performance |
| **V1.1** | Explicitly deferred. Do not build in V1 |

---

## 0. Before touching code

- [ ] **0.1** Commit or stash the uncommitted onboarding refactor. 9 files are
  deleted, and `components/`, `steps/` and `model/` are untracked.
- [ ] **0.2** Decide whether the deleted `docs/ANDROID_RELEASE_READINESS_PLAN.md`
  stays deleted (this plan replaces its open items) or is restored.
- [ ] **0.3** Branch: work happens off `ravi/prototype-port-ready`. Merge to the
  default branch at each phase end.
- [ ] **0.4** Product name and application ID decision (see §11.1). The code says
  "Steadyline" internally, which collides with the live Steadyline app.

---

## 1. What we learned (inputs to this plan)

Sources: Play Store review mining (19 apps, about 33K India reviews, pulled
2026-10-07), topper and coaching research, and a code audit. Reddit research
was blocked or still pending at time of writing. **Fold in Reddit findings and
the 10 student interviews (§13) before Phase 3 starts.**

### 1.1 Market facts (sourced)

- NEET UG 2026: 22,79,743 registered, 19,99,895 appeared (NTA via Testbook).
- Past exam dates: 7 May 2023, 5 May 2024, 4 May 2025, 3 May 2026 (cancelled
  after a leak; re-exam 21 June 2026).
- **NEET 2027 date: not announced.**
- **NEET 2027 moves to computer-based testing** (Education Minister, 15 May
  2026). NTA's bulletin is still pending.
- Comparable apps on Play (India):
  - **MemoNeet:** 1M+ installs, 4.39★ from 12.6K ratings. Day-wise plan from
    the exam date, spaced repetition, paid content packs.
  - **TrackIt:** 500K+ installs, 4.64★ from 5.2K ratings. Pricing (iOS India):
    ₹99/month, ₹399/year, ₹699 lifetime.
  - **MyStudyTracker:** 50K+ installs, 4.69★. Free, no ads, praised *because*
    it is free.
- Coaching rhythm (Aakash 2025-26 published schedule): a unit test about every
  2 weeks, part and cumulative tests, a monthly all-India test, then weekly
  full-syllabus tests in the final 7 weeks.
- Topper loop: same-day revision, then DPPs or practice, then tests, then a
  mistake notebook. Mocks get much denser near the exam (one 2025 topper did
  22 mocks in 54 days).
- Chapter weightage: there is no official source. Per-paper chapter counts
  from PW and Vedantu agree to within ±1 question. Multi-year "weightage %"
  pages are unreliable. Store each data point with its source and year.

### 1.2 What students punish (1–3★ themes, ranked)

1. Paywalls on basic setup: subject or chapter caps, no trial.
2. Purchases that don't carry over to a second phone; no sync or backup.
3. Timers that fail with the screen off, no end sound, wrong totals, a fixed
   day start (5 AM).
4. Ads that interrupt studying, especially an ad after every tick.
5. Data lost on update, reinstall or switching exams.
6. Outdated syllabus (removed chapters still present), no editable chapter
   names (coaching names differ from NCERT names), ticks that don't stick.
7. Apps that need internet. Students turn data off to study.
8. Redesigns that break a simple UI.
9. Plans that stop working after day one, or goals that change by themselves.

### 1.3 What students reward and request

- **Rewarded:**
  - free, no ads;
  - a detailed preloaded syllabus;
  - time per subject and per topic;
  - visible progress;
  - **revision reminders** (MemoNeet's top review, 355 upvotes);
  - a daily plan made for them.
- **Requested repeatedly:**
  - revision cycles (1-3-7-28 style) and a revision count per chapter;
  - an exam countdown;
  - a syllabus finish timeline with days left;
  - a home-screen widget or persistent notification with % covered;
  - editable per-chapter checkboxes (Notes / NCERT / PYQ / …);
  - weighting by marks;
  - a mock score log with a trend graph;
  - backlog tracking;
  - questions solved per day;
  - manual time entry;
  - a configurable day start;
  - Monday-first week;
  - offline use;
  - sync or restore.

### 1.4 What the current code gets wrong

The full metric inventory is from the 2026-10-07 code audit.

- **Hours are invented.** `app/src/main/assets/syllabus_neet.json` has 902 hours
  split as about 225 hours per subject, evenly per chapter:
  - Physics 8h per chapter, Chemistry 12h, Botany 11.25h, Zoology 18.75h.
  - There is no weightage. The only exam data is `q: 45` per subject.
- **The same concept has different values on different screens:**
  - coverage %: 4 formulas;
  - total hours: 4 values (902 / 1,155 / per-subject / truncated);
  - revision due: 3 rules;
  - studied minutes: 2 sources.
- **The plan schedules only LEARN blocks.** There are no practice, PYQ, mock or
  revision-phase blocks. Work that doesn't fit is silently dropped
  (`RollingPlanResult.unscheduledMinutes` is not shown anywhere).
- **The exam date and the finish-by date are one field.** The default is today
  + 118 days.
- **Revision outcomes are applied twice** (`StudySessionRepository` + `revisionStateWithOutcome`).
- **Leftover subject IDs from an older exam setup** (`quant`, `reasoning`, `ga`,
  `english`) reach Insights (`StudySession.kt` `sectionSubjectId`).
- **Focus Lock needs** `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW` and a
  `FOREGROUND_SERVICE_SPECIAL_USE` foreground service. All three are Play
  policy review risks.
- **Not in the build:** analytics, crash reporting, billing, widget,
  WorkManager/notifications scheduling, export/backup.

---

## 2. V1 product definition

**One sentence:** *Tells a NEET aspirant exactly what to study today, adjusts
when life gets in the way, and shows honestly whether they will finish the
syllabus before their date. Offline, no ads.*

### 2.1 Core loop

```
Onboard (≤3 min) → Today: one obvious next block → Focus → mark outcome
      ↑                                                     ↓
 Replan on miss / change  ←  Insights: on track? what's due?  ←  Revision queue
```

### 2.2 In V1

- Onboarding with deadline mode as the default, plus steady mode (§3).
- A planner that schedules Learn, Practice and Revise blocks, and a revision
  phase before the exam (§4).
- The syllabus with per-chapter stages and editable names (§5).
- Focus timer that is reliable with the screen off, with manual time entry (§6).
- Revision engine: one system, visible counts (§7).
- Mock test log with score trend and a lightweight mistake log (§8).
- Insights with one canonical formula per metric (§9).
- Home-screen widget (§10.3).
- Backup export and import, plus restoring purchases (§10.4).
- Paywall: generous free tier, Pro unlock (§11.2).
- Analytics and crash reporting with honest disclosure (§11.3).

### 2.3 Not in V1 (V1.1 or later)

- Focus Lock / app blocking. Removed from V1 for Play policy reasons; may
  return after V1 approval as a separate opt-in feature.
- Accounts and cloud sync. Export/import covers restore in V1.
- Social features, study rooms, leaderboards.
- Content: questions, videos, PYQ papers, AI chat.
- Coaching institute schedule presets.
- Hindi UI. Strings are already resource-backed. Hindi chapter names come only
  if interviews demand it.
- Parent dashboard.
- iOS.

---

## 3. Onboarding and plan modes

### P1-3.1 Deadline mode is the default; steady mode is an option

- **Question:** "Do you have a date you want to finish the syllabus by?"
  - **Yes** (default, preselected) → deadline mode.
  - **Not yet** → steady mode.
- **Deadline mode:** the student sets a finish-by date. The plan spreads
  remaining work across the days up to that date. The app shows required vs
  available hours.
- **Steady mode:** the student sets daily hours only. The plan sequences the
  next work. The app shows a *projected* finish date from actual pace. There is
  no "behind" state.
- The student can switch modes any time from Settings → Plan. History is
  always kept.

**Acceptance criteria:**
- Both modes generate a plan from the same engine (§4).
- Switching modes regenerates future AUTO blocks only.
- Steady mode never shows "behind", "short" or a red state.

### P0-3.2 Separate the exam date from the finish-by date

- Add `examDate` (NEET date) separately from `targetCompletionDate` (the
  student's goal). Today both are set from one picker in `OnboardingViewModel`.
- **Exam date:**
  - Default to the official NEET date when it is known.
  - Until NTA announces it, show "NEET 2027 · expected early May (not yet
    announced)". Keep it remotely updatable via the `RemoteConfig` interface.
- **Finish-by date:** default to exam date − 90 days (a revision window).
  Editable. It must be on or before the exam date.

**Acceptance criteria:**
- No screen labels the finish-by date as "Exam".
- The countdown widget uses the exam date.
- The finish-by date is validated to be ≤ the exam date.

### P1-3.3 Starting point

- Add a step: "Which class / attempt?" with options Class 11 · Class 12 ·
  Dropper/repeater.
  - Droppers default to "most chapters done once". The first run proposes
    revision-shaped plans.
- **Fast bulk entry:** "Mark chapters you've already finished" lets the
  student tick a whole subject or class, then untick exceptions.
- Fix the edit-mode bug: covered chapters must be subtracted from the need
  calculation (`OnboardingViewModel` need calculation ignores covered).

**Acceptance criteria:**
- A dropper can mark about 80% of the syllabus done in ≤5 taps.
- The feasibility number reflects only the remaining work.

### P0-3.4 Honest feasibility screen

- **Replace with real numbers:**
  - "Remaining work: ~X h (estimate)"
  - "Your time to finish-by date: Y h"
  - "Fits" or "Short by Z h"
- **Remove the double-counted revision.** The `REVISION_MULTIPLIER` 1.28 *and* a
  separate Revise-days bar must not both apply. Revision time is planned
  explicitly in §4.3, so learn hours carry no multiplier.
- **Count days correctly.** Leftover days must use their real weekday/weekend
  type (`Scheduler.availableHours` treats them as weekdays). Use per-date
  availability from the actual commitments, not the 5+2 approximation.
- **When short, offer real choices**, each showing its effect:
  1. Add N minutes per day.
  2. Park low-priority chapters (§4.2).
  3. Move the finish-by date.
  4. Accept partial coverage.
- Every hour figure carries an "estimate" label until calibrated (§4.1).

**Acceptance criteria:**
- The feasibility numbers equal what the planner actually schedules (same
  function).
- Unit tests pin the arithmetic.

### P2-3.5 Copy and defaults

- Defaults must never invent coaching. The school and coaching presets stay
  opt-in (already done 2026-09-04; verify).
- Day start / "study day ends at" is configurable (supports late-night study
  past midnight).

---

## 4. Planner engine

### P0-4.1 Hours: honest estimates plus personal calibration

- Keep the per-section `m` estimates, but label them "estimate" everywhere.
- **Calibration:**
  - After at least 3 completed Learn chapters, compute
    `personalFactor = actual focused minutes / estimated minutes` (median,
    clamped 0.5–2.5).
  - Apply it to the remaining estimates.
  - Show: "Your pace: chapters take you about 1.3× the estimate."
- Replace the even per-chapter split with researched relative chapter sizes
  when available (§4.2 data task). Until then, keep the even split but label
  it.

**Acceptance criteria:**
- No hour number appears without an estimate label before calibration.
- The calibration factor is unit-tested.
- The calibration factor is visible in Insights.

### P1-4.2 Chapter priority from PYQ data (data task plus code)

- **Data task:**
  - Build `assets/neet_pyq_weightage.json`: for each chapter ID, a list of
    `{year, questions, source}`.
  - Use per-paper counts from at least 2 coaching analyses per year (PW,
    Vedantu, Allen, Aakash).
  - Record disagreements, using the median across sources.
  - Cover at least 2019–2025 plus 2026 (both the cancelled and re-exam papers,
    flagged).
  - **Never** use "expected" or projected numbers.
- **Use:**
  - Priority = average questions per year. Labels: High / Medium / Low.
  - Shown on the chapter row as "~N Qs/yr (2019–25)". The source is visible on
    tap.
  - When the student is short (§3.4 option 2), suggest parking the
    lowest-priority chapters first.
  - In the revision phase, order by priority.
- Ordering options in Organise: Syllabus order (default) · High-yield first ·
  Shortest first.

**Acceptance criteria:**
- Every weightage number has its source and years on tap.
- Chapters with no data show "no data", never 0.

### P0-4.3 Block types and phases

- **The planner schedules Learn, Practice and Revise blocks automatically**
  (today: Learn only).
  - After a chapter's Learn work, schedule a Practice block. Default 1 per
    chapter, length = 25% of the chapter's estimate (configurable).
  - Revision blocks come from the revision engine (§7) and are **real plan
    blocks**, not display-only timeline entries (today, `DayTimeline`
    synthesises 30-min auto-revision entries that aren't in the plan).
- **Revision phase** (deadline mode): from the finish-by date to the exam date,
  the planner switches to:
  - revision passes ordered by priority (§4.2) and weakness (§7, §8);
  - mock tests, with density rising toward the exam (default 1 every 2 weeks,
    then weekly, then 2–3 a week in the last 4 weeks; configurable).
- **Steady mode:** Learn → Practice → Revise interleaved; no phase switch until
  the student sets a date.

**Acceptance criteria:**
- A generated 21-day plan contains all three block types.
- A plan in the revision window contains no new Learn blocks unless the
  student opts in.

### P0-4.4 Replanning rules

| Trigger | Behaviour |
|---|---|
| One block missed | Recovery card: "Move to next free slot" (shows the slot) · "Drop it". One tap. Already built; keep it |
| Whole day or days missed | On next open: "You missed N blocks. Rebalance the next days?" · "Yes" / "Not now". Deadline mode then re-checks the fit (§3.4) |
| Hours, dates, chapters or commitments changed | Regenerate future AUTO blocks. Never touch COMPLETED, RUNNING, MANUAL or moved blocks (already enforced in `RollingPlan`; keep tests) |
| Daily | Top the horizon back up to 21 days (WorkManager or on app open) |
| Today regenerated mid-day | **Never place blocks in the past.** Start today's fill at `now` rounded up to 5 min (today `generateRollingPlan` fills from window start, so blocks show as missed immediately) |

- **Never silently drop work.** Deadline mode shows unscheduled work: "X h
  doesn't fit before your date" with the §3.4 choices.

**Acceptance criteria:**
- Unit tests for each trigger.
- `unscheduledMinutes` is visible in deadline mode.

### P1-4.5 Coaching-aware input (light)

- "Taught in class today" quick log on Today: pick chapter(s), then mark
  Learn as done and schedule a same-day 20–30 min revision block plus a
  Practice block.
- Editable chapter display names, so coaching names map to NCERT chapters
  (§5.2).

---

## 5. Syllabus and tracking

### P1-5.1 Per-chapter stages (replace the single covered tick)

- **Default stages per chapter:** Learned · NCERT read · Practice/DPP · PYQ ·
  Revised ×N. These match printable trackers and review requests.
- Stage labels are editable per student (rename, hide, add one custom stage).
- **Coverage definition (canonical, §9):** a chapter counts as covered when
  its **Learned** stage is ticked. The other stages show depth and do not
  change coverage %.
- Completing a Learn Focus session on a chapter prompts: "Mark chapter learned?"
  with Yes / Not yet. Today, a session only sets IN_PROGRESS silently.
- The section level (the 512 NCERT sections) stays available as an optional
  expand. It is not required for ticking.

**Acceptance criteria:**
- A tick persists across scroll, restart and update (a review complaint).
- Stage ticks take one tap, with no ad and no dialog.

### P0-5.2 Syllabus correctness and editing

- Verify `syllabus_neet.json` against the current NMC NEET UG syllabus and
  the rationalised NCERT books. Remove deleted topics. Record the verification
  date and source in the JSON `source` field.
- **Editing:**
  - rename a chapter;
  - add a custom chapter or topic (exists in Organise; keep);
  - park a chapter (exclusion).
- **Versioning:** a syllabus update must migrate progress by stable ID and
  never wipe it.

**Acceptance criteria:**
- There is a checklist of every chapter against its source.
- A migration test shows progress surviving a syllabus version bump.

### P1-5.3 Backlog view

- **Definition:** backlog = planned blocks in the past that are not completed
  and not dropped, plus chapters behind the deadline-mode pace.
- Shown on Today as a count, with a "Clear backlog" plan action that spreads
  the backlog over N days.

---

## 6. Focus timer

### P0-6.1 Reliability

- The timer must complete correctly with the screen off, after process death,
  and after a device reboot during a session (reconcile on boot).
- An end-of-session **sound and/or vibration** via a notification channel.
  User-selectable; on by default.
- A foreground notification while running ("Physics · 32:10 left") with Pause
  and Stop actions.
- Remove `FOREGROUND_SERVICE_SPECIAL_USE` unless it's still needed after Focus
  Lock is removed. Prefer an exact alarm or notification approach; check the
  Play exact-alarm policy if `SCHEDULE_EXACT_ALARM` is used.

**Acceptance criteria:**
- Physical device matrix (§12): screen off for 50 min, app swiped away, Doze,
  and at least 1 OEM with aggressive battery handling (Xiaomi, Realme, Vivo or
  Samsung).

### P0-6.2 Correct time accounting

- One source of truth for studied minutes: **focused seconds** from
  `StudySession`.
  - Home must stop crediting the full planned length for partial sessions
    (`HomeViewModel` uses `durationMinutes`).
- Fix the double-applied outcome (`StudySessionRepository` completion +
  `revisionStateWithOutcome`). The outcome is applied exactly once.
- "Day" boundary uses the configurable day start (§3.5).

### P1-6.3 Manual entry and edits

- "Log study I did offline": subject, chapter, type, duration, date and time.
- Edit or delete a past session. Analytics recompute.

### P2-6.4 Lengths

- Presets 25 / 50 / 90, plus custom (5–180 min).
- Settings and the Focus chip write to the same store (today Focus writes only
  the settings store).

---

## 7. Revision engine (one system)

### P0-7.1 Unify

- **Delete** the legacy fixed rule `revisionSuggestions` ("studied exactly 3
  days ago", `StudySession.kt`). Its uses in Insights move to `RevisionState`.
- **One schedule:**
  - After Learn: first revision due at +1 day (Struggled) / +3 (Okay) / +7
    (Strong).
  - After each revision: Struggled → 1 day; Okay → ×2 (cap 30); Strong → ×3
    (cap 60).
  - Make these intervals a single constant table.
- **Overdue items stay visible as overdue.** Today they vanish from Insights.
- Revision count per chapter is visible on the chapter row ("Revised ×3").

### P1-7.2 Revision on Today

- Due revisions become real `REVISION_ENGINE` plan blocks placed in free time.
  The default length is 20–30 min and is configurable.
- Cap revision load at 40% of a day's study time. Overflow rolls to the next
  day, ordered by most overdue first.

**Acceptance criteria:**
- Home, Insights and the chapter row show the same due set.
- Unit tests cover the interval table and the overdue rule.

---

## 8. Tests, mocks and mistakes

### P1-8.1 Mock and test log

- **Log:**
  - date;
  - test name/type (coaching unit test · part test · full mock · PYQ paper);
  - score per subject;
  - total out of 720;
  - attempted / correct / wrong (optional).
- Entry in ≤60 seconds; per-subject fields are optional.
- Trend graph: total and per subject over time.
- **CBT note:** NEET 2027 is computer-based. Label mock types accordingly; no
  OMR assumptions.

### P1-8.2 Lightweight mistake log

- **Per test, optional:** chapters where marks were lost, with a mistake type
  (Concept · Silly/calculation · Time pressure · Guess).
- Chapters tagged here get a sooner revision (feeds §7 as a Struggled
  outcome) and rise in revision-phase priority.
- Insights shows the "Most-lost chapters" list.

### V1.1-8.3

Question-level mistake notebook with photos. Rank prediction.

---

## 9. Insights: one formula per metric

Create `domain/Metrics.kt`. **Every screen calls these functions; no screen
computes its own.** Remove duplicate computations in `HomeViewModel`,
`SyllabusViewModel`, `ProgressViewModel`, `MoreRoute` and `OrganiseViewModel`.

| Metric | Canonical definition | Notes |
|---|---|---|
| **Coverage %** | Learned chapters ÷ target chapters (parked excluded), rounded down | One denominator everywhere: chapters, not sections |
| **Remaining work (h)** | Σ remaining estimated minutes of unlearned target chapters × calibration factor | Labelled estimate until calibrated |
| **Studied time** | Σ focused seconds of completed + partial sessions + manual entries | Never planned length |
| **Plan adherence %** | Completed planned minutes ÷ planned minutes **for blocks whose end time has passed** | Fixes "always behind mid-day" |
| **Pace** | Studied minutes in last 14 days ÷ 14, shown only with ≥7 active days | Learn minutes only for the finish forecast; total minutes for the habit view |
| **Finish forecast** (both modes) | `today + remaining work ÷ learn pace`, range ×0.85–×1.15 | Uses the same remaining-work number as onboarding |
| **Required pace** (deadline) | Remaining work ÷ available study minutes until finish-by (per-date availability) | Not calendar days |
| **Subject status** (deadline only) | Subject coverage vs expected coverage from the plan schedule (not a straight line), ±5 pts | Hidden in steady mode and for the first 7 days |
| **Revision due / overdue** | From `RevisionState` only | §7 |
| **Revision on time %** | Revisions completed by their due date ÷ revisions due in period | Rename it if it's computed any other way |
| **Streak** | Consecutive days with ≥1 completed session, using the configured day start | — |
| **Days to exam** | `examDate − today` | Not the finish-by date |
| **Days to finish-by** | `targetCompletionDate − today` | Deadline mode only |

**Also:**
- Fix the "This week" heading to follow the selected period.
- Fix "Chapters done" labels that count sections.
- Remove the leftover `quant/reasoning/ga/english` IDs (`sectionSubjectId`).
- The heatmap follows the period or is labelled "Last 12 weeks".
- Remove dead code: `DaySummaryHeader`, `OnboardingSteps.kt` cushion
  composables, `generateInitialPlan`, the legacy `SyllabusRepository`, and
  unused strings.

**Acceptance criteria:**
- **A cross-screen consistency test.** One fixture state is rendered through
  every metric consumer; the same concept gives the same number.
- The test runs in CI or the local test suite.

---

## 10. Platform features

### P1-10.1 Notifications (opt-in, quiet by default)

- **Off by default, except:** the Focus session end (§6.1) and one optional
  daily "Today's plan" reminder at a time the student chooses.
- No streak-guilt notifications.
- Ask for `POST_NOTIFICATIONS` in context: when enabling a reminder, not at
  launch.

### P2-10.2 Offline

The whole app works with zero network. Analytics batches and sends when a
connection is available (§11.3). Verify by testing in airplane mode.

### P1-10.3 Home-screen widget (Glance)

- **Small widget:** days to exam plus coverage %.
- **Medium widget:** next block plus a Start button that deep-links into Focus.
- Updates on plan or session change and at midnight / day start.

### P0-10.4 Backup, restore, devices

- Export everything as a single JSON file (plan, progress, sessions, revision,
  mocks, settings) through the Android share sheet or SAF.
- Import, with a "replace current data" confirmation.
- Versioned schema.
- **Pro restores on any device signed into the same Google account** via Play
  Billing `queryPurchasesAsync`, with no account system needed. A complaint
  about a competitor ("asks me to repurchase on second phone") must be
  impossible here.
- Keep `allowBackup=false` / data-extraction rules **or** switch to Android
  Auto Backup. Decide, then make the privacy copy say exactly what happens.
- Export/import is **free**. Never paywall the student's own data.

**Acceptance criteria:**
- Export, uninstall, reinstall, import: data is identical.
- Pro restores after reinstall.

---

## 11. Business and release

### Decisions locked (Ravi, 2026-10-07)

- **Application ID:** keep `com.exam.assistant`.
- **Analytics:** Firebase Analytics + Crashlytics. Not PostHog.
- **Pricing:** Pro = **₹59 lifetime**, a one-time non-consumable.
  - No subscription, so no trial. The free tier is the trial.
  - This supersedes the price hypothesis in §11.2.
- **Git:** commit locally only. Ravi pushes.

### P0-11.1 Identity

- **Product name:** decide. It must not be "Steadyline". Candidates must pass
  a Play search collision check.
- **Application ID:** `com.exam.assistant` is generic. Choose the final ID
  **before** the first production upload; it can never change after.
  - Firebase App Distribution testers' data will not carry over. That's
    acceptable, since there are no production users yet.
- Rename internal `Steadyline*` symbols (`SteadylineNavHost`, `SteadylineTheme`)
  to the product name.

### P0-11.2 Paywall

**Principles from the review evidence:**
- Never cap subjects or chapters.
- No ads, ever.
- Offer a trial.
- Lifetime is accepted.
- Restore must work.

| Free (forever) | Pro |
|---|---|
| Full syllabus, all subjects and chapters, stages | Revision-phase planner and high-yield (PYQ) ordering |
| Daily plan, replanning, recovery | Mock/test log trend graphs + mistake log + "most-lost chapters" |
| Focus timer, manual entry | Full Insights: forecast range, subject status, calibration detail, all periods |
| Revision engine with reminders and counts | Medium widget (Start next block) |
| Basic Insights (today, 7 days, coverage, streak) | Custom stages and themes |
| Small widget, export/import | — |

- **Prices (hypothesis; test via remote config):** Pro lifetime ₹499,
  Pro yearly ₹249. The anchor is TrackIt (₹399/yr, ₹699 lifetime).
  - Consider "Season pass to NEET 2027", a one-time purchase valid until the
    exam date plus 60 days. It would be a non-consumable with an expiry
    enforced in-app; verify it complies with Play policy before using it.
- **7-day free trial** of Pro via a Play Billing subscription offer (yearly
  plan). Lifetime has no trial; the free tier serves as one.
- **Paywall placement:**
  - when tapping a Pro feature;
  - one soft prompt after day 7 *if* the student has ≥5 active days.
  - Never during onboarding or a Focus session.
- **Implementation:**
  - Google Play Billing Library (current major version).
  - Entitlement cached locally, so Pro works offline.
  - Restore on launch and from Settings.
  - Purchase acknowledgement.
  - Handling for refunds and revocation.
  - Server-side verification is optional for V1, since local verification is
    acceptable at this price. Document the risk.

**Acceptance criteria:**
- Purchase, restore on a second device, refund and offline cases verified on
  device with Play license testers.

### P0-11.3 Analytics and crash reporting

- **Crash reporting:** Firebase Crashlytics (the Firebase project already
  exists for App Distribution).
- **Product analytics:** PostHog (already in Ravi's toolchain) or Firebase
  Analytics. Pick one. Anonymous ID, no PII, no syllabus content beyond
  chapter IDs.
- **Minimum events:**
  - `onboarding_started`, `onboarding_completed` (with mode and attempt type)
  - `plan_generated`
  - `block_started` / `block_completed` / `block_missed` / `block_moved` / `block_dropped`
  - `chapter_learned`, `revision_completed`
  - `mock_logged`
  - `paywall_viewed`, `purchase_started`, `purchase_completed`, `restore_completed`
  - `widget_added`
  - `export_done`
- Settings → "Share anonymous usage data" toggle, on by default, disclosed in
  onboarding and the privacy policy.
- **Privacy policy and Data safety form must match exactly.** Today's copy says
  "local-only", which becomes false once analytics ships.

### P0-11.4 Play policy and listing

- **Target API:** confirm Play's current requirement for new apps at upload
  time. The app is at `targetSdk 35`; bump if required.
- **Permissions after removing Focus Lock:**
  - `POST_NOTIFICATIONS`, the foreground service type for the timer, and
    possibly `SCHEDULE_EXACT_ALARM`.
  - Drop `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW` and
    `FOREGROUND_SERVICE_SPECIAL_USE`.
- **Target audience:** many users are 16–18, i.e. minors.
  - Complete the target-audience and content declarations honestly.
  - Do not target under-13s.
  - No ads, which keeps the Families/ads constraints moot.
  - Analytics must not collect identifiers beyond what is allowed for teen
    audiences. Verify against current policy.
- Data safety form, content rating questionnaire, privacy policy URL (host it
  on the same infra as the Steadyline privacy site), and contact email.
- **Store listing (ASO), English + Hindi:**
  - Target terms: NEET planner, NEET study planner, NEET timetable, NEET
    syllabus tracker, NEET revision, NEET countdown, NEET 2027.
  - Screenshots of Today, feasibility, revision and the widget.
  - A short promo video is optional.

### P1-11.5 Web companion for distribution (search-led)

- **Free web tools on a site you control:**
  - NCERT chapter-wise checklist (printable);
  - NEET countdown;
  - "Can I finish the syllabus by X?" calculator using the same domain math;
  - chapter-wise PYQ weightage pages, with sources, built from §4.2 data.
- Each tool links to the Play listing with UTM-equivalent referrer tracking.
- Reuse the Steadyline SEO process (`project-sam/SEO_EXPERIMENTS.md` method:
  baseline, change, 28-day review).

---

## 12. Release engineering and verification gates

### P0-12.1 Build

- Signed release AAB with R8, using an upload key and Play App Signing.
  - Store keystore backups in two places. Never commit them.
- Baseline Profile plus a `:macrobenchmark` module (cold start → Today;
  onboarding start → finish), per ARCHITECTURE.md §3.
- Budgets (ARCHITECTURE.md §8): cold start under 500 ms on a mid-range device;
  release APK/AAB download under 15 MB.
- `versionCode` / `versionName` policy: monotonically increasing, tagged in
  git.

### P0-12.2 Automated tests (must pass)

- **Domain:**
  - planner, for both modes and all §4.4 triggers;
  - feasibility;
  - calibration;
  - revision interval table;
  - every §9 metric;
  - the cross-screen consistency test.
- **Data:**
  - Room migrations from every distributed schema (Firebase builds 0.1.x,
    with fixtures);
  - syllabus version migration;
  - export/import round-trip.
- **Billing:** entitlement state machine (purchase, restore, revoke, offline).
- **UI:** Compose tests for onboarding (both modes), Today start → Focus →
  complete → outcome, and the paywall.

### P0-12.3 Physical device matrix (Ravi, on device)

| Case | Device(s) |
|---|---|
| Clean install → onboarding (deadline + steady) → first Focus | Own phone + 1 low-end (≤4 GB RAM) |
| 50-min Focus with screen off; app swiped away; reboot mid-session | Own phone + 1 aggressive-OEM device |
| Upgrade from last Firebase build without data loss | Own phone |
| Airplane mode, full day of use | Any |
| TalkBack walk-through of the core loop; font scale 200% | Any |
| 12h/24h clock, Hindi system locale (layout survives) | Any |
| Purchase / restore on second device / refund (license tester) | 2 devices |
| Widget add, update, Start deep link | Own phone |
| Export → uninstall → reinstall → import | Own phone |

### P0-12.4 Rollout

1. Internal testing track: Ravi + 5–10 testers, 7 days, zero P0 bugs.
2. Closed testing with 20+ NEET aspirants for 14 days. Personal Play developer
   accounts created after Nov 2023 must run a closed test with ≥12 testers for
   ≥14 days before production; confirm the current rule.
3. Production at a staged rollout of 20% → 50% → 100%, watching crash-free
   sessions (target ≥99.5%) and ANRs.
4. Write rollback notes: the previous AAB, and how to halt the rollout.

### 12.5 Go / No-Go checklist

- [ ] All P0 items done and verified
- [ ] All P1 items done, or explicitly moved to V1.1 in this file with a reason
- [ ] No visible stub, fake number or unlabelled estimate
- [ ] Cross-screen consistency test green
- [ ] Device matrix complete with notes
- [ ] Privacy policy = Data safety = actual behaviour
- [ ] Billing verified with license testers
- [ ] Analytics events visible in the dashboard from the closed test
- [ ] Store listing live in English + Hindi

---

## 13. Validation in parallel (not code)

### 13.1 10 student interviews before Phase 3 freezes

Spread: Kota classroom ×2, home-city coaching ×2, online-only (PW) ×2,
self-study ×1, droppers ×3. Use `docs/research/neet-questions.md` §3–§7.

**Must answer:**
- real self-study hours;
- what they track today;
- whether they'd log mock scores;
- deadline vs steady preference;
- price reaction to ₹249/yr and ₹499 lifetime.

### 13.2 Closed-test metrics that decide what happens after V1

| Metric | Target to continue | Kill/rethink signal |
|---|---|---|
| Onboarding completion | ≥70% | <50% |
| D1 / D7 retention | ≥40% / ≥20% | <25% / <10% |
| Blocks completed per active day | ≥2 | <1 |
| Paywall view → purchase (production, 30 days) | ≥2% | <0.5% |
| Organic installs per week (production, weeks 1–6) | Growing week over week | Flat or declining |

These thresholds are judgement calls, not benchmarks. Lock them before launch
and don't move them afterwards.

---

## 14. Execution order

| Phase | Contents | Exit |
|---|---|---|
| **A. Foundation** | §0, §11.1 identity, §9 `Metrics.kt` + consistency test, §6.2 time accounting, §7.1 revision unify, §5.2 syllabus verify, dead-code removal, remove Focus Lock + permissions | Consistency test green; one number per concept |
| **B. Planner** | §3.2 exam vs finish-by, §4.1 calibration, §4.3 block types + revision phase, §4.4 replan rules incl. no past blocks, §3.4 honest feasibility | Planner tests for both modes green |
| **C. Onboarding + tracking** | §3.1 modes, §3.3 starting point, §5.1 stages, §5.3 backlog, §4.5 class log | Onboarding ≤3 min on device |
| **D. Focus + revision + tests** | §6.1 reliability, §6.3 manual entry, §6.4 lengths, §7.2 revision blocks, §8.1–8.2 mocks + mistakes | Device timer matrix passes |
| **E. Platform** | §10.1 notifications, §10.3 widget, §10.4 backup + restore | Export/import round-trip on device |
| **F. Business** | §11.2 billing + paywall, §11.3 analytics + Crashlytics, privacy + Data safety | License-tester purchase/restore pass |
| **G. Data** | §4.2 PYQ weightage dataset + UI (can run in parallel from Phase A) | Every chapter sourced or "no data" |
| **H. Release** | §12 build, tests, device matrix, internal → closed → production; §11.4 listing; §11.5 web tools | Go/No-Go all green |

**Rules for any agent executing this plan:**
- Follow `ARCHITECTURE.md`. No emulator, no adb.
- Claim only what the build and tests prove.
- One phase per branch.
- Update the checkboxes in this file with commit hashes as items land.

## Progress log

- 2026-10-07 · Phase A2 · §9 Metrics.kt + consistency test, §6.2 time accounting, §7.1 revision unify · 01653b0
