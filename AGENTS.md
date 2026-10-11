# Steadyline

| Path | What |
|---|---|
| `android/` | **The app.** Kotlin + Compose. Ships. Contract: `ARCHITECTURE.md` — read before adding code. |
| `prototype/` | Throwaway visual reference (`mocks/*.html`, `onboarding-ng/`). Read-only. Never polish, fix, deploy, or port code from it. |
| `tokens.json`, `syllabus_cgl.json` | Generated exports. Never hand-edit. |
| `docs/MOBILE_DESIGN_RULES.md` | Copy + layout caps for every screen. |

## Rules
- Reply style: skill `caveman`, always.
- **No unit tests.** Don't write, run, or fix them. Ravi tests on device.
- Delivery = `/ship-apk` only. Never emulator, adb, Developer options.
- Visual ambiguity → ask which file/URL first. Screens exist in several places; guessing has been expensive.

## Harness (`.claude/`)
- Hooks block: adb/emulator, gradle test/check/build, edits to generated files, prototype, test dirs.
- Hook flags on every `.kt` edit: Android in `:domain`, feature→feature import, hardcoded dp/sp/colour/radius, inline Dispatchers, GlobalScope, runBlocking, LiveData/Rx/Hilt/Koin, `collectAsState()`.
- Stop hook compiles changed Kotlin; red → fix before finishing.
- Skills: `compose` (load before UI/VM code), `port-screen`, `ship-apk`. Agent: `arch-reviewer` before commit.
