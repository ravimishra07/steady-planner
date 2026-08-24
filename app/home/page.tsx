'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { HomeHeader } from '@/components/HomeHeader';
import { Phone } from '@/components/Phone';
import { cushion, examFor, todayBudget } from '@/lib/data';
import { usePlan } from '@/lib/state';

type Block =
  | { t: string; brk: true }
  | { t: string; brk?: false; n: string; s: string; k: 'read' | 'prac' | 'rev' };

const PLAN: Block[] = [
  { t: '06:30', n: 'Geometry — Triangles: similarity', s: 'Your book, sections 4.1–4.4 · 90 min', k: 'read' },
  { t: '08:00', brk: true },
  { t: '08:15', n: 'Geometry — 40 practice questions', s: 'Previous-year set, 2019–2023', k: 'prac' },
  { t: '09:30', n: 'Revision: Percentage', s: 'Done 6 days ago · 4th touch', k: 'rev' },
  { t: '10:00', n: 'Current Affairs — this week', s: 'Rolling topic · 30 min daily', k: 'read' },
];

const LBL = { read: 'Read', prac: 'Practice', rev: 'Revise' } as const;

export default function HomeScreen() {
  const router = useRouter();
  const { state } = usePlan();
  const c = cushion(state);
  const E = examFor(state.exam);

  // Ticked blocks are session-local — the prototype does not persist them.
  const [ticked, setTicked] = useState<Record<number, boolean>>({});

  // Date formatting must not run on the server: the timezone differs.
  const [dayLabel, setDayLabel] = useState('');
  useEffect(() => {
    const t = new Date();
    setDayLabel(
      t.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' }) +
      ' · ' + todayBudget(state) + ' hrs planned' +
      (state.place ? ' · ' + state.place : '')
    );
  }, [state]);

  return (
    <Phone>
      <HomeHeader exam={E} days={state.days} c={c} title="Today" tab="plan" />
      <div className="pane on">
        <p className="daylabel">{dayLabel || ' '}</p>

        {PLAN.map((b, i) => (
          <div className="block" key={i}>
            <div className="time">{b.t}</div>
            <div className="cardw">
              {b.brk ? (
                <div className="tk brk">
                  <div className="t">
                    <b style={{ fontWeight: 400, color: 'var(--ink-3)', fontSize: 12.5 }}>
                      Break &mdash; 15 min
                    </b>
                  </div>
                </div>
              ) : (
                <div className={b.k === 'rev' ? 'tk rev' : 'tk'}>
                  <div
                    className={ticked[i] ? 'box chk' : 'box'}
                    role="checkbox"
                    aria-checked={!!ticked[i]}
                    tabIndex={0}
                    onClick={() => setTicked(p => ({ ...p, [i]: !p[i] }))}
                  />
                  <div className={ticked[i] ? 't done' : 't'}>
                    <b>{b.n}</b>
                    <span>{b.s}</span>
                  </div>
                  <div className={`tag ${b.k}`}>{LBL[b.k]}</div>
                </div>
              )}
            </div>
          </div>
        ))}

        <button className="cta" style={{ marginTop: 14 }} onClick={() => router.push('/focus')}>
          Start focus session
        </button>
        <button className="ghost" onClick={() => router.push('/rebalance')}>
          I missed some days &#8594;
        </button>
      </div>
    </Phone>
  );
}
