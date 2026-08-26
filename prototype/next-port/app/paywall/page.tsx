'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Bar } from '@/components/Bar';
import { Body, Foot, Phone } from '@/components/Phone';
import { cushion, examFor } from '@/lib/data';
import { usePlan } from '@/lib/state';

const INCLUDED = [
  'Daily plan rebuilt from your real hours',
  'One-tap rebalance when you miss days',
  'Revision scheduled automatically, spaced out',
  'App blocking during focus sessions',
  'Works fully offline. No account needed.',
];

export default function PaywallScreen() {
  const router = useRouter();
  const { state } = usePlan();
  const c = cushion(state);
  const E = examFor(state.exam);
  const [tier, setTier] = useState<'y' | 'm'>('y');

  return (
    <Phone>
      <Bar back="/cushion" step={0} of={0} />
      <Body>
        <p className="eyebrow">Your plan is ready</p>
        <h1>
          {c.short
            ? `You are ${c.gap} hours short. Here is the way through.`
            : `You have ${Math.abs(c.gap)} hours of room. Protect it.`}
        </h1>
        <p className="sub">Keep it updated every single day until you sit the paper.</p>

        <div className="mine">
          <div className="row"><span>Exam</span><b>{E.name}</b></div>
          <div className="row"><span>Days left</span><b>{state.days}</b></div>
          <div className="row"><span>Syllabus needs</span><b>{c.need} hrs</b></div>
          <div className="row"><span>You have</span><b className={c.short ? 'warn' : ''}>{c.have} hrs</b></div>
        </div>

        <button className={tier === 'y' ? 'plan sel' : 'plan'} onClick={() => setTier('y')}>
          <div className="n"><b>Till your exam</b><span>One payment, covers the full attempt</span></div>
          <span className="best">Best</span>
          <span className="p">&#8377;399</span>
        </button>
        <button className={tier === 'm' ? 'plan sel' : 'plan'} onClick={() => setTier('m')}>
          <div className="n"><b>Monthly</b><span>Cancel any time</span></div>
          <span className="p">&#8377;99</span>
        </button>

        <div className="inc">
          {INCLUDED.map(x => <div key={x}><i>&#10003;</i>{x}</div>)}
        </div>
      </Body>
      <Foot>
        <button className="cta" onClick={() => router.push('/home')}>Unlock my plan</button>
        <button className="ghost" onClick={() => router.push('/home')}>Not now</button>
      </Foot>
    </Phone>
  );
}
