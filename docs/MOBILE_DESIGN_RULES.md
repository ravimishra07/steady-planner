# Mobile design rules

Hard rules for every screen. Break one = redo screen.
Numbers marked **[M3]** are Material 3 spec. **[house]** = our call, not research.

---

## 1. Text amount (biggest problem)

Users scan, not read. Mobile comprehension is about half of desktop (NN/g Cloze test). Concise, scannable copy tested 124% more usable (NN/g).

| Element | Max | Example good | Example bad |
|---|---|---|---|
| Screen title | 1–3 words | `Today` | `Your plan for today` |
| Section header | 1–3 words | `Backlog` | `Lectures you're behind on` |
| Button | 1–2 words, verb | `Start` | `Start this session now` |
| Chip / tag | 1 word | `PYQ` | `PYQ practice done` |
| Row primary | ≤5 words | `Laws of Motion` | — |
| Row secondary | ≤6 words or a number | `3 lectures behind` | sentence |
| Empty state | title ≤4 words + 1 line ≤10 words + 1 button | `No sessions yet` | paragraph |
| Dialog | title ≤5 words, body ≤15 words, 2 buttons | `Start fresh?` | 3-line explanation |
| Helper text under field | ≤8 words, only if field is ambiguous | `Minutes, 5–180` | — |
| Onboarding step | title ≤6 words, subtitle ≤10 words or none | `Hours per day` | — |

**[house]** Rules:
- **No 2–3 line text blocks on any app screen.** Need more → info icon → sheet. Exception: privacy policy.
- No narration. UI does not explain itself ("This shows…", "Here you can…", "We'll use this to…"). Delete.
- No repeating what's visible. Title says `Backlog` → row doesn't say "backlog".
- Numbers over words: `23 behind`, not "You are twenty-three lectures behind".
- Sentence case. No Title Case, no ALL CAPS **[M3]**.
- Numerals, never spelled out **[M3]**.
- Front-load: key word first **[M3]**.
- No "est.", "(approx)" or hedging on every number. Mark estimate once, at the screen level.
- No exclamation marks. No motivational filler. No "Great job!".

Current debt: 48 strings over 12 words. Worst: `onboarding_usage_data_note` (30), `onboarding_syllabus_blank` (29), `syllabus_yield_method` (27), `settings_plan_*_detail` (24–25), `syllabus_stage_labels_body` (22).

---

## 2. Type

Use the M3 scale only **[M3]**:

| Role | sp / line | Weight | Use |
|---|---|---|---|
| displaySmall | 36/44 | Regular | single hero number (days left, timer) |
| headlineSmall | 24/32 | Regular | screen hero title, onboarding question |
| titleLarge | 22/28 | Regular | top app bar |
| titleMedium | 16/24 | Medium | card title, row primary |
| titleSmall | 14/20 | Medium | section header |
| bodyLarge | 16/24 | Regular | main body (rarely needed) |
| bodyMedium | 14/20 | Regular | row secondary, dialog body |
| bodySmall | 12/16 | Regular | metadata only |
| labelLarge | 14/20 | Medium | buttons |
| labelMedium | 12/16 | Medium | chips, tabs |

**[house]**
- **Max 3 sizes per screen** + 1 hero number.
- Max 2 weights: Regular + Medium. Bold only for the hero number.
- **Nothing below 12sp.** `FontSize.xxs` (9sp), `xs2` (10sp) and `xs` (11sp) are banned. Unused today; delete them.
- `Tokens.kt FontSize` has 17 sizes. Collapse to the M3 roles above via `MaterialTheme.typography`; drop raw `fontSize =`.
- Hierarchy through size + weight + color (onSurface vs onSurfaceVariant), not through more sizes.

---

## 3. Color and contrast

Minimums (WCAG 2.2 AA):
- Body text: **4.5:1**.
- Large text (≥24sp regular, ≥19sp bold) and icons, borders, chart lines: **3:1**.

**Ban: tinted background + same-hue darker text.** Low-contrast pastel chips. Fails 4.5:1 almost always.

Allowed pairs only **[M3]**:

| Background | Text/icon on it |
|---|---|
| surface / surfaceContainer* | onSurface (primary text), onSurfaceVariant (secondary) |
| primary | onPrimary |
| primaryContainer | onPrimaryContainer |
| secondaryContainer | onSecondaryContainer |
| errorContainer | onErrorContainer |

**[house]**
- Never `color.copy(alpha = 0.1x)` as a background with text on it. 15 alpha tints in the codebase now; audit each.
- **1 accent color per screen.** Everything else neutral (surface tones).
- **Status colors** (red/amber/green) only where a decision depends on it. Max 1 status color visible per card.
- No colored text for decoration. Body text = onSurface or onSurfaceVariant. Period.
- **No grey text on dark backgrounds, ever (Ravi).** On dark themes all text is plain white, secondary text included. Hierarchy comes from size and weight, not from greying text out.
- Check both light and dark themes with a contrast checker before shipping a screen.

---

## 4. Corner radius

Use M3 shape scale only **[M3]**:

| Token | dp | Use |
|---|---|---|
| extraSmall | 4 | text field, tooltip |
| small | 8 | chip, small button, tag |
| medium | 12 | card, list group |
| large | 16 | FAB, large card, nav drawer |
| extraLarge | 28 | bottom sheet, dialog |
| full | pill | buttons (M3 default), segmented, avatar |

