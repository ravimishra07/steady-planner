# Flutter app — build spec

Target: Android + iOS. Lives in `flutter_app/`. The web app in `design/` is a
prototype and the reference for behaviour; it is not being ported line by line.

## The one rule

**No widget hardcodes a colour, font size, radius or spacing value.**
Everything comes from the theme. If a value is missing from the theme, add it
to the theme — never inline it. This is the whole point of the exercise: the
user changes the theme and every screen follows.

A review that finds `Color(0xFF...)`, `fontSize: 16`, `EdgeInsets.all(20)` or
`BorderRadius.circular(14)` inside a screen file fails.

## Source of truth

`tokens.json` at the repo root is generated from `design/sam-tokens.css`.
It has three groups:

- `structure` — spacing, radii, font sizes, weights, line heights (54 entries)
- `dark` — 33 colour tokens
- `light` — the same 33, overridden

Dark and light have exact parity. Transcribe both; do not invent values and do
not "improve" any colour.

`syllabus_cgl.json` (47KB) is the SSC CGL syllabus: 4 Tier-1 sections, 615
nodes, plus 5 Tier-2 sections. Node shape: `{n: name, h: hours, q: questions,
c: [children]}`. Ships as an asset; never rewritten by hand.

## Layout

```
flutter_app/
  lib/
    theme/
      tokens.dart          raw values, transcribed from tokens.json
      app_colors.dart      ThemeExtension — everything Material lacks
      app_typography.dart  the type ramp as named styles
      app_theme.dart       ThemeData light + dark, built from the above
      theme_controller.dart  system/light/dark, persisted
    domain/
      plan.dart            state model
      scheduler.dart       cushion(), availableHours(), needHours()
      syllabus.dart        loads the asset, hour splitting
    screens/
      onboarding/          exam, date, shape, hours, cushion
      home/
    widgets/               shared: PillTab, ChoiceRow, CircleCheck, Gauge
  assets/syllabus_cgl.json
```

## Theme contract

Material's `ColorScheme` covers only part of the palette. Everything else goes
in a `ThemeExtension<AppColors>`:

`brand, brandSoft, brandDeep, brandContainer, success, successContainer,
warning, warningTint, warningRow, danger, dangerContainer, dangerSoft,
info, infoTint, surface, surfaceTinted, surfaceCard, surfaceControl,
surfaceInk, elevated, surface3, border, borderSubtle, hairline,
hairlineSoft, glassTint, glassStroke, textPrimary, textSecondary,
textMuted, textDisabled, tabBg, tabSelected, tabUnselected, onBrand,
onSuccess, cardBorder, cardShadow`

Access: `Theme.of(context).extension<AppColors>()!`. Add a
`context.colors` extension so call sites stay short.

Typography mirrors the `fs-*` ramp by name — `fsXs` (11) … `fsDisplay` (56).
Numbers use tabular figures (`FontFeature.tabularFigures()`), as on the web.

Font: platform default for now (Roboto on Android, SF on iOS), set in one
place. Swapping to a bundled face later must be a one-line change.

## Behaviour that must match the web

- Scheduler: `need = round(rawHours * 1.28)`,
  `available = weeks * (5*wd + 2*we) + remainder * wd`, `gap = need - available`.
  For SSC CGL at 4h/7h over 118 days that is **568 / 812, gap 244**. Verify.
- Hour splitting: a topic's hours divide across children in 0.5h steps and the
  parts must sum exactly to the parent. Analogies 7h → 2.5 + 2.5 + 2.
- Syllabus ticks are tri-state: ticking a parent ticks every leaf under it; a
  partly-done parent renders a filled centre, not a tick.
- Theme choice persists across restarts.

## Screens, in build order

1. **Onboarding — exam.** Six exams, each with applicant count and paper
   structure. Only SSC CGL shows hours (634 hrs · 49 topics); the others have
   no tree, so showing a number would be a lie. Selected state, CTA pinned to
   the bottom.
2. **Onboarding — date.** Countdown with weeks broken out, a scale from today
   to the exam, a date field, and a "not announced yet" path.
3. **Onboarding — day shape.** Three options, each showing the weekday and
   weekend hours it implies.
4. **Onboarding — hours.** Two live sliders and a study spot field; the total
   hours before the exam recomputes as they move.
5. **Onboarding — cushion.** The payoff: the gap as one large number, a
   self-labelling gauge (the numbers sit inside the regions they describe),
   and three ways to close it.
6. **Home / Today.** Week strip, day timeline with named free gaps, blocks
   with subject badge and Read/Practice/Revise tag, bottom tab bar.

Screens 1–5 are a flow with progress dots and a back affordance. Onboarding
answers are held in memory until the last step commits them.

## Reference

Run the web app to see any screen's intended behaviour:

```sh
sh design/serve.sh     # http://localhost:8765/app.html
```

`design/SCREENS.md` describes every screen and flags what is still demo data.
