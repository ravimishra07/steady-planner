# Android Release Readiness Plan

**Product:** NEET Planner / Steadyline

**Shipping code:** `android/`

**Prototype status:** `prototype/` is visual reference only and is not part of this release plan.

**Audit baseline:** 4 September 2026, current local working tree

**Current decision:** **NO-GO for production release**

## Implementation update — 4 September 2026

The production-code pass is substantially complete. The release remains **NO-GO** until the device, upgrade, accessibility, performance, signing, Play-policy, and internal-track gates below are executed with the actual release artifact.

Implemented in `android/`:

- Canonical Room-backed Today → Focus → completion → syllabus/revision/Insights flow with stable NEET IDs; legacy stores are retained only for migration and final cleanup.
- Idempotent legacy import that preserves valid NEET plan/history instead of deleting it, plus exported Room schemas and explicit migrations through schema 4.
- Workload-weighted rolling 21-day generation, target-date cap, preservation of manual/history blocks, remaining-minute accounting, exclusions, deterministic refill, and real Organise preview/order behavior.
- Durable Focus expiry in foreground and after process relaunch, coarse checkpoints, partial-save/discard choices, and post-session Struggled/Okay/Strong revision calibration.
- Monday-first Today, validated placement, calm missed-work recovery, useful-gap threshold, Start-now hierarchy, expandable cards, stable-ID topic picker, and explicit Learn/Practice/Revise/Mock input.
- Truthful planned/completed/extra-study analytics, target-pace subject status, forecast evidence threshold/ranges, and a concise first-use CTA instead of a fake dashboard.
- Saved-state hydration for onboarding edits, process-restorable onboarding drafts, Android Back handling, and removal of the unreachable exam step.
- Canonical Syllabus progress, next-unfinished Play behavior, accessible action sizes, genuine Organise preview, and explicit Settings save/error/unsaved-change behavior.
- Focus Lock service gating, lower polling frequency, accurate privacy copy, and backup/device-transfer exclusion rules.

Still required before **GO**:

- Physical-device clean-install, upgrade, process-death, Focus Lock/OEM, TalkBack, font-scale, 12/24-hour, battery, and backup/transfer verification.
- Migration fixtures from every previously distributed Firebase build and failure-injection/instrumentation coverage.
- Compose accessibility/navigation tests, baseline profile and macrobenchmark evidence.
- Signed release AAB, R8/release-only verification, final application-ID/signing decision, Play Data safety and `specialUse` review material, internal testing publication, artifact hash, and rollback notes.

This document is the release contract for the first production Android release. A checked box must mean that the behavior is implemented in the shipping app, covered by appropriate tests, and verified on a physical Android device where device behavior matters.

## 1. Definition of release-ready

The app is release-ready only when all of the following are true:

- [ ] Every **P0 release blocker** in this document is completed and verified.
- [ ] Every **P1 core-product requirement** is completed, or explicitly removed from the production UI.
- [ ] No visible control is a stub, preview, hardcoded value, or false affordance.
- [ ] Today, Syllabus, Focus, Insights, and Settings agree about the same plan and progress.
- [ ] A real existing installation can upgrade without losing data.
- [ ] The app maintains a useful plan beyond day seven and toward the target date.
- [ ] A student can open the app and begin the right work without first repairing the schedule.
- [ ] Privacy copy, Android backup behavior, permissions, and Play declarations agree.
- [ ] Unit, integration, Compose UI, accessibility, upgrade, and physical-device release gates pass.
- [ ] The signed release build meets the architecture performance and size budgets.

### Status labels

| Label | Meaning |
|---|---|
| **P0 — Release blocker** | Data loss, corrupted truth, broken central loop, false privacy behavior, or a failure that prevents safe production release. |
| **P1 — Required for V1** | The app may run, but the core student experience is incomplete, misleading, or unnecessarily difficult. |
| **P2 — Quality hardening** | Accessibility, resilience, consistency, performance, and polish required before the final production sign-off. |
| **Stub / preview** | Visible UI exists but the promised behavior is absent, temporary, hardcoded, or routed incorrectly. Implement it fully or remove it. |
| **Device verification** | Source intent exists, but build/test/device evidence is still required. |

## 2. Current feature status

