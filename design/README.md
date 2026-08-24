# Steadyline — exam planner prototype

Plain ES modules. No build step, no framework, no backend.

## Run it

```sh
sh design/serve.sh
```

Serves `design/` on <http://localhost:8765> with caching disabled, and opens the app.
Same URLs work from your phone on the same Wi-Fi: `http://<your-mac-ip>:8765/app.html`.

| URL | What |
|---|---|
| `app.html` | The app |
| `app.html?seed=1` | Load a demo plan |
| `app.html?reset=1` | Wipe everything, restart onboarding |
| `app.html?theme=light` | Force a theme for one load |
| `canvas-view.html` | Design canvas — all artboards side by side |

## Screens

`splash → onboarding (exam · date · shape · hours · cushion) → today`

Tabs: **Today · Syllabus · Focus · Progress · More**
From More: Rebalance, Upgrade, Redo onboarding, Settings, Account, Privacy, Terms, About.

## How it is put together

| File | Role |
|---|---|
| `app.js` | Hash router + shell. One `ROUTES` table is the whole map. |
| `data.js` | Exams, scheduler (`cushion()`), plan state |
| `syllabus-cgl.js` | SSC CGL tree — 4 sections, 49 topics, 634 hrs, nested subtopics with source attribution |
| `syllabus-progress.js` | Turns ticked syllabus leaves into hours (partial topics count proportionally) |
| `focus-timer.js` | Focus session state. Wall-clock based, so it survives route changes, backgrounding and reload |
| `sam-theme.js` | Applies the theme before first paint. Must stay a classic script in `<head>` |
| `theme.js` | ES-module wrapper over it, for views |
| `sam-tokens.css` | All colour/type/spacing tokens. Dark and light, 33 tokens each |
| `views/*.js` | One `mount<Name>(root, ctx)` per screen, returning an optional teardown |

### Storage

Everything is on-device. There is no account and no server.

| Key | Holds |
|---|---|
| `plan` | Exam, date, hours, place, name, checked time blocks |
| `syllabus_ui` | Ticked syllabus leaves |
| `focus` | Current/last focus session |
| `sam_theme` | `light` \| `dark` \| `system` |

## Deploy

Netlify, from the repo root — `netlify.toml` sets `publish = "design"` and no build command.
`_redirects` serves the app at `/`.

## Still demo data

- The daily plan in `todayBlocks()` is fixed, not generated from the syllabus.
- Only SSC CGL has a syllabus tree; the other five exams fall back to it.
- Payments are not wired. The paywall buttons say so rather than pretending.
