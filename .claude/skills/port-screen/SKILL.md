---
name: port-screen
description: Port a screen from prototype mock to Android Compose. Use when building or matching a screen to a mock.
argument-hint: <screen or mock file>
---
1. Ambiguous source → ask which file/URL. Mocks: `prototype/mocks/*.html`, `prototype/onboarding-ng/`. Read-only.
2. Read mock. List: elements, states (loading/empty/error), copy, interactions.
3. Map every colour/size/radius/type to `:core:design` (`AppTheme.colors.*`, `Tokens.kt`, `Type.kt`). Missing token → stop, ask. Colour pairs per ARCHITECTURE §5b.
4. Copy per `docs/MOBILE_DESIGN_RULES.md` (word caps, no narration).
5. Write `feature/<f>/<X>Screen.kt` (stateless, UiState + lambdas), `<X>ViewModel.kt` (`StateFlow`, `stateIn WhileSubscribed(5_000)`), `<X>UiState.kt` (one immutable data class). Reuse `:core:design` components first.
6. Nav wired in `:app` only. Load skill `compose`.
7. `/ship-apk`. Report diffs vs mock you could not match.