**[house]**
- **One radius per component type, app-wide.** All cards are 12. All chips are 8. No exceptions.
- Delete `Radius.summary` (24dp, off-scale; 1 use).
- Nested shapes: inner radius = outer radius − padding (12 outer, 4 padding → 8 inner). Never the same radius inside and out.
- No mixing pill and rounded-rect buttons on one screen.

---

## 5. Spacing and margins

**[M3]**
- 4dp grid. Every gap is a multiple of 4.
- Screen margin on phone (compact window): **16dp**.
- Touch target ≥ **48×48dp**, with ≥8dp between targets.

**[house]** Scale: 4 · 8 · 12 · 16 · 24 · 32 · 48.

| Gap | dp |
|---|---|
| icon ↔ label | 8 |
| row internal padding | 16 h / 12 v |
| between rows in a list | 0 (divider) or 8 |
| card internal padding | 16 |
| between cards | 12 |
| between sections | 24–32 |
| top of screen → first content | 16 |
| bottom CTA from screen edge | 16 + nav-bar inset |

- Off-grid tokens to remove: `Spacing.xxs` (2), `icon` (6), `ml` (14), `xl` (20). 24 uses today.
- `Spacing.screen` = 20dp. M3 compact = 16. Decide once; recommend 16.
- Proximity = grouping. Related items close (8), unrelated far (24+). Don't separate with boxes when space does it.

---

## 6. Structure

- **Every non-tab screen has a top app bar: title + back.** No exceptions. Tab roots: title only.
- Content order: what matters now → what to do → details. One primary action per screen. Bottom CTA if the screen has a "done".
- **No cards on cards.** One container level. Lists are rows with dividers, not a stack of cards.
- **One concept, one place.** Syllabus picker appears once in onboarding, not twice. Same rule app-wide.
- Max 3–4 blocks visible above the fold on Today. Everything else below or behind a tap.
- Progressive disclosure: details in sheets, not inline paragraphs.
- Before building a screen: open the matching screen in `prototype/web-app` and match it.

---

## 7. AI-signature ban list

These read as "an AI made this". Never ship any of them.

| Tell | Rule |
|---|---|
| Purple/blue gradients | No gradients on text, buttons or backgrounds |
| Glow / aurora blobs | No radial glows, blurs, glassmorphism |
| Neon accents on dark | Desaturated accent, 1 per screen |
| Emoji as icons or labels | Zero emoji in UI. Use Material Symbols |
| Cards on cards | One container level |
| Colored side bars on every card | Only if color = meaning, and then max 1 per screen |
| Status dots everywhere | Dot only if it's a real state the user acts on |
| Pills and badges on everything | Badge only for a count or a state change |
| Tinted pastel chip + same-hue text | Use the M3 container/on-container pairs |
| Every element rounded 16–24 | Radius by component, per §4 |
| Explainer sentence under every heading | Delete. §1 |
| "Smart", "AI-powered", "magic", "✨" | Never |
| Motivational filler ("You've got this!") | Never |
| Every number hedged "est. ~" | Mark estimate once per screen |
| Uniform heavy shadows | Tonal elevation; shadow only on floating elements |
| Centered everything | Left-align text and lists; center only hero numbers and empty states |
| 3 stat tiles in a row | Only if all 3 drive a decision |

Root cause: models output the statistically most common pattern. Default = generic. Every screen must be checked against this list.

---

## 8. Pre-ship checklist (per screen)

- [ ] Top app bar present (non-tab)
- [ ] No text block > 1 line except body content
- [ ] ≤3 type sizes, all from M3 roles, none < 12sp
- [ ] Every text/background pair ≥ 4.5:1, light and dark
- [ ] One accent color; status colors only where they carry meaning
- [ ] Radii from §4 only; one radius per component type
- [ ] All gaps on 4dp grid; screen margin 16
- [ ] Touch targets ≥ 48dp
- [ ] No §7 items
- [ ] Matches `prototype/web-app` screen

---

## Sources

- M3 type scale: androidx `TypeScaleTokens.kt` — https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/TypeScaleTokens.kt
- M3 shape scale: https://github.com/material-components/material-components-android/blob/master/docs/theming/Shape.md
- M3 writing (sentence case, numerals, front-load, concise): https://m2.material.io/design/communication/writing.html
- M3 color roles: https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-color-scheme/
- Layout: 4dp grid, 16dp compact margin, 48dp targets: https://m1.material.io/layout/metrics-keylines.html and https://www.sap.com/design-system/fiori-design-android/foundations/layout
- WCAG 2.2 contrast: https://www.getstark.co/wcag-explained/perceivable/distinguishable/contrast-minimum/
- NN/g scanning, mobile comprehension, 124% (via secondary): https://toptal.com/designers/ui/microcopy-impact-ux, https://uxcel.com/blog/10-microcopy-tips-to-make-every-word-count-471
- AI UI tells: https://pages.thefountaininstitute.com/posts/7-tells-that-a-ui-is-ai-generated
- Restraint / premium feel: https://vp0.com/blogs/what-makes-an-app-look-professional
