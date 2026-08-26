# Steadyline

Two things live here. Only one of them is the product.

| Path | What it is |
|---|---|
| `android/` | **The app.** Kotlin + Compose. This is what ships. |
| `prototype/` | **Throwaway.** Web mockups used to settle the design. Not the product. |

## prototype/ is a prototype

Everything under `prototype/` is a reference for what screens should look like
and how they should behave. It is not shipped, not maintained, and not the
source of truth for anything except visual intent.

**Do not** polish it, refactor it, fix its bugs, deploy it, or port code out of
it. If a screen there looks broken, that does not matter.

**Do** open it to see how a screen is meant to look before building the Android
version of that screen.

```
prototype/
  web-app/       the interactive version — the best reference. `sh serve.sh`
  original-html/ the first static screens
  mockups/       hi-fi artboards, older palette — layout reference only
  next-port/     an abandoned Next.js port. Dead. Ignore it.
```

Its own `README` and `SCREENS.md` describe the screens.

## Working here

- `ARCHITECTURE.md` is the contract for `android/`. Read it before adding code.
- Android delivery is: build the APK, reveal it in Finder. **Never** start an
  emulator, run `adb`, or suggest enabling Developer options — see
  ARCHITECTURE.md section 10.
- `tokens.json` and `syllabus_cgl.json` at the root are shared data: the design
  tokens and the SSC CGL syllabus tree. Both are generated exports — do not
  hand-edit them.

## Asking about intent

When something visual is ambiguous, ask which file or URL is being looked at
before building anything. Several screens have existed in more than one place
in this repo, and guessing has been expensive.
