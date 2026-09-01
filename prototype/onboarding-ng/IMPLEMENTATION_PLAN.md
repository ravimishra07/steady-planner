# Steadyline desktop web prototype: implementation plan

Use this plan with `ARCHITECTURE.md`. It sequences the work so future agents do
not redesign the shell while product rules are still trapped inside components.

## Outcome

Build a complete Angular Material 3 desktop prototype covering the same product
loop as Android: onboarding, planning, Today, Syllabus, Focus, Progress, and
Settings. Preserve independent TypeScript implementation and make future
cross-platform consistency testable through shared contracts and fixtures.

## Phase 0: establish evidence

- [ ] Inventory the Android routes, domain models, repositories, and feature
      states relevant to the web prototype.
- [ ] Inventory current Angular behavior and classify each piece as reusable,
      mobile-only presentation, architecture debt, or incomplete.
- [ ] Create `DECISIONS.md` with a small template: date, decision, evidence,
      platforms affected, and consequences.
- [ ] Create a parity matrix for the six major features.
- [ ] Record the current build and test baseline without modifying behavior.

Exit: every current capability has an owner and no Android behavior is being
silently guessed.

## Phase 1: architectural foundation

- [ ] Introduce Angular Router with lazy feature routes.
- [ ] Create `app`, `core`, `domain`, `data`, and `features` boundaries from
      `ARCHITECTURE.md`.
- [ ] Move pure calculations out of components and stores into `domain`.
- [ ] Define repository interfaces for plan, syllabus, sessions, progress,
      preferences, and focus state.
- [ ] Replace direct persistence usage with a versioned local adapter.
- [ ] Add deterministic clock and demo-data providers.
- [ ] Add domain and route smoke-test harnesses.

Exit: the existing flow still works, routes survive refresh, and feature UI no
longer owns persistence.

## Phase 2: desktop shell and design system

- [ ] Remove the fixed phone frame from the post-onboarding application.
- [ ] Build the responsive application shell and persistent navigation.
- [ ] Define wide, compact, and narrow layout behavior.
- [ ] Centralize theme, density, typography, spacing, shape, and elevation.
- [ ] Create only the shared components proven necessary by two or more
      features.
- [ ] Implement skip link, focus order, keyboard states, reduced motion, and
      appearance preference.
- [ ] Keep a running Focus session reachable from every route.

Exit: all feature placeholders render inside a credible desktop shell without
changing their underlying product rules.

## Phase 3: feature slices

Implement one vertical slice at a time. Do not create all page shells first and
leave behavior for later.

### 3A. Onboarding

- [ ] Preserve exam, coaching, commitments, date, plan shape, available hours,
      syllabus scope, and plan review decisions.
- [ ] Add a contextual plan preview where desktop width makes it useful.
- [ ] Make completion create repositories' initial state, then route to Today.

### 3B. Today

- [ ] Present date navigation, availability, fixed commitments, generated study
      blocks, breaks, free windows, and plan editing.
- [ ] Support starting a sitting and adding work to a free window.
- [ ] Keep timeline and actionable plan readable together on wide screens.

### 3C. Syllabus

- [ ] Unify browse and organize views around one syllabus state.
- [ ] Support order rules, inclusion, taught-to markers, custom chapters,
      multi-select, and bulk actions.
- [ ] Replace optimistic flat projection with forward scheduling before dates
      are presented as trustworthy.

### 3D. Focus

- [ ] Preserve queue, browse-all, running timer, pause, extend, early finish,
      completion, recall result, and next revision behavior.
- [ ] Make reload recovery explicit and deterministic.
- [ ] Treat browser limitations separately from Android focus-lock behavior.

### 3E. Progress

- [ ] Implement coverage, pace, rebalance options, revision due ranges, study
      time, and drill-down.
- [ ] Use desktop space for comparison and explanation, not repeated metric
      cards.
- [ ] Label fabricated history and methodology.

### 3F. Settings

- [ ] Use section navigation and a detail pane on desktop.
- [ ] Cover exam/date, hours/breaks, fixed commitments, appearance, focus,
      reminders, data controls, privacy, about, and developer tools.
- [ ] Hide or explain platform-specific options that a browser cannot support.

Exit for each slice: route, behavior, repository state, tests, accessibility,
desktop adaptation, and parity record are complete.

## Phase 4: shared cross-platform artifacts

- [ ] Create a root `shared/` only after the first real duplicate contract is
      proven. Do not create speculative packages.
- [ ] Move generated syllabus/exam-pack exports behind a documented generation
      path rather than hand-maintaining copies.
- [ ] Define JSON schemas for portable plans, sessions, preferences, and
      syllabus progress.
- [ ] Add scheduling and retention fixture cases with named expected outcomes.
- [ ] Run the fixtures in TypeScript; document how Kotlin and future Swift
      implementations consume the same cases.
- [ ] Define shared analytics event names without coupling analytics SDKs.

Exit: equivalent product rules can be checked across platforms without sharing
UI or runtime code.

## Phase 5: prototype hardening

- [ ] Add loading, empty, error, corrupt-storage, and reset/recovery states.
- [ ] Test wide, compact, and narrow layouts with keyboard-only operation.
- [ ] Check contrast, accessible names, landmarks, and focus restoration.
- [ ] Verify local data migrations and direct-route refreshes.
- [ ] Remove obsolete mobile-frame CSS and superseded state paths.
- [ ] Run unit tests, component tests, and production build.
- [ ] Record what remains prototype-only before any shipping-web decision.

Exit: the prototype is coherent enough to validate the full desktop product,
while its production gaps remain explicit.

## Agent working rules

Before starting a phase:

1. Read repository `AGENTS.md`, root `ARCHITECTURE.md`, this plan, and the web
   `ARCHITECTURE.md`.
2. Inspect current Git changes and avoid overwriting unrelated work.
3. Inspect the exact Android behavior relevant to the slice.
4. State the intended desktop adaptation before editing UI.
5. Work within one vertical slice unless an architectural prerequisite is
   explicitly part of the task.

At handoff, report files changed, Android evidence inspected, decisions made,
tests/builds actually run, visual checks actually performed, and unresolved
parity differences. Never describe a build as visual verification.
