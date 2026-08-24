# Screens

Everything reachable in the app, what each is for, and how finished it is.
The app is `app.html`; every screen is a hash route.

Run it: `sh design/serve.sh` → <http://localhost:8765/app.html>
Live: <https://steady-planner.netlify.app>

| Query | Effect |
|---|---|
| `?seed=1` | Load a demo plan and go straight to Today |
| `?reset=1` | Erase everything and restart onboarding |
| `?theme=light` / `?theme=dark` | Force a theme for one load |

---

## Onboarding — five steps, once

Reached automatically when no plan is stored. Answers are held in
`sessionStorage` until the last step, so backing out changes nothing.

| # | Route | Screen | What it asks / shows |
|---|---|---|---|
| 1 | `#/onboarding/exam` | Pick exam | Six exams with applicant counts and paper structure. Only SSC CGL has a syllabus tree, so only it shows hours. |
| 2 | `#/onboarding/date` | Exam date | Countdown broken into weeks, with a scale from today to the exam. |
| 3 | `#/onboarding/shape` | Day shape | Full-time / working / college — each shows the weekday and weekend hours it implies. |
| 4 | `#/onboarding/hours` | Hours per day | Two live sliders and a study spot. Recomputes total hours before the exam as you drag. |
| 5 | `#/onboarding/cushion` | Your plan | **The payoff.** The gap as one number, a self-labelling gauge, and three ways to close it. |

`#/splash` is the launch screen; it forwards to Today or onboarding.

---

## Main tabs

| Route | Tab | State |
|---|---|---|
| `#/today` | Today | Week strip, day timeline, gaps between blocks, FAB into Focus |
| `#/syllabus` | Syllabus | Subject tabs over a three-level expandable tree |
| `#/focus` | Focus | Working timer — start, pause, resume, stop |
| `#/progress` | Progress | Cushion, today's hours, per-section syllabus coverage |
| `#/more` | More | Account, settings, rebalance, upgrade, policies |

### Today
Calendar week strip, then the day as a timeline with the free gaps named
("4h 30m free"). Blocks carry a subject badge and a Read / Practice /
Revise tag. **The blocks are fixed demo data — not generated from the
syllabus yet.** That is the biggest remaining piece of real work.

### Syllabus
Subject tabs — Quant / Reasoning / GA / English — and below them only that
subject's topics. Every parent has a circular expand toggle and its children
hang off a thread rail: topic → subtopic → sub-subtopic, each collapsing on
its own. Checks are circular and tri-state; ticking a parent ticks
everything under it, and a partly-done parent shows a filled centre.

Hours belong to the topic. Expanding splits them across the children in
half-hour steps that add back up exactly — Analogies 7h becomes 2.5 + 2.5 + 2.

Three spacing variants exist while the density is being decided:

| Route | Variant | Topic row | Topics on screen |
|---|---|---|---|
| `#/syllabus` | A — dense | 48px | 11 |
| `#/syllabus-c` | C — midway | 58px | 9 |
| `#/syllabus-b` | B — roomy, topic cards | 64px | 5 |

Side by side: <http://localhost:8765/syllabus-compare.html>
All three share one tick state, so ticking in one shows in the others.
**Pick one and delete the other two** — they are a decision aid, not a feature.

### Focus
Wall-clock timer, so a running session survives route changes, the tab
being backgrounded, and a reload. Start / pause / resume / stop, a progress
arc, session beads, the task it is logged against, and the app-blocking
panel. Blocking is a mock — the web cannot hide other apps.

### Progress
Every number here is real: the cushion, today's hours from checked blocks,
and per-section syllabus coverage computed from ticked leaves, counting
partly-finished topics proportionally. No history is stored, so there are
no trends yet.

### More
Account, Settings, Rebalance, Upgrade, Redo onboarding, Privacy, Terms, About.

---

## Secondary screens

| Route | Screen | Notes |
|---|---|---|
| `#/rebalance` | Rebalance | What slipped with a total, three recovery options, and where each leaves you before you commit |
| `#/paywall` | Upgrade | Plan summary with the gauge, two tiers, what is included. **Payments are not wired** — the buttons say so |
| `#/settings` | Settings | Theme (System / Light / Dark), weekday and weekend hours, study spot, focus length, export, clear |
| `#/account` | Account | Name, plan summary, sign out |
| `#/policy/privacy` | Privacy | What is stored, where, and how to delete it |
| `#/policy/terms` | Terms | What the app is and is not |
| `#/policy/about` | About | Version, syllabus sourcing, honest status |

---

## Not the app

| Path | What |
|---|---|
| `canvas-view.html` | Design canvas — every screen side by side, light and dark |
| `syllabus-compare.html` | The three syllabus variants side by side |
| `home.html`, `syllabus.html` | Standalone prototype pages that predate the app shell |
| `*.dc.html` | Design artboards used by the canvas |
| `../design-archive/screens/` | Earlier hi-fi mockups in a different palette — reference only |

---

## What is real, and what is not

**Real:** the cushion arithmetic; the SSC CGL syllabus tree (4 sections,
49 topics, 634 hrs, with nested subtopics and per-source attribution);
syllabus progress in hours; the focus timer; theming; everything stored
on-device.

**Not yet:**

- Today's plan is fixed demo data, not generated from the syllabus
- Only SSC CGL has a tree; the other five exams fall back to it
- Tier 2 exists in the data but is not reachable since the Syllabus rebuild
- Payments are not connected
- There is no account and no server; "sign out" erases local data
- No history is kept, so Progress cannot show trends

---

## Storage

| Key | Holds |
|---|---|
| `plan` | Exam, date, hours, place, name, checked time blocks |
| `syllabus_ui` | Ticked syllabus leaves, open nodes, current subject |
| `focus` | Current or last focus session |
| `sam_theme` | `light` \| `dark` \| `system` |
