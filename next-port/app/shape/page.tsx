'use client';

import { useRouter } from 'next/navigation';
import { Bar } from '@/components/Bar';
import { Body, Foot, Phone } from '@/components/Phone';
import { WORK } from '@/lib/data';
import { usePlan } from '@/lib/state';

export default function ShapeScreen() {
  const router = useRouter();
  const { state, patch } = usePlan();

  return (
    <Phone>
      <Bar back="/date" step={3} />
      <Body>
        <p className="eyebrow">Step 3 of 4</p>
        <h1>How are your days shaped?</h1>
        <p className="sub">Working aspirants get a different plan, not just a shorter one.</p>
        {WORK.map(w => (
          <button
            key={w.id}
            className={state.work === w.id ? 'opt sel' : 'opt'}
            onClick={() => patch({ work: w.id, wd: w.wd, we: w.we })}
          >
            {w.t}
            <span>{w.s}</span>
          </button>
        ))}
      </Body>
      <Foot>
        <button className="cta" disabled={!state.work} onClick={() => router.push('/hours')}>
          Continue
        </button>
      </Foot>
    </Phone>
  );
}
