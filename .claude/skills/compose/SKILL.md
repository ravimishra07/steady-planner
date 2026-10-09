---
name: compose
description: Compose + Android correctness rules. Load before writing or reviewing any Kotlin UI, ViewModel, Gradle, or R8 change.
---
**State**
- `remember(key)` when input changes; `rememberSaveable` for user input surviving rotation/process death.
- Derived from fast state → `derivedStateOf`. Never write state during composition.
- `LaunchedEffect(key)` keys = everything it reads; `Unit` only for truly once. Latest lambda → `rememberUpdatedState`.
- Read flows: `collectAsStateWithLifecycle()`. One-shot events = state field + consumed callback, not Channel to UI.
**Recomposition**
- Params stable: `List`/`val` data classes, `@Immutable` if needed. No `var`, no `MutableList` in UiState.
- Lazy lists: `key = { it.id }`, `contentType` for mixed rows. No lambda alloc in item loop.
- Fast-changing reads deferred: `Modifier.offset { }`, `graphicsLayer { }`, `drawBehind { }` lambdas, not value params.
- >2 updates/s value → own small state read by one leaf (focus timer).
**Modifiers**
- Order matters: `clickable` before `padding` = bigger hit area. `padding` before `background` = inset fill.
- First param of public composable after required: `modifier: Modifier = Modifier`, applied once to root.
- Touch target ≥48dp (token). `contentDescription` on icon-only buttons; decorative → null.
**Platform**
- `enableEdgeToEdge()` in Activity; screens consume `WindowInsets.safeDrawing`/`systemBars`; IME → `imePadding()`.
- Predictive back: `BackHandler`, no `onBackPressed` override.
- R8: release minify+shrinkResources on. Keep rules only for reflection/serialization actually used; no blanket `-keep class **`.
- Strings in `strings.xml` for user-visible copy.
