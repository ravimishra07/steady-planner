# Design Guidelines

Use this file for any visual, UX, or frontend task in this repository.

## Scope first

- Change only the surface, files, and behavior the user explicitly asks for.
- Do not turn a small styling request into a broader redesign, audit, refactor, or product task.
- Do not touch Android code when the request is prototype-only. Do not touch the prototype when the request is Android-only.
- Preserve unrelated uncommitted work.

## Prototype and product

- `android/` is the shipping product.
- `prototype/` is a disposable visual reference. It is not production code.
- Treat `prototype/web-app/` as the interactive reference when the user asks to work on a prototype.
- Never infer that prototype changes should be ported to Android. Wait for an explicit request.

## Permission boundaries

- Do not open, control, reload, navigate, inspect, or take screenshots in a browser unless the user explicitly asks for browser use in the current request.
- Do not start a local server, emulator, simulator, or device workflow unless the user explicitly asks.
- Do not claim visual verification unless it was explicitly requested and actually completed.

## Design decisions

- Work from the exact screen, image, or file the user identifies. If the intended visual target is unclear, ask before changing it.
- Make the smallest change that satisfies the request.
- Preserve the established layout, spacing, color tokens, typography, and component vocabulary unless the user asks to change them.
- Prefer semantic CSS/classes and shared components over one-off overrides when a change is intended to be reused.
- Keep selected states, focus states, disabled states, labels, and touch targets understandable and accessible.

## Android rules

- Read `ARCHITECTURE.md` before Android UI edits.
- Use `:core:design` tokens and shared components. Do not add raw colors, spacing, sizes, or radii in feature UI code.
- Do not hand-edit generated `tokens.json` or `syllabus_cgl.json` exports.

## Delivery

- State exactly what changed and where.
- Report verification honestly. Static checks do not prove visual parity.
- Do not commit, push, deploy, or modify files outside the requested scope unless the user explicitly asks.
