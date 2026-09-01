# Steadyline desktop web prototype: architecture contract

This document defines the intended web prototype. Read it before changing code.

The goal is a feature-complete desktop interpretation of the Steadyline study
planner. Android is the current product reference for behavior and product
concepts. The web implementation remains independent Angular code. It must not
copy Compose layouts, pretend a phone frame is a desktop product, or become a
shared runtime dependency of Android or a future iOS app.

This remains prototype code under `prototype/`. It is allowed to move quickly,
use local deterministic data, and omit production services. It is not allowed
to blur feature boundaries or invent product behavior that contradicts the
shipping app without recording the decision.

## 1. Product intent

Steadyline helps a student turn an exam date, syllabus, available hours, fixed
commitments, and current progress into a realistic daily plan. The main loop is:

1. Choose what is in scope.
2. Generate a credible plan around real availability.
3. Study from Today or Focus.
4. Record the sitting and recall result.
5. Recalculate revision, pace, and future work.

The desktop app is not an administration dashboard. It is the same student
product adapted to a larger workspace. It should make planning, syllabus
organization, progress inspection, and schedule adjustment materially easier
than on a phone while keeping Focus calm and narrow.

## 2. Relationship between platforms

Android, web, and future iOS are separate applications:

| Concern | Android | Web prototype | Future iOS |
|---|---|---|---|
| UI | Kotlin + Compose | Angular + Material 3 | Swift + SwiftUI |
| Navigation | Native Android | Angular Router | Native iOS |
| Persistence | Room/DataStore | Prototype browser adapter | Native iOS |
| Platform behavior | Android-specific | Browser/desktop-specific | iOS-specific |
| Product rules | Equivalent outcomes | Equivalent outcomes | Equivalent outcomes |

We share contracts, not UI code. Suitable shared artifacts are generated data,
design-token exports, JSON schemas, analytics names, and behavioral fixtures.
Scheduling implementations may remain native to each platform as long as the
same fixtures produce the same outcomes.

Never import code from `android/` into this Angular project. Read the Android
domain and feature code when behavior is unclear, then express the rule in
TypeScript and add a test or fixture that makes the parity observable.

## 3. Non-negotiable boundaries

1. Features do not import other features.
2. Domain code imports neither Angular nor browser APIs.
3. UI components do not read or write `localStorage` directly.
4. A page reads state through its feature facade/store and sends user intents
   back through that same boundary.
5. Shared visual values come from the web design system, never scattered raw
   colors, radii, or spacing values.
6. Routes are real URLs. Do not switch the whole application with one local
   `signal` or a template-only `@if` chain.
7. Large features are lazy loaded.
8. Demo data is deterministic, labeled, and replaceable through repository
   interfaces.
9. M3 is the component and token foundation, not a reason to enlarge the
   mobile navigation bar or force all content into cards.
10. Changes to product rules must be recorded in `DECISIONS.md` before a future
    agent treats them as platform truth.

## 4. Target source structure

```text
src/app/
  app/                    bootstrap, routes, shell, providers
  core/
    design/               tokens, theme, shared presentational components
    persistence/          browser storage adapter and migrations
    config/               prototype flags and deterministic clock
    testing/              builders and shared test utilities
  domain/
    models/               plain TypeScript product types
    scheduling/           pure scheduling and day-plan rules
    retention/            recall, decay, and revision rules
    progress/             coverage, pace, and projection calculations
  data/
    contracts/            repository interfaces
    local/                local prototype implementations
    fixtures/             demo account and deterministic history
  features/
    onboarding/
    today/
    syllabus/
    focus/
    progress/
    settings/
```

Each feature owns its routes, pages, feature-specific components, and facade.
Feature UI may depend on `domain`, `data/contracts`, and `core`. Cross-feature
navigation goes through the router. Cross-feature state comes from a shared
repository contract, not a direct store import.

## 5. State and data flow