| Area | Current status | Production assessment |
|---|---|---|
| Onboarding | Production code substantially implemented | Create/edit hydration, draft restoration, Android Back, Monday-first defaults, and canonical planning are implemented. Existing-coverage baseline and device validation remain. |
| Today | Production code substantially implemented | Canonical IDs/data, recovery, Start hierarchy, validation, expandable details, actionable-gap behavior, and Monday-first calendar are implemented. Device/UI automation remains. |
| Syllabus | Implemented visually | Not release-ready: progress identity can disagree, parent Play can start the wrong unit, filters/tri-state actions need truth and accessibility hardening. |
| Organise | Implemented, verification pending | Default/Shortest affects generation, fake Custom was removed, preview is regenerated, and the impact count is real. Transaction/failure device tests remain. |
| Focus timer | Implemented, verification pending | Natural expiry, relaunch reconciliation, partial save, discard, coarse persistence, and adaptive outcome input are implemented. Background/device matrix remains. |
| Focus Lock | Implemented but device-unverified | Service start is gated by enabled/configured/capable state and ordinary timers do not start it. Battery, overlay, OEM, and Play-policy validation remain. |
| Insights | Production code substantially implemented | Canonical sources, truthful denominators, forecast ranges/evidence, target refill, and concise first-use state are implemented. Chart/device cases remain. |
| Settings | Production code substantially implemented | Visible stubs/demo controls are removed, coverage/version are real, and preference saves regenerate the plan. Device and storage-failure verification remain. |
| Persistence and migration | Canonical runtime implemented | Room is the core-loop source and legacy import is idempotent/recoverable. Distributed-build fixtures and physical upgrade evidence remain release blockers. |
| Testing and release engineering | Unit/lint/debug-build stage | Domain/feature tests and schema export exist. Compose UI/accessibility, migration instrumentation, benchmarks, signed release, and physical gates remain. |

## 3. P0 — Release blockers

### 3.1 Safe upgrade and migration

**Problem:** Migration treats a legacy plan as invalid when a matching Room attempt does not already exist, then clears the plan, syllabus, and study history.

**Why it matters:** A production upgrade must never erase a genuine student's preparation history merely because the new database has not been populated yet.

