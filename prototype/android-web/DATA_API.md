# domain/ + data/ API (for feature ports)

Verified identical to Kotlin output (1,565 lines diffed). Code against it, don't re-port logic.

## Conventions
- Data classes: `Foo({...})` → frozen obj with `.copy()`, `.equals()`. Companions = statics (`Calibration.NONE`, `CoachingBacklog.NONE`, `PyqPriorityTable.EMPTY`).
- Enums: singletons `{name, ordinal, ...}`; `Foo.entries`, `Foo.valueOf(n)`, `Foo.valueOfOrNull(n)`; compare `===`; stored as `.name`.
- Sealed: tagged objs with `type` (TimelineItem, DayTimelineEntry, PlanFitOption, ImportResult).
- Map/Set → JS Map/Set. Pair → `[a, b]`.
- Extension fns: receiver = 1st arg (`withChapterTitles(pack, titles)`, `isRegenerable(block)`, `countsAsStudy(session)`).
- Overloads `leafIds`/`totalMinutes`/`flatten` dispatch on node/subject/pack.
- `PlanInputs` has `.plan() .planFit() .chapterNeeds() .projectedFinish()`.
- Named-arg fns take one props obj: `generateRollingPlan`, `computeInsights`, repo `replenish`, `planFit`, `missedPrompt`, `dismissMissedPrompt`, `rebalanceMissed`, `startFresh`. Others positional; `undefined` = default.
- `suspend fun` → `async`.
- Time (domain/time.js): dates `'YYYY-MM-DD'`, times = minute-of-day int, DayOfWeek Mon=1, `plusDays/plusMonths`, epochMs↔date, `nowMs`. Helpers in domain/kt.js.
- Mappers: `entity.toDomain()` method; domain→entity `toEntity(...)` (or `weeklyToEntity` etc).
- Room table / DataStore file + key names = Kotlin.

## data/container.js
`import { container } from '../../data/container.js'` (singleton of `AppContainer`, lazy getters). Shell calls `container.runMigrationInBackground()` once at startup.

| Group | Fields |
|---|---|
| Stores | `settings`, `planStore`, `syllabusStore`, `focusStore`, `studySessionStore`, `stageLabelStore`, `migrationStore` |
| Services | `dispatchers`, `remoteConfig`, `analytics` (NoOp), `proRepository`, `focusController`, `focusNotifier`, `dailyReminder`, `backupRepository`, `appScope.launch(fn)` |
| Repos | `examPackRepository`, `attemptRepository`, `planRepository`, `studySessionRepository`, `topicProgressRepository`, `revisionRepository`, `chapterStageRepository`, `mockTestRepository`, `rollingPlanRepository`, `targetSyllabusRepository`, `availabilityRepository`, `studyPreferenceRepository`, `migrationRepository` |
| Request flows | `paywallRequest`, `openFocusRequest`, `openTodayRequest`, `pendingSyllabusPick`, `onboardingStartAtPlan` |

Methods: `openPaywall()`, `startPlannedBlock(id)`, `loadTodaySnapshot(today?)` → `{hasPlan, daysToExam, coveragePercent, blocks}`, `reconcileExpiredFocus()`, `onBootOrUpdate()`, `onBackupImported()`, `logOut()`, `rearmDailyReminder()`, `restartProcess()` (= reload).

Shell-rendered notifications: `focusNotifier.state` (null | `{kind:'running'|'paused', session}`), `focusNotifier.complete` (`{kind:'complete', session, withSound}`), `dailyReminder.notification` (`{title, text}`).

Key APIs:
- rollingPlanRepository: `replenish, planFit, draftFit(draft), draftProjection, projectedFinish, calibration, preview, missedPrompt, rebalanceMissed, startFresh, topUpIfNewDay, setLiveBacklogSplit`; exports `PlanDraft`, `MissedPrompt`.
- studySessionRepository: `startSession, completeSession, completePartialSession, recordOutcome, logManualSession, updateSession, deleteSession` + reads/sums.
- focusController: `start, pause, resume, extend` (EXTEND_SECONDS 300), `stopSavingPartial, stopDiscarding, reconcile, consumeCompletion, transitions.subscribe` (no replay).
- proRepository: flows `isPro/status/formattedPrice`, `launchPurchase`, `setDebugForcePro`.
- backupRepository: `exportTo`, `importFrom(File|Blob|text)`, `wipeAll`.

## Differences from Android
Browser time zone only. `formatInsightDate` locale-dependent. Billing fake (always succeeds, 600ms; debug force-Pro on). Alarms/reminders only while page open (focus end reconciled on reload). No real notifications/widgets/analytics. Shared default arrays/Maps/Sets = immutable.