Use Angular signals for synchronous view state and computed read models. Use
RxJS where a stream is genuinely asynchronous or cancelable. Do not wrap every
value in an Observable merely for uniformity.

```text
Page/component
  -> user intent
Feature facade
  -> domain operation
Repository interface
  -> local prototype adapter
  -> persisted state

Persisted state
  -> repository read model
  -> facade computed state
  -> page/component
```

The current `persisted()` helper is acceptable only during migration. The
target is versioned storage behind repositories, with decode validation,
migrations, reset capability, and recoverable handling of corrupt data.

The prototype does not need a backend yet. Repository contracts must make a
future authenticated remote implementation possible without rewriting pages.

## 6. Desktop information architecture

After onboarding, use a persistent desktop shell:

- Navigation rail or side navigation: Today, Syllabus, Focus, Progress,
  Settings.
- Main workspace: the active route.
- Context panel: optional and route-specific, never permanently empty.
- Running focus session: always reachable without hiding the current work.
- Global actions: limited to actions that truly apply across routes.

Desktop behavior by feature:

| Feature | Desktop adaptation |
|---|---|
| Onboarding | Centered, bounded task flow with optional contextual preview |
| Today | Timeline and day plan visible together where width permits |
| Syllabus | Tree/list workspace with persistent filters and detail/editor pane |
| Focus | Calm, narrow session surface; queue can remain adjacent |
| Progress | Comparative views with drill-down, not a grid of identical cards |
| Settings | Section navigation plus detail pane, with standard form controls |

Responsive behavior is structural:

- Wide desktop: navigation plus main workspace plus useful context pane.
- Compact desktop/tablet: navigation rail plus one or two content panes.
- Narrow browser: single-pane layout with mobile-appropriate navigation.

Do not preserve the current fixed `390 x 844` `.phone` wrapper in the desktop
shell. Mobile browser support is a responsive state of the web UI, not a phone
mockup floating on a desktop canvas.

## 7. Material 3 and design-system rules

Angular Material 3 supplies accessible component primitives, focus behavior,
theming, typography, density, and semantic tokens. Product-specific components
compose those primitives without reaching into private Material DOM classes.

- Apply the theme once at the application root.
- Prefer semantic `--mat-sys-*` roles and documented component overrides.
- Support light, dark, and system appearance.
- Desktop density may be more compact than mobile but must preserve readable
  type, clear states, and usable pointer targets.
- Every control needs default, hover, focus-visible, active, disabled, loading,
  and error behavior where applicable.
- Use standard desktop affordances, keyboard navigation, and visible focus.
- Use cards only when an independent grouped surface needs one.
- Motion communicates state and should normally complete in 150–250 ms.

## 8. Product parity policy

Android is evidence, not code to translate line by line. For each feature,
record:

- Android source files inspected.
- Product rules that must match.
- Desktop adaptations intentionally made.
- Known omissions or prototype-only behavior.
- Shared fixture coverage.

A web feature is parity-complete only when its important outcomes match the
Android behavior or the divergence is documented. Pixel parity is not the goal.

## 9. Testing contract

Minimum checks for a completed slice:

1. Pure domain tests for scheduling, retention, projection, and edge cases.
2. Facade/store tests for user intents and persisted outcomes.
3. Component tests for critical states and accessibility names.
4. Route-level smoke tests for direct URLs and refresh recovery.
5. Shared behavioral fixtures for rules expected to match other platforms.
6. Production build succeeds.
7. Browser review at wide, compact, and narrow breakpoints when visual work is
   explicitly in scope.

Static compilation does not prove visual quality, product parity, or correct
desktop behavior.

## 10. Definition of done

A slice is done when it has a real route, typed state, repository boundary,
empty/loading/error states where relevant, keyboard and focus behavior, tests,
and a recorded parity decision. A screen that only renders deterministic demo
content can still be a valid prototype slice, but it must be labeled and must
not bypass the architecture that real data will need.
