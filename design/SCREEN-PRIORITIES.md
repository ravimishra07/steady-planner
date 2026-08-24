# Screen priorities — what to perfect first

**Last updated:** Aug 2026 · prototype phase (`app.html`)

---

## TL;DR — start here

> **Perfect `Today` (Home) first.**

It is the screen users open **every day**. Onboarding sells the promise once; Today delivers it daily. If Today feels right on a real phone, the app feels real — even if Syllabus and Focus are still rough.

**File to own:** `design/views/today.js` + `design/app-views.css` (Today section)  
**Reference artboards:** `Today.dc.html`, `home.html`  
**Demo URL:** `app.html#/today` or `app.html?seed=1`

---

## Why Today wins

| Reason | Detail |
|--------|--------|
| **Highest frequency** | Opened 1–3× per day vs onboarding (once) or Rebalance (rare) |
| **Core loop** | See plan → check blocks → start focus → feel progress |
| **Emotional hook** | “% complete”, hours done, cushion strip = am I on track? |
| **Hub** | Links to Focus (FAB), Rebalance (cushion), implicit Syllabus (topics in blocks) |
| **Already 70% built** | Interactive blocks, demo data, tab nav — polish, not greenfield |

---

## Screen maturity snapshot

| # | Screen | Route | Maturity | User sees it… |
|---|--------|-------|----------|----------------|
| **1** | **Today / Home** | `#/today` | **~70%** — interactive, needs polish | Daily |
| 2 | Onboarding (5 steps) | `#/onboarding/*` | ~60% — flows, static inputs | Once |
| 3 | Syllabus | `#/syllabus` | ~75% — full tree, heavy UI | Weekly |
| 4 | Cushion verdict | onboarding step | ~50% — artboard only feel | Once |
| 5 | Focus | `#/focus` | ~40% — static timer | Daily (if wired) |
| 6 | Rebalance | `#/rebalance` | ~45% — static options | When behind |
| 7 | Progress | `#/progress` | ~5% — stub | Rare |
| 8 | More | `#/more` | ~50% — demo nav only | Rare |
| 9 | Paywall | not in app | ~30% — artboard only | TBD |

---

## Recommended priority order

### Tier 1 — do now (one screen at a time)

#### 1. Today / Home ← **YOU ARE HERE**
**Goal:** Feels like a shipped iOS daily planner on phone.

Must feel perfect:
- [ ] Full-bleed phone layout (safe areas, no awkward gaps)
- [ ] Today card: date line, scheduled hours, progress bar, % copy
- [ ] Time blocks: morning / afternoon / evening groups
- [ ] Checkbox tap → instant visual feedback (strike-through, progress updates)
- [ ] Cushion strip: red when short, green when buffer — tap → Rebalance
- [ ] FAB “Start focus session” → opens Focus with **current block** context
- [ ] Empty state when no plan → clear CTA to onboarding
- [ ] Typography 100% tokens (no stray px)
- [ ] Scroll: only the list scrolls; top bar + dock stay fixed

Nice to have (after core):
- [ ] Haptic-style press states on cards
- [ ] Subtle enter animation for completed blocks
- [ ] “Current” block highlighted (next unchecked item)

**Do not touch yet:** real scheduling algorithm, notifications, backend.

---

#### 2. Focus (after Today is locked)
**Goal:** FAB on Today opens a session that matches the selected block.

Why second: Today’s FAB is useless without a credible Focus screen. Together they form the **do the work** loop.

Must feel perfect:
- [ ] Receives block title + tag + duration from Today
- [ ] Countdown (can stay demo/static timer first)
- [ ] End session → return to Today with block marked done
- [ ] Full-screen, no tab bar distraction

---

#### 3. Onboarding (Exam → Cushion)
**Goal:** First-run on phone feels smooth; ends on Today with a plan.

Why third: Already works in `app.html`. Polish after the destination (Today) is right — otherwise you perfect a funnel into a weak home screen.

Must feel perfect:
- [ ] Each step: back, progress dots, CTA pinned bottom
- [ ] Selections persist (exam, shape, hours)
- [ ] Cushion screen uses **real math** from `data.js` (already partial)
- [ ] “Start day 1” lands on Today with demo blocks populated

---

### Tier 2 — after daily loop works

#### 4. Syllabus
Heavy but mostly done. Prioritize **readability on phone** (tap targets, scroll performance) over new features.

#### 5. Rebalance
Only matters when cushion strip is red. Polish when Today → Rebalance → back flow is tested on phone.

#### 6. Progress tab
Stub today. Design after Today + Syllabus share the same “hours done” numbers.

---

### Tier 3 — defer

| Screen | Why wait |
|--------|----------|
| Paywall | No monetization logic in prototype |
| More (settings) | Demo nav only; not user-facing value |
| Progress charts | Needs real history data model |
| Canvas / `.dc.html` artboards | Keep for design review; don’t merge into app yet |

---

## What “perfect one screen” means (checklist)

Use this for **Today** before moving on:

### Visual
- [ ] Matches `SamSystem.dc.html` tokens (type, color, spacing)
- [ ] Light + dark both look intentional
- [ ] 390px and full-width phone (no desktop “card in center” feel in app shell)

### Interaction
- [ ] Every tappable thing has a visible pressed state
- [ ] No full page reloads inside `app.html`
- [ ] Back / tab behavior is predictable

### Content
- [ ] Copy is realistic (SSC CGL, real topic names, believable hours)
- [ ] Empty, partial, and “behind syllabus” states all demo-able

### Phone test
- [ ] Tested on Safari iOS via Wi‑Fi (`192.168.x.x:8765/app.html`)
- [ ] Thumb can reach FAB and bottom tabs
- [ ] Scroll doesn’t fight the browser rubber-band

---

## Suggested focus plan

| Week | Focus | Done when… |
|------|-------|--------------|
| **This week** | Today only | You’d show it to someone and say “this is the app” |
| Next | Focus + Today wire-up | FAB → timer → mark done → progress updates |
| Then | Onboarding pass | Fresh install → onboarding → lands on polished Today |
| Later | Syllabus phone pass | Tree scrolls smoothly; checkboxes feel as good as Today |

---

## Files map (Today focus)

```
design/
  views/today.js      ← logic + HTML for Today
  app-views.css       ← Today styles (search "Top bar")
  data.js             ← blocks, cushion, plan state
  sam-tokens.css      ← colors, type scale
  sam-shared.css      ← cards, tags, list rows
  home.html           ← older standalone version (keep in sync or deprecate)
  Today.dc.html       ← static reference artboard
```

---

## Decision log

| Question | Answer for now |
|----------|----------------|
| One screen or whole app? | **One screen** — Today |
| Standalone `home.html` or `app.html`? | **`app.html`** is the product demo; sync changes from Today view |
| Real data or static? | **Static demo data** in `data.js` + `localStorage` — enough to fake the loop |
| iOS or Android first? | **iOS** (SF Pro, safe areas, 390px) — Android can follow |

---

## Next action

1. Open `app.html?seed=1` on your phone  
2. Stay on **Today** tab only  
3. List what feels wrong (spacing, copy, missing state, broken tap)  
4. Fix only `views/today.js` + Today CSS until that list is empty  

**Do not start Syllabus or Progress until Today is signed off.**
