# Design learnings

What each screen taught us. Short. Add to this every time a screen gets fixed.
Rules themselves live in `MOBILE_DESIGN_RULES.md`; this file is the evidence.

---

## Theme ("Choose a theme")

**Title**
- Say the action: `Choose a theme`, not `Theme`.
- Left-aligned. Centered was tried and dropped. Titles start at the margin like every other screen.

**Continue button**
- Stays accent-filled. White-on-dark / black-on-light was tried and looked odd. Reverted.

**Accent colours: equal intensity**
- Problem: each accent had different brightness/saturation, so text contrast changed per colour (purple fine, green muddy).
- Rule (Material tonal roles): same tone AND same chroma for every accent, only hue changes.
  - Dark backgrounds: fill = tone 80 (chroma 36), text on it = neutral near-black #1C1B1F (no tint) → ~11:1 for all five. Tone-20 same-hue text was readable but looked tinted; rejected.
  - Light backgrounds: fill = tone 40, text white, chroma 48 → ~6.5:1 for all five.
- Swatch circles use the same fill, so what you pick is exactly what buttons get.
- Apple system colours rejected for this: hand-tuned, different brightness per colour (green/orange far lighter than blue/purple).

**Accent swatches**
- No card around each swatch. A box holding one circle = repeated-card pattern. The circle is the control.
- Circle 48dp, tap target ≥48dp.
- All swatches same lightness and chroma (OKLCH), only hue changes. Otherwise green and amber look dull next to blue and rose.
- Selected = tick inside circle + label turns bold. One mark, not tick + outline box.
- Labels 12sp grey, white only when selected. Color carries the meaning.
- First and last swatch flush with the content edges. Row must line up with the block below it.

- Accent sits just above Continue: the 5 circles inside one fully rounded pill outline (1dp), Continue below it. No panel around both; a bordered sheet with top corners was tried and was too heavy.
  - No "Accent" label, no colour names: the circles say it (names kept as accessibility labels only).
  - Circles 36dp (48dp tap area), 12dp between pill and Continue.

**Background previews**
- No "Background" label. The carousel of screens says it. Carousel starts 32dp under the title.
- Was 3 layers: screen → grey card → preview with its own border. Box in a box in a box.
- Now 2 layers: the preview IS the tile. Label sits under it, outside.
- Selected = 2dp accent ring with 2dp gap around the preview + bold label.
- Unselected keep a 1dp outline so light previews don't vanish on light backgrounds.
- Previews look like phone screens, not square boxes. Build them as a real 360×780 screen scaled to 0.4, so every part keeps true proportion: 16dp margin → 6, 56dp list row → 22, 56dp button → 22 and fully round, top bar space → 26.
  - Preview = our real Today screen in miniature, not an abstract "lines + box": 2 header lines, a hero card (accent progress line), 2 block rows (accent ring + text), bottom nav with the active pill in accent. Big empty middle is fine; real screens have it.
  - Draw the thumbnail content large enough to read (about 1.5× true scale: 10dp side margin, 36dp rows, 88dp hero, 40dp nav). Exact 0.4 scale made everything tiny inside a big frame.
  - Proportion beats detail. One giant block (too abstract) and stacks of lines (too busy) both failed. Recognisable structure at true scale is what reads as "a screen".
- Carousel, not a grid. Centered preview 144dp wide, neighbours peek left and right, scale (−18%) and fade by distance from center, snap to center. Centered = selected; the app recolours live on settle. Name + page dots under it. Tap a peeking preview to bring it to center.
  - Tried first: one row of 5 (too small, cramped) and a 3 + 2 grid (big, second row below the fold, empty slot). Both rejected.
  - Compose: `HorizontalPager` with horizontal `contentPadding` + `graphicsLayer` scale/alpha from page offset (or M3 `HorizontalMultiBrowseCarousel`).
- The mini button uses the chosen accent, so both choices preview together.
- (Android) FlowRow + weight measured before widening and clipped the previews; use a fixed Row with weights.

**Vertical spacing: 3 levels only**

| Between | dp |
|---|---|
| Title → first section | 32 |
| Section → section | 32 |
| Section label → its content | 12 |
| Items inside a block | 8 (12 for preview grid with labels) |

- Title must get at least as much space as sections get from each other. 24 under the title vs 32 between sections read upside down.
- Label → content (12) must be bigger than item → item (8), or the label stops owning its block.
- Two values (8, 32) everywhere looks mechanical. Three levels give rhythm.

**Open**
- Two selection languages on one screen: tick (swatch) vs ring (preview). Decide if both stay.

---

## How do you study?

- Plain M3 list (dividers + radios) read like a settings page and boxed the options in lines.
- Now an M3 Expressive grouped list (Android 16 Settings / Pixel setup style): 3 rows on the raised surface, 2dp gaps, no dividers, 20dp outer corners, 4dp inner corners, 56dp rows.
- Leading icon = plain 24dp icon (M3 list spec), grey; accent colour when selected. Selected label goes bold. Radio on the right.
  - 40dp avatar circles were tried: within spec but meant for people/items; too heavy for a one-line row, the filled selected circle outshouted the label.
- Kept at the top under the title (not moved down to the CTA).

---

## Fixed non-study hours

- Title `Fixed non-study hours`, no subtext. Section label `Suggested` with a lightbulb (not sparkles).
- Defaults match the app: Lunch 1 to 2 pm and Dinner 8:30 to 9:30 pm every day, plus Coaching or School seeded from the previous answer.
- Item cards stay as in the app (name, duration, edit, ✕, Starts/Ends, S M T W T F S). A collapsed-rows version with time pills and day patterns was tried and reverted.
- Time fields = M3 outlined text field (56dp, label sits in the border notch, value `4:00 pm`, clock icon), 12dp gap between the pair so both sides balance. Tap opens the M3 time picker dialog (hour/minute boxes, AM/PM, clock dial, Cancel/OK). Compose: `OutlinedTextField` (read-only) + `TimePicker` in a dialog.
- Rejected earlier: a day-bar redesign (too clever; had to be decoded).

---

## Finish-by date ("Finish the syllabus by")

- Title says what the date is for. `Set an end date` confused: end of what?
- One screen, one purpose. Only one subtext (`146 days to go`). No revision talk here.
- Two choices = segmented button, not two big cards with paragraphs.
- The answer (chosen date) is bigger than everything except the title.
- Calendar first, under the choice, above the fold.
- Only design what Compose can build: stock DatePicker can't mark one specific day. Exam shown as a row under the calendar; days after it disabled.

---

## Global rules from review

- No grey text on dark backgrounds. Secondary text, labels, subtitles: plain white on dark. (Ravi, 2026-10-08)

---

## Process

- Edit the main mock (`prototype/mocks/onboarding.html`) in place. No "now vs proposed" phones, no notes lists in the page.
- Don't restyle screens Ravi already approved while working on others.
- Check the result in the preview pane before saying it's done. The pane is narrower than 360dp; the phone must shrink to fit or things look clipped.
- Roboto stays. The impeccable detector flags it as "overused", but it's Android's system font.
