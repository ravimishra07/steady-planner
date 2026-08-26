# Steadyline Android — architecture

Kotlin + Jetpack Compose, Android only.

`:domain` is a plain Kotlin/JVM Gradle module with no Android dependency. That is
ordinary clean architecture, not a KMP construct — there is no `commonMain` here.
It earns its place three ways: JVM-only tests run in milliseconds instead of
needing Robolectric, the build physically prevents `Context` leaking into the
scheduler, and it skips resource and manifest processing so it compiles faster.
That it could later become a KMP source set is a free side effect, not the
reason for it.

Module boundaries cost nothing at runtime. After R8 it is all one DEX in one ART
process; a call across modules compiles to the same invoke as a call within one.
The only cost is Gradle configuration time.

This document is the contract. Any agent adding code follows it or the change is
rejected. It exists because the fastest way to ruin an app this size is to let
five different agents each invent their own structure.

---

## 1. Non-negotiables

1. **Nothing blocks the first frame except the theme.** Every other startup task
   runs off the critical path.
2. **`:domain` never imports Android.** No `Context`, no `android.*`, no Compose.
   If it needs a platform thing, that thing belongs in `:core:data`.
3. **Features never import features.** `:feature:home` cannot see
   `:feature:syllabus`. They meet in `:app`.
4. **No hardcoded colours, sizes, radii or spacing in UI code.** Everything comes
   from `:core:design`.
5. **No `Dispatchers.IO` written inline.** Threading goes through `AppDispatchers`.
6. **One screen = one ViewModel + one immutable `UiState` + stateless composables.**

---

## 2. Module graph

```
:app                    Application, MainActivity, NavHost, AppContainer
  ├── :feature:onboarding
  ├── :feature:home
  ├── :feature:syllabus
  ├── :feature:focus
  ├── :feature:progress
  └── :feature:settings
        │
        ├── :core:design    theme, tokens, shared composables
        ├── :core:common    AppDispatchers, Result, extensions
        ├── :core:data      repositories, DataStore, RemoteConfig
        └── :domain         pure Kotlin — models, scheduler, hour splitting
```

**Allowed dependencies**

| Module | May depend on |
|---|---|
| `:app` | everything |
| `:feature:*` | `:domain`, `:core:*` |
| `:core:data` | `:domain`, `:core:common` |
| `:core:design` | nothing but Compose |
| `:core:common` | nothing |
| `:domain` | nothing — plain Kotlin/JVM module |

Why modules and not one big `:app`: Gradle compiles them in parallel, the
dependency rules are enforced by the build rather than by discipline, and an
agent working on one feature has a blast radius of one directory.

Why not more modules than this: every extra module costs build configuration and
navigation overhead. Six features and four cores is the right size for this app.
**Do not add a module without a reason written into this file.**

---

## 3. Startup — the part that decides whether the app feels fast

### Critical path

```
Application.onCreate     → construct AppContainer only. Target < 20ms.
MainActivity.onCreate    → installSplashScreen(), setContent
SplashViewModel          → resolve theme, resolve "has a plan?"  (parallel)
first frame              → onboarding or home
```

Nothing else is allowed on this path. Not syllabus parsing, not remote config,
not analytics, not migrations.

### Splash

Use `androidx.core.splashscreen` — the system splash. **Never a splash Activity
and never a splash Composable that fakes a delay.** A splash Activity adds a
whole extra Activity launch to cold start.

```kotlin
val splash = installSplashScreen()
splash.setKeepOnScreenCondition { !viewModel.ready.value }
```

The splash holds only until theme + plan-presence resolve. Both are small
DataStore reads. If that ever exceeds ~100ms, the fix is to make the reads
smaller, not to show a nicer splash.

### Sequencing — parallel or series?

**Series only where there is a genuine data dependency.** There are almost none.

```kotlin
// startup: parallel, then release the splash
coroutineScope {
    val theme = async(dispatchers.io) { settings.themeMode() }
    val hasPlan = async(dispatchers.io) { planStore.exists() }
    _state.value = Startup(theme.await(), hasPlan.await())
}
```

**Deferred — never on the critical path:**

| Work | When |
|---|---|
| Syllabus JSON (47KB, 817 nodes) | First time the Syllabus screen opens. Parsed on `Dispatchers.Default`, cached in memory after. |
| Remote config fetch | Background, after first frame. Applies next launch. |
| Analytics init | After first frame, `Dispatchers.Default`. |
| Any migration | Background, guarded by a version key. |