**Evidence:** [`MigrationRepository.kt`](../android/core/data/src/main/kotlin/com/exam/assistant/core/data/repo/MigrationRepository.kt#L27), [`SteadylineApp.kt`](../android/app/src/main/kotlin/com/exam/assistant/SteadylineApp.kt#L16)

- [ ] Define every supported legacy on-disk state from previous distributed builds.
- [ ] Migrate a valid legacy NEET plan into Room instead of deleting it.
- [ ] Preserve completed sessions, syllabus ticks, target date, hours, commitments, coaching, appearance, and Focus state where valid.
- [ ] Perform migration transactionally or through an idempotent staged migration.
- [ ] Mark migration complete only after all required writes succeed.
- [ ] Leave recoverable legacy data intact when migration fails.
- [ ] Add fixtures representing actual previous app versions.
- [ ] Add upgrade tests for success, interrupted migration, corrupted fields, repeated launch, and rollback-safe failure.
- [ ] Verify a real upgrade from the latest Firebase-distributed build on a physical phone.

**Acceptance:** All supported legacy fixtures upgrade without losing valid data; repeated migration is a no-op; injected failure never leaves a half-migrated user.

### 3.2 One canonical data model and stable identifiers

**Problem:** Today uses legacy stores and positional `t1_*` identifiers, while Syllabus and Insights use Room and stable ExamPack IDs. The manual NEET picker still maps section indexes to obsolete CGL subject IDs.

**Why it matters:** The same completed work can appear complete in one tab and incomplete in another. This destroys trust in progress, revision, and forecasting.

**Evidence:** [`AppContainer.kt`](../android/app/src/main/kotlin/com/exam/assistant/AppContainer.kt#L69), [`HomeViewModel.kt`](../android/feature/home/src/main/kotlin/com/exam/assistant/feature/home/HomeViewModel.kt#L75), [`HomeStudyContent.kt`](../android/feature/home/src/main/kotlin/com/exam/assistant/feature/home/HomeStudyContent.kt#L339), [`StudySession.kt`](../android/domain/src/main/kotlin/com/exam/assistant/domain/StudySession.kt#L91)

- [ ] Make Room repositories plus stable ExamPack IDs the only runtime source of truth.
- [ ] Remove live reads from legacy `SyllabusStore` and `StudySessionStore` after migration.
- [ ] Remove positional `t1_*` node IDs from production flows.
- [ ] Remove obsolete `quant`, `reasoning`, `ga`, and `english` mappings from the NEET app.
- [ ] Use canonical NEET subject and node IDs for manual, generated, syllabus-started, revision, and Focus sessions.
- [ ] Create one completion operation that updates session, plan block, topic progress, and revision state atomically.
- [ ] Create one atomic skip operation and one atomic reschedule operation.
- [ ] Remove compatibility double-writes after verified migration.
- [ ] Add cross-screen tests: start in Today -> finish in Focus -> Today, Syllabus, Insights, and revision all agree.

**Acceptance:** A single stable session and node identity can be followed through every screen and database table; no feature-specific compatibility mapping remains.

### 3.3 Durable Focus completion

**Problem:** `FocusStore.load()` converts an expired running timer to `DONE` before `FocusViewModel` checks whether it should commit completion. The natural-expiry completion path is therefore unreachable.

**Why it matters:** A student can finish a full session and receive no durable credit, syllabus progress, or scheduled revision.

**Evidence:** [`FocusStore.kt`](../android/core/data/src/main/kotlin/com/exam/assistant/core/data/FocusStore.kt#L37), [`FocusViewModel.kt`](../android/feature/focus/src/main/kotlin/com/exam/assistant/feature/focus/FocusViewModel.kt#L68)

- [ ] Preserve access to the raw persisted RUNNING state and deadline.
- [ ] Implement one idempotent `completeIfExpired` operation at the data/domain boundary.
- [ ] Persist completion exactly once when the timer expires in foreground.
- [ ] Persist completion when the app is backgrounded during expiry.
- [ ] Persist completion after process death and app relaunch.
- [ ] Prevent duplicate completion, duplicate revision creation, and duplicate progress increments.
- [ ] Save timer state on lifecycle transitions and coarse checkpoints, not every second.
- [ ] Add tests for foreground expiry, background expiry, process restart, pause/resume, extension, duplicate callback, and storage failure.

**Acceptance:** Every natural timer expiry updates all canonical state exactly once, including after process death.

### 3.4 Rolling target-date planner

**Problem:** Onboarding and Organise generate exactly seven days, while the UI presents a plan toward the exam/target date. No rolling refill exists.

**Why it matters:** Day eight can be empty even though the product says it built an exam plan.

**Evidence:** [`OnboardingViewModel.kt`](../android/feature/onboarding/src/main/kotlin/com/exam/assistant/feature/onboarding/OnboardingViewModel.kt#L361), [`OrganiseViewModel.kt`](../android/feature/syllabus/src/main/kotlin/com/exam/assistant/feature/syllabus/OrganiseViewModel.kt#L146)

- [ ] Define a rolling planning horizon, recommended 14–21 days.
- [ ] Replenish the horizon on onboarding completion, app/date change, completion, skip, reschedule, syllabus edit, hour change, and target-date change.
- [ ] Stop generation at the target date.
- [ ] Preserve completed, running, manually added, and manually moved work.
- [ ] Exclude completed and explicitly excluded syllabus leaves.
- [ ] Never duplicate an already scheduled remaining-work allocation.
- [ ] Make replenishment idempotent.
- [ ] Show a safe empty/recovery state if no feasible future slot exists.
- [ ] Add day-eight, day-21, target-date, timezone/date-boundary, and repeated-refill tests.

**Acceptance:** Opening any date inside the maintained horizon shows a valid plan or an explicit feasibility decision; generated work never silently stops after one week.

### 3.5 Workload-weighted scheduling

**Problem:** The initial scheduler gives each leaf one block and wraps with modulo, ignoring the leaf's estimated remaining minutes.

**Why it matters:** A short unit and a multi-hour unit receive equivalent treatment, so the coverage promise and generated day plan are mathematically disconnected.

**Evidence:** [`InitialPlan.kt`](../android/domain/src/main/kotlin/com/exam/assistant/domain/InitialPlan.kt#L28)

- [ ] Maintain a remaining-minutes ledger for every included stable leaf.
- [ ] Subtract completed focused effort using a documented rule.
- [ ] Split large workloads into reasonable sessions.
- [ ] Do not repeat a leaf after its remaining learn workload reaches zero.
- [ ] Keep LEARN, QUESTIONS/PRACTICE, REVISION, and MOCK TEST as explicit activity types.
- [ ] Document how estimated hours translate into completion and coverage.
- [ ] Balance subjects without ignoring workload or student priority.
- [ ] Add tests for tiny topics, multi-session topics, partial completion, exclusions, covered topics, insufficient capacity, and exact total-minute reconciliation.

**Acceptance:** Scheduled learn minutes reconcile with remaining estimated syllabus work, within a documented rounding tolerance.

### 3.6 Truthful progress and forecasting

**Problem:** Half-hour targets are truncated, extra study is included in planned minutes, subject “ahead/behind” is relative to overall coverage rather than required pace, and exact finish dates can appear after only three active days.

**Why it matters:** Students may make consequential study decisions from false precision.

**Evidence:** [`TodaySchedule.kt`](../android/domain/src/main/kotlin/com/exam/assistant/domain/TodaySchedule.kt#L93), [`StudyAnalytics.kt`](../android/domain/src/main/kotlin/com/exam/assistant/domain/StudyAnalytics.kt#L125), [`StudyAnalytics.kt`](../android/domain/src/main/kotlin/com/exam/assistant/domain/StudyAnalytics.kt#L234)

- [ ] Preserve half-hour capacity as minutes; never truncate 4.5h to 4h.
- [ ] Separate planned minutes, completed planned minutes, extra study, and fixed time.
- [ ] Calculate target pace from remaining workload and days to target.
- [ ] Define “ahead,” “on track,” and “behind” against target pace, not another subject's percentage.
- [ ] Use ranges for early forecasts.
- [ ] Show sample size and observation window beside forecasts.
- [ ] Define the minimum evidence required before a pace forecast is shown.
- [ ] Add tests for no history, sparse history, extra study, partial sessions, timezone changes, target changes, excluded syllabus, and half-hour targets.

**Acceptance:** Every number has a documented numerator, denominator, time window, and empty-state behavior; changing the same input changes Today and Insights consistently.

### 3.7 Privacy and backup truth

**Problem:** The app says everything stays on the phone and nothing is sent to a server, but Android backup is enabled and no extraction rules are configured.

**Why it matters:** Platform backup can move app data through a backup transport. Privacy copy must match actual behavior.

**Evidence:** [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml#L25), [`strings.xml`](../android/feature/settings/src/main/res/values/strings.xml#L109), [Android Auto Backup documentation](https://developer.android.com/identity/data/autobackup)

- [ ] Decide whether study plan/history data should participate in cloud backup, device-to-device transfer, both, or neither.
- [ ] Configure `allowBackup`, `fullBackupContent`, and `dataExtractionRules` consistently for supported Android versions.
- [ ] Exclude sensitive or nonportable state where required.
- [ ] Rewrite Privacy copy to describe OS backup accurately.
- [ ] Complete the Play Data safety form from verified code and dependencies.
- [ ] Test backup/restore and device-to-device transfer behavior without exposing real user data.

**Acceptance:** Manifest, backup rules, privacy page, and Play disclosures make the same verifiable claim.

## 4. P1 — Core student experience

### 4.1 Today must answer “What should I do next?”

- [ ] Make the next actionable task the strongest element on Today.
- [ ] Always expose **Start** or **Continue** for an actionable task.
- [ ] A missed task must still offer **Start now**; Reschedule cannot replace the primary study action.
- [ ] Show the reason for the recommendation: scheduled next, revision due, or chosen priority.
- [ ] Keep Edit plan secondary.
- [ ] Display completed/planned study and next fixed commitment compactly.
- [ ] Use one canonical computation for fixed time; overlapping school/lunch intervals must be counted once.
- [ ] Verify the screen initially positions around “now.” If automatic positioning is unreliable, collapse earlier blocks behind **Earlier today**.
- [ ] Add visible loading, corruption recovery, retry, and no-plan states instead of rendering a blank screen.

**Evidence:** [`HomeTimeline.kt`](../android/feature/home/src/main/kotlin/com/exam/assistant/feature/home/HomeTimeline.kt#L118), [`HomeScreen.kt`](../android/feature/home/src/main/kotlin/com/exam/assistant/feature/home/HomeScreen.kt#L114)

### 4.2 Recovery when the day changes

**Required behavior:** The app absorbs scheduling complexity; the student should not repair every missed card manually.

- [ ] Detect when one or more earlier blocks are missed.
- [ ] Collapse missed work into one calm recovery card.
- [ ] Show remaining usable time today, calculated from unioned fixed commitments.
- [ ] Primary action: **Replan the rest of today**.
- [ ] Secondary action: **Start next task now**.
- [ ] Tertiary action: inspect individual missed items.
- [ ] Preserve completed, running, and manually pinned work during replan.
- [ ] Move overflow explicitly and tell the student what moved.
- [ ] Avoid guilt, streak-loss, failure, or alarm language.
- [ ] Add tests for one missed task, many missed tasks, no remaining capacity, fixed-time overlap, near-bedtime recovery, and historical dates.

**Evidence:** Missed cards currently replace Start with Reschedule in [`HomeTimeline.kt`](../android/feature/home/src/main/kotlin/com/exam/assistant/feature/home/HomeTimeline.kt#L515).

### 4.3 Validated scheduling and rescheduling

- [ ] Centralize placement rules in one domain service.
- [ ] Validate against fixed commitments, other sessions, wake/sleep, date boundaries, target date, and minimum duration.
- [ ] Never fall back to an occupied original time when no slot exists.
- [ ] Never create a past session from a historical selected date.
- [ ] Separate **Start now** from **Add to plan**.
- [ ] Handle midnight rollover explicitly; never pass hour 24 to a time picker.
- [ ] Keep a sheet/dialog open while saving and show failure without losing the student's choice.
- [ ] Add collision, past-date, midnight, no-space, storage-failure, and concurrent-change tests.

**Evidence:** [`HomeViewModel.kt`](../android/feature/home/src/main/kotlin/com/exam/assistant/feature/home/HomeViewModel.kt#L251)

### 4.4 Useful gaps, not pressure in every empty minute

- [ ] Define a minimum actionable study gap, recommended 25–30 minutes.
- [ ] Render shorter windows as **Break** or **Transition** without an Add CTA.
- [ ] Keep long free windows actionable.
- [ ] Do not describe the entire awake interval as “free”; distinguish fixed time, planned study, necessary breaks, and genuinely unallocated time.
- [ ] Validate threshold and language with students on small phones.

**Evidence:** Every gap is currently clickable in [`HomeTimeline.kt`](../android/feature/home/src/main/kotlin/com/exam/assistant/feature/home/HomeTimeline.kt#L655).

### 4.5 Add work at the student's natural level

- [ ] Allow selection at subject, chapter, topic, and subtopic levels.
- [ ] Separate **Study this** from **Explore deeper**.
- [ ] Resolve a broad selection to the next unfinished included leaf, while showing what the app chose.
- [ ] Ask activity type: **Learn**, **Questions**, **Revise**, or **Mock test**.
- [ ] Support a custom task when it does not map cleanly to the syllabus.
- [ ] Suggest duration from remaining workload and available time; allow editing.
- [ ] Reuse the same picker and identity model in Today, Syllabus, and Focus.
- [ ] Add accessibility and navigation tests for every hierarchy level.

**Evidence:** Parent rows currently only drill down in [`HomeStudyContent.kt`](../android/feature/home/src/main/kotlin/com/exam/assistant/feature/home/HomeStudyContent.kt#L339).

### 4.6 Study-card details

- [ ] Make every study card open details, including leaf nodes and revisions.
- [ ] Show subject/chapter/topic breadcrumb.
- [ ] Show status text: Upcoming, Studying now, Missed, Completed, Moved, or Skipped.
- [ ] Do not encode status only through color or a warning triangle.
- [ ] Show Start/Continue, Move, Skip, Mark complete, and details according to state.
- [ ] Keep only one expanded card at a time on small screens.
- [ ] Expose expanded/collapsed semantics to accessibility services.

**Evidence:** Cards are clickable only when subtopics exist or the session is complete in [`HomeTimeline.kt`](../android/feature/home/src/main/kotlin/com/exam/assistant/feature/home/HomeTimeline.kt#L392).

### 4.7 Focus session truth

- [ ] On early end, make **Save X min and end** the primary action.
- [ ] Keep **Discard session** explicit and destructive.
- [ ] Decide when partial focused time can complete a planned block versus merely record progress.
- [ ] Use one duration model across Today and standalone Focus.
- [ ] When no planned work exists, let the student choose from the full syllabus or start a custom free-focus task.
- [ ] Prevent Start from appearing before persisted Focus state finishes loading.
- [ ] Preserve the active session across rotation, backgrounding, process death, and navigation.

**Evidence:** Early stop currently abandons the session in [`FocusViewModel.kt`](../android/feature/focus/src/main/kotlin/com/exam/assistant/feature/focus/FocusViewModel.kt#L202).

### 4.8 Adaptive revision without false authority

- [ ] Use the canonical `RevisionState` instead of the parallel fixed three-day legacy suggestion path.
- [ ] Ask for one lightweight outcome after relevant study: **Struggled**, **Okay**, or **Strong**.
- [ ] Schedule the next suggested review from result/history, with safe bounds.
- [ ] Keep the wording **Suggested review** until validation supports stronger personalization claims.
- [ ] Make revision due state and Insights use the same repository.
- [ ] Explain why a review is due and allow snooze/move without losing it.
- [ ] Add tests for first learn, repeated review, poor outcome, strong outcome, missed review, and disabled auto-review.

**Evidence:** The live legacy suggestion path uses a fixed interval in [`StudySession.kt`](../android/domain/src/main/kotlin/com/exam/assistant/domain/StudySession.kt#L61), while normalized revision state already models increasing intervals in [`TopicProgress.kt`](../android/domain/src/main/kotlin/com/exam/assistant/domain/TopicProgress.kt#L75).

## 5. Feature completion checklist

### 5.1 Onboarding

- [ ] Keep the current calm visual direction and concise copy.
- [ ] Confirm Monday-first day display everywhere while preserving correct stored weekday values.
- [ ] Keep seeded lunch/dinner all week and new non-meal commitments Monday–Saturday by default.
- [ ] Keep overlapping fixed commitments valid and merge them for availability.
- [ ] Distribute the latest local overlap-validation fix; it was not included in the last recorded Firebase build.
- [ ] Add explicit **Create plan** and **Edit existing plan** modes.
- [ ] In edit mode, hydrate every saved field before showing editable controls.
- [ ] Preserve fields the student does not change.
- [ ] Exclude already-covered leaves during every replan path.
- [ ] Add an optional existing-coverage baseline: Starting fresh / Partly covered / Mostly revision.
- [ ] Let repeaters mark whole chapters covered quickly and refine later.
- [ ] Persist onboarding draft state or restore it after process death.
- [ ] Make Android system Back move to the previous onboarding step.
- [ ] Remove the unreachable Exam step or make the intended path explicit.
- [ ] Verify small-screen scrolling, keyboard behavior, dynamic type, contrast, long labels, and bottom-button clearance.

### 5.2 Syllabus

- [ ] Read and write only canonical stable IDs and canonical progress.
- [ ] Subject Play must start the next included unfinished leaf, not the first chapter.
- [ ] Label the subject CTA with what will start, for example **Continue: Cell Cycle**.
- [ ] Rename “Due” to “Not covered” unless it is backed by actual revision due dates.
- [ ] Confirm large bulk completion/uncompletion changes.
- [ ] Implement correct tri-state semantics and labels: Mark done / Partly done / Mark not done.
- [ ] Keep excluded/parked state consistent with Today and Insights.
- [ ] Ensure 48dp non-overlapping targets for status and Play actions.
- [ ] Add search, filter, expansion, completion, exclusion, and TalkBack UI tests.

### 5.3 Organise

- [ ] Decide whether ordering is a V1 feature.
- [ ] If included, persist Default/Shortest/Custom priority and make it affect generation.
- [ ] Implement actual Custom reorder interaction, or remove the Custom option.
- [ ] Recalculate a genuine proposed week before Apply.
- [ ] Label the existing view **Current week** until a draft has been calculated.
- [ ] Show exact impact: chapters added/removed, hours added/removed, first affected date, and moved blocks.
- [ ] Replace approximate “changes” count with actual operations.
- [ ] Apply syllabus, priority, and future-plan changes transactionally.
- [ ] Preserve completed, running, and manual sessions.
- [ ] Surface save failure without dismissing the draft.

### 5.4 Insights

- [ ] Use canonical session/progress/revision sources only.
- [ ] Implement the truthful metric definitions in section 3.6.
- [ ] Replace the large first-use static mock with a concise empty state.
- [ ] Empty-state primary CTA: **Start next session**; secondary: **Go to Today**.
- [ ] Remove noninteractive period pills and hardcoded `4h target` from the empty state.
- [ ] Make “Manage target” use the Organise transaction and regenerate the future plan.
- [ ] Show forecast ranges and evidence window.
- [ ] Explain insufficient evidence without implying failure.
- [ ] Fix “Focus sitting” to “Focus session.”
- [ ] Verify charts with zero, sparse, large, and mixed planned/extra data.

### 5.5 Settings

- [ ] Replace hardcoded `0% covered` with canonical coverage or remove it.
- [ ] Remove reminder switches until real, or implement persisted notification schedules.
- [ ] If reminders ship, handle notification permission, reboot, timezone, date change, denial, and cancellation.
- [ ] Route Focus Shield to actual Focus Lock setup.
- [ ] Route revision settings to real revision controls or remove the row.
- [ ] Make hour changes show impact and atomically regenerate future automatic work.
- [ ] Keep demo-data generation behind a debug/developer build flag.
- [ ] Populate About version from `BuildConfig.VERSION_NAME`.
- [ ] Ensure settings wait for saved state before accepting edits.
- [ ] Make clear/restart copy enumerate what is deleted and what cannot be recovered.
- [ ] Complete privacy and backup work from section 3.7.

### 5.6 Focus Lock

- [ ] Start the foreground service only when Focus Lock is enabled, configured, and required permissions/capabilities are ready.
- [ ] Never show “Focus Lock is active” when no apps are being blocked.
- [ ] Stop the service immediately when the session ends or blocking is disabled.
- [ ] Avoid polling or persistent work for ordinary timer-only sessions.
- [ ] Profile the current 800ms polling approach for battery and responsiveness.
- [ ] Verify overlay behavior across supported Android versions and major OEMs.
- [ ] Verify temporary allowance, return-to-study, pause, app restart, and service restart behavior.
- [ ] Ensure the user can always stop the session and disable blocking.
- [ ] Prepare the Android 14+ `specialUse` foreground-service declaration for Play review.
- [ ] Ensure notification copy and channel behavior are accurate.

**References:** [`FocusLockService.kt`](../android/app/src/main/kotlin/com/exam/assistant/focuslock/FocusLockService.kt#L53), [Android foreground-service requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required)

## 6. P2 — Accessibility, resilience, and performance

### 6.1 Accessibility

- [ ] Every interactive target has an explicit non-overlapping minimum of 48dp.
- [ ] Calendar days announce full date, selected state, and study status.
- [ ] Expandable controls announce expanded/collapsed state.
- [ ] Warning, missed, completed, moved, and revision-due states have text semantics.
- [ ] Adjacent Syllabus status and Play targets do not overlap invisibly.
- [ ] Reading and focus order follow visual order.
- [ ] Dynamic type does not clip titles, buttons, time ranges, charts, or bottom navigation.
- [ ] Contrast is checked for every accent/background combination and disabled state.
- [ ] TalkBack and Switch Access passes are completed on a physical device.
- [ ] Compose semantics tests cover the calendar, Today cards, picker hierarchy, Syllabus tree, timer, dialogs, and settings switches.

**Reference:** [Android Compose accessibility semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) and [minimum touch-target guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

### 6.2 Loading, failure, and process death

- [ ] Startup cannot remain on splash forever when a store read fails.
- [ ] Startup uses supervised reads, safe recovery defaults, and an explicit recovery path.
- [ ] Home, Syllabus, Focus, Insights, Organise, and Settings expose loading and retryable error states.
- [ ] No screen shows editable defaults before saved state has loaded.
- [ ] Onboarding and Organise drafts survive expected process recreation or warn before loss.
- [ ] Storage failures never dismiss unsaved user input.
- [ ] Corrupted local state has a safe repair path that avoids deleting unrelated valid history.

### 6.3 Performance and battery

- [ ] Replace or justify full-day `verticalScroll` composition for a growing timeline.
- [ ] Profile Home after months of history and a rolling 21-day plan.
- [ ] Avoid filtering the entire history repeatedly on the main thread.
- [ ] Stop writing DataStore and Room every timer second.
- [ ] Profile Focus Lock CPU, battery, overlay latency, and service lifetime.
- [ ] Add a Baseline Profile and Macrobenchmark module.
- [ ] Measure cold start on a representative mid-range Android device.
- [ ] Measure Today/Syllabus scrolling and navigation frame times.
- [ ] Confirm release APK size after R8/resource shrinking.

**Architecture budgets:** cold start under 500ms, frames under 16ms, release APK under 15MB.

### 6.4 Localization and consistency

- [ ] Make Home Monday-first to match onboarding and the intended Indian student mental model.
- [ ] Confirm locale-safe dates, times, pluralization, and 12/24-hour display.
- [ ] Decide whether English-only is acceptable for V1 and document it explicitly.
- [ ] Ensure screen labels consistently use Today, Syllabus, Focus, Insights/Progress, and Settings.
- [ ] Use one duration vocabulary and one activity vocabulary throughout.

## 7. Research-backed product principles

Research informs the direction; it does not replace product validation with real NEET students.

### 7.1 Study quality over timer accumulation

Repeated retrieval can produce stronger delayed recall than repeated study, and reviews of learning techniques rate practice testing and distributed practice highly. The app should therefore help the student identify whether a block involved learning, questions, or revision and capture a lightweight outcome—not treat all focused minutes as equivalent mastery.

- [Retrieval-practice experiment — Karpicke & Roediger, 2008](https://doi.org/10.1126/science.1152408)
- [Review of effective learning techniques — Dunlosky et al., 2013](https://pubmed.ncbi.nlm.nih.gov/26173288/)

**Product implication:** Add explicit activity type and a short post-session result. Use it to guide revision; do not claim that time alone proves coverage.

### 7.2 Distributed review, not one universal interval

Spacing benefits long-term retention, but the useful gap changes with the desired retention interval. A fixed three-day rule should not be presented as personalized optimization.

- [Spacing effects in learning — Cepeda et al., 2008](https://journals.sagepub.com/doi/abs/10.1111/j.1467-9280.2008.02209.x)

**Product implication:** Begin with transparent suggested reviews and adapt from actual outcomes. Show the reason and let the student move the review.

### 7.3 Support planning, monitoring, and adjustment

Self-regulated learning guidance emphasizes planning, monitoring, and evaluating learning. The product should make these actions understandable without turning the student into the scheduler.

- [EEF Metacognition and Self-Regulated Learning guidance](https://educationendowmentfoundation.org.uk/education-evidence/guidance-reports/metacognition)

**Product implication:** Today gives the next decision, Focus records what happened, and the weekly check-in offers a small set of feasible adjustments.

### 7.4 Recovery must reduce pressure

NCERT provides examination-period support focused on coping with stress and anxiety, while WHO identifies adolescence as a formative period where supportive environments and coping skills matter. These sources do not prove that a particular UI causes or prevents distress; the UX direction below is a cautious design inference.

- [NCERT Manodarpan examination support](https://ncert.nic.in/pdf/manodarpan/StudentExaminationServices.pdf)
- [WHO adolescent mental health](https://www.who.int/news-room/fact-sheets/detail/adolescent-mental-health)

**Product implication:** Avoid shame, red-wall warnings, streak loss, and repeated administrative chores after a disrupted day. Give one calm recovery action and preserve truthful effort.

### 7.5 Core product principle

> The app absorbs scheduling complexity. The student chooses, starts, and reports what happened.

## 8. Required automated test matrix

### Data and migration

- [ ] Upgrade from every previously distributed schema/state.
- [ ] Interrupted and repeated migration.
- [ ] Cross-store failure during completion, skip, reschedule, and replan.
- [ ] No data loss after process death.
- [ ] Database schema export and migration tests enabled.

### Planner

- [ ] Day-eight and rolling-horizon refill.
- [ ] Workload exhaustion without accidental repetition.
- [ ] Partial topic remaining minutes.
- [ ] Covered/excluded topic handling.
- [ ] Insufficient capacity and no-slot behavior.
- [ ] Fixed-commitment overlap counted once.
- [ ] Replan preserves manual/completed/running work.

### Focus

- [ ] Natural expiry foreground/background/process restart.
- [ ] Exactly-once completion.
- [ ] Pause/resume/extend/end-partial/discard.
- [ ] Focus Lock enabled, disabled, missing permission, and service restart.

### UI and navigation

- [ ] Onboarding Android Back and process recreation.
- [ ] Today Start now, missed recovery, and validated reschedule.
- [ ] Broad hierarchy selection and custom task.
- [ ] Cross-screen completion consistency.
- [ ] Settings load-before-edit and transactional save.
- [ ] Empty, loading, failure, and retry states.
- [ ] TalkBack semantics and touch-target tests.

## 9. Physical-device validation matrix

No build, lint, or static inspection proves the following. Test on physical devices without using emulator evidence as the release gate.

- [ ] Fresh install and onboarding on a small/low-end device.
- [ ] Upgrade from the latest Firebase-distributed build with real test data.
- [ ] Light, dark, grey, and slate backgrounds with every supported accent.
- [ ] Font scaling at 100%, 130%, and 200%.
- [ ] 12-hour and 24-hour time formats.
- [ ] Offline first launch and ongoing use.
- [ ] Background/process-killed Focus expiry.
- [ ] Focus Lock setup, blocking, temporary allowance, pause, stop, and disable.
- [ ] Notification permission allowed and denied.
- [ ] OEM-specific behavior on at least Pixel/stock Android and one major Indian-market OEM.
- [ ] TalkBack navigation through every main route.
- [ ] Rotation/configuration changes where supported.
- [ ] Battery/CPU observation during a long Focus Lock session.
- [ ] Backup/restore behavior matches the privacy decision.

## 10. Store and release operations

- [ ] Finalize production app name, application ID strategy, icon, and versioning.
- [ ] Replace stale hardcoded version copy with `BuildConfig.VERSION_NAME`.
- [ ] Configure and protect release signing.
- [ ] Build and test the signed release variant, not only debug.
- [ ] Run all unit, integration, Compose UI, lint, baseline-profile, and benchmark gates.
- [ ] Verify R8 rules and release-only behavior.
- [ ] Confirm release APK/AAB size is within the architecture budget.
- [ ] Complete Play Data safety and privacy declarations from verified behavior.
- [ ] Complete Android 14+ foreground-service `specialUse` declaration and review material.
- [ ] Confirm screenshots and store copy do not promise unsupported personalization or guaranteed exam outcomes.
- [ ] Publish to an internal testing track first.
- [ ] Run clean-install and upgrade smoke tests from the Play-delivered artifact.
- [ ] Prepare rollback, known-issues, and support instructions.
- [ ] Record the exact commit, versionCode, versionName, artifact hash, test results, and release URL.

## 11. Final go/no-go checklist

The release owner completes this section only after all earlier required work is done.

### Product truth

- [ ] The plan continues beyond seven days and reconciles to remaining workload.
- [ ] Completion is durable and identical across Today, Syllabus, Focus, and Insights.
- [ ] Progress and forecasts have documented, tested definitions.
- [ ] Every visible control performs its stated behavior.
- [ ] Settings changes preserve user work and regenerate only affected future automatic work.

### Safety and privacy

- [ ] Existing-user upgrade cannot delete valid data.
- [ ] Completion/reschedule/replan operations cannot leave partial cross-screen state.
- [ ] Privacy copy, backup configuration, permissions, and store declarations agree.
- [ ] Focus Lock can be understood, stopped, and disabled by the user.

### Student experience

- [ ] Today clearly answers what to do next.
- [ ] Missed work can be started or recovered without repairing every card.
- [ ] Short gaps are treated as breaks, not productivity failures.
- [ ] Broad study intent can be added without forced leaf-level navigation.
- [ ] Partial genuine effort is saved truthfully.
- [ ] Revision suggestions are transparent and not presented with false precision.

### Engineering evidence

- [ ] Automated release suite passes.
- [ ] Migration and process-death tests pass.
- [ ] Compose accessibility tests pass.
- [ ] Physical-device matrix passes.
- [ ] Performance, battery, and APK-size budgets pass.
- [ ] Signed internal-track artifact passes clean-install and upgrade smoke tests.

### Release decision

- [ ] **GO:** No open P0, no visible stub, all P1 requirements complete or removed, all release gates passed.
- [ ] Release owner:
- [ ] Commit:
- [ ] Version code/name:
- [ ] Signed artifact hash:
- [ ] Internal testing URL:
- [ ] Decision date:

## 12. Explicitly non-blocking for the first offline V1

These should not expand the release scope unless product strategy changes:

- User accounts or cloud sync.
- Social feeds, leaderboards, coins, or competitive gamification.
- A full question-bank/content-delivery platform.
- AI-generated study advice.
- Cross-device web parity.
- Complex custom revision algorithms presented as scientifically optimized.

The first release can be valuable as a focused offline planner, provided its plan, progress, timer, recovery, privacy, and visible controls are completely trustworthy.
