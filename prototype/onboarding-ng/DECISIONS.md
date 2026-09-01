# Steadyline desktop web decisions

Record decisions that affect product rules, platform parity, architecture, or
future implementation. Visual tweaks that do not alter a reusable rule do not
need an entry.

## Template

### YYYY-MM-DD: Short decision name

- **Decision:** What is now true.
- **Evidence:** Product source, test, research, or constraint used.
- **Platforms:** Android, web, iOS, or shared.
- **Consequences:** What future work must preserve or revisit.

## 2026-09-01: Independent applications with shared contracts

- **Decision:** Android, Angular web, and future iOS use independent application
  and UI code. Shared artifacts are generated data, JSON schemas, design tokens,
  analytics names, and behavioral fixtures.
- **Evidence:** Android is Kotlin/Compose and platform-specific; the web app is
  Angular/Material 3; future iOS should preserve native interaction quality.
- **Platforms:** Android, web, iOS, shared.
- **Consequences:** Do not import application code across platforms. Equivalent
  rule implementations must be checked against common fixtures.

## 2026-09-01: Android behavior, desktop presentation

- **Decision:** Android is the current reference for product outcomes. Web
  adapts information architecture to desktop and is not required to reproduce
  phone geometry or navigation.
- **Evidence:** The repository architecture declares Android as the shipping
  app; the Angular prototype already models the same student loop.
- **Platforms:** Android and web.
- **Consequences:** Parity reviews compare decisions and persisted outcomes.
  Intentional layout and interaction differences are expected and documented.

## 2026-09-01: Real routes replace the tab signal

- **Decision:** Primary web destinations use Angular Router and lazy component
  loading. The application shell owns navigation and the running-session entry.
- **Evidence:** Desktop URLs must be linkable, refreshable, accessible, and
  independently testable. A root `signal` switch did not provide those traits.
- **Platforms:** Web.
- **Consequences:** Cross-feature navigation goes through Router. Feature state
  must not depend on another feature's page component.

## 2026-09-01: Material 3 is foundation, not phone imitation

- **Decision:** Angular Material 3 supplies semantic tokens, component
  primitives, density, and interaction states. Desktop layouts use familiar
  navigation and multi-pane workspaces where they help the task.
- **Evidence:** Material supports adaptive products; the fixed 390 by 844 frame
  prevented credible desktop behavior.
- **Platforms:** Web.
- **Consequences:** Do not enlarge mobile bottom navigation or stretch mobile
  cards across the viewport. Keep a narrow responsive mode without presenting
  it as the desktop layout.
