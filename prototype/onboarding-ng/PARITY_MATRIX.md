# Android to Angular product parity

Updated: 2026-09-01

This is an outcome matrix, not a pixel-parity checklist. Android is the current
shipping reference. Angular may add a desktop-specific presentation while the
underlying student decision and persisted result remain equivalent.

Status values: **matched**, **web extension**, **gap**, **platform-specific**.

## Application foundation

| Capability | Android evidence | Angular evidence | Status | Required work |
|---|---|---|---|---|
| Feature navigation | `SteadylineNavHost.kt`, `Routes.kt` | `app.routes.ts`, `app-shell.ts` | matched | Route smoke tests |
| Independent feature boundaries | Gradle `feature:*`, `domain`, `core:*` modules | Target boundaries in web `ARCHITECTURE.md` | gap | Extract pure domain and repository contracts from current stores |
| Theme and appearance | `core/design`, `SettingsStore` | M3 `mat.theme`, persisted appearance/accent | web extension | Consolidate tokens and add system mode |
| Local persistence | Room and DataStore repositories | Direct `persisted()` signals | gap | Versioned adapter, validation, migrations, repository interfaces |
| Startup decision | `StartupViewModel` checks plan/theme | Default route plus explicit onboarding route | gap | Persist onboarding-complete state and route deterministically |

## Onboarding

| Capability | Android evidence | Angular evidence | Status | Required work |
|---|---|---|---|---|
| Exam selection | `OnboardingExamStep.kt`, `ExamCatalog.kt` | `exam-step.ts`, `exam-pack.ts` | web extension | Label incomplete packs honestly |
| Target date | `OnboardingSteps.kt`, `OnboardingViewModel.kt` | `date-step.ts`, encoded local date | matched | Add boundary tests |
| Daily shape and hours | `OnboardingSteps.kt` | `shape-step.ts`, `hours-step.ts` | matched | Verify calculation fixtures |
| Fixed commitments | Availability repositories and onboarding state | `commitments-step.ts`, `commitments.ts` | web extension | Compare overlap/validation rules |
| Syllabus scope | Target syllabus domain and repository | provided/custom syllabus, taught-to, ordering | web extension | Define portable schema |
| Plan summary | Onboarding plan preview | `plan-step.ts` | matched | Desktop live preview and completion routing |
| Appearance | Settings flow after onboarding | First Angular onboarding step | intentional web extension | Record as web-only ordering choice |

## Today and planning

| Capability | Android evidence | Angular evidence | Status | Required work |
|---|---|---|---|---|
| Week/date navigation | `HomeCalendarChrome.kt` | `today-screen.ts` | matched | Desktop placement and route tests |
| Availability and fixed blocks | `DayTimeline.kt`, `HomeViewModel.kt` | `scheduler.ts`, commitments | matched conceptually | Shared outcome fixtures |
| Study scheduling | `Scheduler.kt`, `HomeViewModel.kt` | `day-plan.ts`, `day-planner.ts`, `scheduler.ts` | gap | Reconcile constants, priority, revision competition, and overflow |
| Free-window add | Home study picker | Today free-slot picker | matched | Keyboard and wide-screen presentation |
| Reschedule/skip | `RescheduleSheet.kt` | session sheet push/skip | matched conceptually | Persist and test reload outcome |
| Start Focus | Home-to-Focus navigation | Today start routes to FocusStore and `/focus` | matched | Direct-route recovery test |

## Syllabus

| Capability | Android evidence | Angular evidence | Status | Required work |
|---|---|---|---|---|
| Browse hierarchy | `SyllabusScreen.kt`, `SyllabusViewModel.kt` | `syllabus-browser.ts` | matched | Desktop tree/detail layout |
| Progress state | `SyllabusUiState.kt`, topic progress repository | `StudyStore`, retention read models | matched conceptually | Repository boundary and fixtures |
| Organize scope/order | Target syllabus domain | `organise-screen.ts`, `sequence.ts` | web extension | Complete bulk operations and unify browser state |
| Custom chapters/topics | Not a primary Android surface | Current Angular custom chapter/topic editing | web extension | Validate names and portable IDs |
| Projection | Android scheduling/analytics domains | `projection.ts` flat pace estimate | gap | Forward-run real planner before presenting reliable dates |

## Focus

| Capability | Android evidence | Angular evidence | Status | Required work |
|---|---|---|---|---|
| Start/pause/resume/end | `FocusViewModel.kt`, `FocusScreen.kt` | `focus-store.ts`, `focus-screen.ts` | matched | Timer boundary and reload tests |
| Session completion | Study session repositories | Writes sitting, progress, revision | matched conceptually | Transaction-like repository operation |
| Recall and revision | `StudySession.kt`, `TopicProgress.kt` | `retention.ts`, Focus completion | gap | Reconcile intervals and shared fixtures |
| Running state across navigation | App-owned focus runtime | Persistent desktop running-session control | matched | Visual/keyboard verification |
| App blocking | Focus-lock service/capabilities | Browser explanation/settings only | platform-specific | Never imply browser-level blocking |

## Progress

| Capability | Android evidence | Angular evidence | Status | Required work |
|---|---|---|---|---|
| Coverage and subject status | `StudyAnalytics.kt`, `ProgressViewModel.kt` | coverage and subject views in `progress-tab.ts` | matched conceptually | Reconcile formulas with fixtures |
| Pace and forecast | Analytics forecast | pace verdict and projection | gap | Use same definitions and denominator |
| Revision due | Revision insight | 7/30/90 due queue | web extension | Align due rules, preserve ranges |
| Study time | Insights study days | 14-day bars and heatmap | web extension | Label demo history and date window |
| Rebalance | Product recommendations | raise hours/move target/park chapters | web extension | Apply actions through repositories and test |

## Settings

| Capability | Android evidence | Angular evidence | Status | Required work |
|---|---|---|---|---|
| Exam/date/hours/breaks | `SettingsDetailViewModel.kt` | `settings-screen.ts` pages | matched | Desktop section/detail navigation |
| Appearance | `ThemeBottomSheet.kt`, design settings | accent/background variants | web extension | Add system appearance and contrast review |
| Focus and reminders | Android capabilities/settings | Prototype settings | platform-specific | Distinguish browser support |
| Export/delete | Android data controls | local JSON export/delete | web extension | Schema/version metadata and recovery confirmation |
| Privacy/about | Android More/settings | Angular static pages | matched conceptually | Final copy and route accessibility |
| Developer/demo data | Seed history | deterministic Angular demo data | matched | Label demo state globally |

## Completion gates

- [ ] Every **gap** above is resolved or recorded as an intentional divergence.
- [ ] Pure rule fixtures cover scheduling, retention, coverage, and projection.
      Planning-capacity parity is now covered; the other rule families remain.
- [ ] Direct URLs and reloads preserve expected state.
- [ ] Wide, compact, and narrow desktop layouts are visually checked.
- [ ] Production build and automated tests pass.
- [ ] Browser-only limitations are not represented as native capabilities.
