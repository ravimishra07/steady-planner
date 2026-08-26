'use client';

import Link from 'next/link';
import { usePlan } from '@/lib/state';

const SCREENS: [string, string, string, string?][] = [
  ['01', '/exam', 'Pick exam'],
  ['02', '/date', 'Exam date'],
  ['03', '/shape', 'Day shape'],
  ['04', '/hours', 'Hours'],
  ['05', '/cushion', 'Cushion', 'the payoff'],
  ['10', '/paywall', 'Paywall'],
  ['06', '/home', "Home — today's plan"],
  ['07', '/syllabus', 'Home — syllabus'],
  ['08', '/focus', 'Focus session'],
  ['09', '/rebalance', 'Rebalance', '4 days missed'],
];

export default function Index() {
  const { reset } = usePlan();
  return (
    <div className="idx">
      <p className="eyebrow">Exam planner</p>
      <h1 style={{ fontSize: 22, marginBottom: 16 }}>Prototype screens</h1>
      {SCREENS.map(([n, href, label, note]) => (
        <Link key={href} href={href}>
          <em>{n}</em>
          <b>{label}</b>
          {note && <span>{note}</span>}
        </Link>
      ))}
      <button
        className="cta"
        style={{ marginTop: 18 }}
        onClick={() => { reset(); alert('State cleared. Start at 01.'); }}
      >
        Reset saved state
      </button>
    </div>
  );
}
