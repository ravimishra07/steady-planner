# android-web — contract

Pixel/behaviour clone of `android/` in plain HTML/JS. Android code = source of truth. Mirror it, never redesign.
No build step. ES modules. Run: `sh serve.sh` → http://localhost:8765. No unit tests.

## Layout
```
index.html  serve.sh  CONTRACT.md
platform/   h.js (DOM helper) store.js (localStorage) flow.js (StateFlow) nav.js (NavHost/back stack) icons.js fonts/
design/     tokens.css (Tokens.kt, Type.kt as CSS vars) colors.js (AppColors/Palette/AccentPalette/BackgroundAppearance → CSS vars) components.js (AppComponents etc.)
domain/     1 JS file per Kotlin file in android/domain, same names
data/       1 JS file per Kotlin file in android/core/data, same names; container.js = AppContainer
assets/     copies of android assets (syllabus json etc.)
app/        shell.js (Scaffold + BottomBar + routes, SteadylineNavHost) , paywall.js
features/<f>/ 1 JS file per Kotlin file in android/feature/<f>, same names
```

## Rules
- **Names mirror Kotlin 1:1**: class, fun, field, enum, route names. `HomeViewModel.kt` → `features/home/HomeViewModel.js` exporting `HomeViewModel`. Composable `fun Foo(...)` → `export function Foo(props)` returning a DOM node. Kotlin named args → one props object, same keys.
- **Units**: 1dp = 1px, 1sp = 1px. Values only via CSS vars from tokens.css (`--space-lg`, `--radius-md`, `--font-...`) / colors (`--c-<appColorsField>`, camelCase kept, e.g. `--c-surfaceContainer`). No raw numbers in feature code, same as Android.
- **Type**: Roboto (vendored woff2 in platform/fonts). M3 scale classes `.t-bodyLarge` etc. from Type.kt (size/line-height/weight/letter-spacing exact).
- **Rendering**: `h(tag, props, ...children)` from platform/h.js. Screen = pure function(uiState, callbacks) → node. Route re-renders on its ViewModel state change. Keep scroll position across re-render (nav.js handles).
- **State**: `MutableStateFlow` in flow.js: `.value`, `.subscribe(fn)→unsub`, `map`, `combine`. ViewModel = class with `uiState` StateFlow + methods same as Kotlin.
- **Local UI state**: Compose `remember { mutableStateOf() }` → `rememberScoped(key, init, deps?)` (nav.js): lives while the composable keeps being rendered, resets when that subtree leaves. `remember` = whole destination, `rememberSaveable` = back-stack entry.
- **String formatting**: `formatAndroid(fmt, ...args)` (platform/format.js, re-exported by data/common.js) = full java.util.Formatter (`%1$s`, `%02d`, `%2$02d`, `%.1f`, `%,d`, …).
- **Persistence**: DataStore prefs → `store.prefs(name)` get/set/flow keyed exactly like Kotlin keys. Room tables → `store.table(name)` (array of rows, autoincrement id, insert/update/delete/query + flow). Everything in localStorage under `sl.` prefix. Survives reload = like app relaunch.
- **Coroutines/delays/time**: `Date`, `setTimeout`/`setInterval`. Focus timer must survive reload (compute from stored start timestamps like Android).
- **Platform-only things** (notifications, widgets, alarms, billing, backups to files, haptics): stub with same API; billing/Pro → local toggle; notifications → no-op or in-page toast. Note each stub in file header comment.
- **Interactions**: ripple (M3 state layer: hover 8%, press 10% of content colour), M3 motion durations, dialogs/bottom sheets/snackbars match Compose M3 visuals (scrim `Opacity.modalScrim`).
- **Viewport**: app frame 412×915 centred on desktop, full-screen on mobile. Status bar 24px + gesture bar 24px insets as fake system bars.
- **Icons**: Compose `Icons.Rounded.X`/`Icons.Outlined.X` → `icon('Rounded.X')` from icons.js with real Material SVG paths (same set as material-icons-extended).
- Never import across features. Features talk to data via `container` passed in, like AppContainer.
