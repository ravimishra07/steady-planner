# Exam planner — Next.js prototype

Android app prototype, running as a web app. Every screen is a phone frame on a dark page.

```bash
npm run dev
```

## Routes

| Route | Screen |
|---|---|
| `/` | Prototype index + reset state |
| `/exam` | 1 · Pick exam |
| `/date` | 2 · Exam date |
| `/shape` | 3 · Day shape |
| `/hours` | 4 · Hours + study place |
| `/cushion` | 5 · Cushion — the payoff screen |
| `/paywall` | 10 · Paywall |
| `/home` | 6 · Today's plan |
| `/syllabus` | 7 · Syllabus tree |
| `/focus` | 8 · Focus session |
| `/rebalance` | 9 · Missed days |

## Layout

- `lib/data.ts` — exams, syllabus trees, the scheduler (`cushion()`), state shape.
  **Product rule: topic names and effort estimates only. No notes, questions, or solutions.**
- `lib/state.tsx` — `PlanProvider` / `usePlan()`. localStorage-backed under key `plan`.
  Renders defaults on the server, hydrates in an effect, so there is no mismatch.
- `app/globals.css` — the design system, ported from `prototype/tokens.css`.
  Font families come from `next/font` variables set in `app/layout.tsx`.
- `components/` — `Phone`/`Body`/`Foot`, `Bar` (back + step dots), `HomeHeader` (dark header + tabs).
- `prototype/` — the original static HTML prototype, kept for reference.

## Known prototype gaps

- `SYLLABUS` only has `cgl`. Other exams fall back to it.
- `/home` renders a hardcoded `PLAN` array; it is not generated from the syllabus.
- Topic ticks on `/syllabus` persist but do not feed back into `cushion()`.
- `/rebalance` slipped-topic list is hardcoded; the arithmetic below it is live.
