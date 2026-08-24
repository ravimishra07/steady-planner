'use client';

import { useRouter } from 'next/navigation';
import { Bar } from '@/components/Bar';
import { Body, Foot, Phone } from '@/components/Phone';
import { cushion } from '@/lib/data';
import { usePlan } from '@/lib/state';

const SLIPPED: [string, number][] = [
  ['Geometry — Circles', 6],
  ['Mensuration — Solids', 7],
  ['Revision: Ratio & Proportion', 2],
  ['Current Affairs × 4 days', 2],
];

export default function RebalanceScreen() {
  const router = useRouter();
  const { state } = usePlan();
  const c = cushion(state);
  const lost = SLIPPED.reduce((a, b) => a + b[1], 0);
  const after = c.gap + lost;

  return (
    <Phone>
      <Bar back="/home" step={4} />
      <Body>
        <p className="eyebrow">4 days missed</p>
        <h1>Here is what slipped.</h1>
        <p className="sub">Nothing is lost. It has to go somewhere, and you choose where.</p>

        <div className="slip">
          {SLIPPED.map(([n, h]) => (
            <div className="r" key={n}><b>{n}</b><em>{h} hrs</em></div>
          ))}
        </div>

        <p className="arrow">&#8595;</p>
        <p className="eyebrow">Pick one</p>
        <div className="fix">
          <div className="row">
            <b>+{(lost / state.days).toFixed(1)}h</b>
            <span>
              Spread all {lost} hrs across your remaining days. No day gains more than{' '}
              {Math.round((lost / state.days) * 60)} minutes.
            </span>
          </div>
          <div className="row">
            <b>&minus;2</b>
            <span>Drop Mensuration &mdash; Solids and one revision pass. Costs about 1.5 marks.</span>
          </div>
          <div className="row">
            <b>{Math.ceil(lost / ((state.wd + state.we) / 2))}d</b>
            <span>
              Push your target date back {Math.ceil(lost / ((state.wd + state.we) / 2))} days
              and keep everything.
            </span>
          </div>
        </div>

        <div className={after > 0 ? 'verdict short' : 'verdict ok'}>
          <h2>{Math.abs(after)} hours {after > 0 ? 'short' : 'to spare'}</h2>
          <p>This is where you land after rebalancing. Your cushion updates the moment you pick.</p>
        </div>
      </Body>
      <Foot>
        <button className="cta" onClick={() => router.push('/home')}>Spread it across my days</button>
      </Foot>
    </Phone>
  );
}