Parsing 47KB of JSON at startup would be the single easiest way to make this app
feel slow. It is only needed on one screen.

### Baseline Profile

Ship one from v1. Compose is not in the system image, so it is interpreted on
first launches until AOT-compiled — Baseline Profiles are worth roughly a 30%
improvement in code execution and around 22% off cold start in measured cases
([Android Developers](https://developer.android.com/develop/ui/compose/performance/baseline-profiles)).

Generate with a `:macrobenchmark` module covering two journeys: cold start →
home, and onboarding start → finish.

---

## 4. Threading

**One dispatcher holder, injected everywhere. No exceptions.**

```kotlin
interface AppDispatchers {
    val main: CoroutineDispatcher          // UI only
    val mainImmediate: CoroutineDispatcher // reentrant UI updates
    val io: CoroutineDispatcher            // disk, DataStore, network
    val default: CoroutineDispatcher       // CPU — parsing, scheduling, sorting
}
```

Writing `Dispatchers.IO` inline makes the code untestable and hides where work
happens. Inject `AppDispatchers` and use `dispatchers.io`.

### Which pool for what

| Work | Dispatcher | Why |
|---|---|---|
| DataStore read/write | `io` | blocking disk |
| Syllabus JSON parse | `default` | CPU-bound, not I/O |
| Scheduler / cushion / hour split | `default` | pure CPU |
| StateFlow emission to UI | `main` | Compose reads on main |
| Remote config fetch | `io` | network |

**Do not create custom thread pools.** `Dispatchers.IO` is already an elastic
shared pool; a bespoke `Executors.newFixedThreadPool` competes with it and costs
memory. If you think you need one, write the reason here first.

### Scopes

- ViewModel work → `viewModelScope`. Cancels with the screen.
- Work that must outlive a screen (a running focus session) → a scope owned by
  `AppContainer`, `SupervisorJob() + dispatchers.default`.
- **Never `GlobalScope`.**

### Flow rules

- Repositories expose `Flow`. ViewModels expose `StateFlow<UiState>`.
- Use `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` —
  survives rotation, stops work when backgrounded.
- No `LiveData`, no RxJava.

---

## 5. Dependency injection

**Manual `AppContainer`. No Hilt, no Koin.**

```kotlin
class AppContainer(context: Context) {
    val dispatchers: AppDispatchers by lazy { DefaultAppDispatchers() }
    val settings: SettingsStore by lazy { SettingsStore(context, dispatchers) }
    val planStore: PlanStore by lazy { PlanStore(context, dispatchers) }
    val syllabus: SyllabusRepository by lazy { SyllabusRepository(context, dispatchers) }
    val remoteConfig: RemoteConfig by lazy { LocalRemoteConfig() }
}
```

Reasons, in order:

1. **Startup cost is zero.** No graph to build, no codegen to run. Every field is
   `by lazy`, so nothing is constructed until first use.
2. **An agent can read the whole dependency graph in one screen of code.** Hilt
   scatters it across annotations and generated files.
3. This app has one Activity and no dynamic feature modules. Hilt solves problems
   we do not have.

If the graph ever exceeds ~20 entries, revisit — and record the decision here.

---

## 6. Remote config

Ships as an interface with compile-time defaults from day one, so nothing has to
be rewired later.

```kotlin
interface RemoteConfig {
    fun bool(key: String, default: Boolean): Boolean
    fun int(key: String, default: Int): Int
    fun string(key: String, default: String): String
    suspend fun refresh()          // never called on the critical path
}
```

- Today: `LocalRemoteConfig` returns the defaults.
- Later: a Firebase implementation behind the same interface.

**Rules:** every key has a compile-time default; the app must work correctly
offline and on first launch with no fetch; `refresh()` runs after first frame and
its values apply on the next launch, never mid-session.

---

## 7. UI layer contract

Every screen, without exception:

```
feature/home/
  HomeScreen.kt      @Composable, stateless, takes UiState + callbacks
  HomeViewModel.kt   exposes StateFlow<HomeUiState>
  HomeUiState.kt     one immutable data class
```

- The composable **never** touches a repository, a dispatcher or a store.
- `UiState` is a single immutable `data class` — not five separate flows.
- Loading and error are fields on that state, not separate screens.
- Screen-level composables take lambdas, not the ViewModel, so previews work.

### High-frequency state is the exception

One immutable `UiState` per screen is right for almost everything, but it is
wrong for values that change many times a second. The focus timer is the case in
this app: copying a whole `UiState` every tick and recomposing the screen around
it is pure waste.

For those, keep the changing value in its own small state and let only the widget
that draws it read that state:

```kotlin
// the rest of the screen never recomposes on a tick
val remaining by viewModel.remainingSeconds.collectAsStateWithLifecycle()
```

Rule: if a value updates more than about twice a second, it does not belong in
the screen's `UiState`.

### Compose performance rules

- Hoist state. Pass the smallest thing a composable needs, never the whole state.
- `LazyColumn` items need stable `key`s.
- No lambda allocation in a loop body — hoist it.
- Data classes in `UiState` must be stable: `List` is fine, `var` is not.
- Read `StateFlow` with `collectAsStateWithLifecycle()`, not `collectAsState()`.

### Navigation

Single Activity, `navigation-compose`, routes as a sealed interface in `:app`.
Features expose an entry composable and a route constant. **A feature never
navigates to another feature directly** — it invokes a callback that `:app` wires.

---

## 8. Budgets

These are checkable, so they are the definition of "fast" for this project.

| Metric | Budget |
|---|---|
| `Application.onCreate` | < 20 ms |
| Cold start → first frame (mid-range device) | < 500 ms |
| Any frame during scroll or transition | < 16 ms |
| Release APK | < 15 MB |
| Syllabus screen open → tree visible | < 200 ms |

App size matters commercially, not just aesthetically: uninstall probability
rises measurably with each extra few MB, and India runs overwhelmingly on
mid-range and budget Android.

---

## 9. Forbidden

An agent doing any of these has broken the architecture:

- Work in `Application.onCreate` beyond constructing `AppContainer`
- A splash Activity, or an artificial splash delay
- Parsing the syllabus at startup
- Blocking the critical path on remote config, network, or analytics
- `Dispatchers.IO` / `Dispatchers.Default` written inline in a ViewModel or composable
- `GlobalScope`
- `runBlocking` anywhere outside tests
- A feature module importing another feature module
- Android imports inside `:domain`
- Hardcoded colours, font sizes, radii or spacing in UI code
- `LiveData`, RxJava, or a second DI framework
- A `UseCase` class that only forwards to a repository, or a mapper between two
  near-identical models. Layers are justified by behaviour, not by symmetry.
- Putting a per-second value in a screen-wide `UiState`
- Adding a Gradle module without recording why in section 2

---

## 10. Delivery

**Never launch an emulator, install a system image, or run `adb install`.**
This machine cannot run the emulator, and the AVDs on it reference system
images that are not installed. Attempting it wastes minutes on a boot that
always times out.

The handoff for any Android change is:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd android && ./gradlew :app:assembleDebug
open -R app/build/outputs/apk/debug/app-debug.apk
```

Never suggest enabling Developer options or wireless debugging — that breaks
banking apps on the device. No emulator, no adb.

Claim only what the build proves — it compiles, tests pass, APK size — and
never that a screen "works" without having seen it.

`gradle/wrapper/gradle-wrapper.properties` points at the `-all` distribution
because that is the one in the local Gradle cache; the `-bin` URL times out.

---

## 11. Build order

Each phase ends with something installable that gets judged on a real device.

| Phase | Contents | Done when |
|---|---|---|
| 0 | Modules, theme from `tokens.json`, nav shell, bottom tabs, splash, `AppContainer`, `AppDispatchers` | App installs, tabs switch, light/dark toggles, cold start measured |
| 1 | Onboarding — 5 steps | The whole flow walks on a phone |
| 2 | Home + the real scheduler | The day is generated from syllabus and hours, not fixed data |
| 3 | Syllabus — subject tabs, three-level tree | Ticking and hour splitting correct |
| 4 | Focus + Rebalance | Timer survives backgrounding |
| 5 | Progress, More, Settings | Theme switch, sign out, policies |

Phase 0 carries no design decisions, so there is nothing to argue about — it
exists to give every later phase somewhere to land.

Phase 2 is the only real risk. Everything else is layout already agreed in the
web prototype; the generated day plan is the unproven idea.
