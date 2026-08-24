# Steadyline — exam planner

A study planner that starts from arithmetic: a syllabus needs some number of
hours, your calendar supplies another, and the gap between them is the thing
that matters.

## Run it

```sh
sh design/serve.sh
```

Opens <http://localhost:8765/app.html>. Same URL works from a phone on the
same Wi-Fi.

[design/SCREENS.md](design/SCREENS.md) lists every screen, what it is for and
how finished it is. [design/README.md](design/README.md) covers routes, storage
keys and architecture.

## Layout

| Path | What it is |
|---|---|
| `design/` | **The app.** Plain ES modules, hash-routed, no build step. This is what deploys. |
| `design-archive/screens/` | Hi-fi mockups as standalone HTML. Design reference only — they use an older palette. |
| `prototype/` | The original static HTML screens, kept for reference. |
| `next-port/` | A Next.js port of the same flow. Superseded by `design/`. |

### Why `next-port/` is not at the repo root

Netlify runs framework detection against the base directory. A root
`package.json` listing `next` makes it auto-install `@netlify/plugin-nextjs`,
which then fails the build hunting for Next.js output in `design/`. Keeping the
port in a subdirectory stops the detection.

To work on it: `npm --prefix next-port install && npm --prefix next-port run dev`

## Deploy

Netlify, from the repo root. `netlify.toml` publishes `design/` with no build
command; `design/_redirects` serves the app at `/`. Nothing to install.

## What is real and what is not

Real: the cushion arithmetic, the SSC CGL syllabus tree (4 sections, 49 topics,
634 hrs, with nested subtopics and per-source attribution), syllabus progress in
hours, the focus timer, theming.

Not yet: the daily plan is fixed demo data rather than generated from the
syllabus; only SSC CGL has a tree; payments are not wired. There is no backend —
everything is stored on the device.
