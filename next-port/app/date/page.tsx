'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Bar } from '@/components/Bar';
import { Body, Foot, Phone } from '@/components/Phone';
import { daysUntil } from '@/lib/data';
import { usePlan } from '@/lib/state';

export default function DateScreen() {
  const router = useRouter();
  const { state, patch, ready } = usePlan();
  // null = nothing chosen yet this session and nothing stored
  const [touched, setTouched] = useState(false);
  const [ownTarget, setOwnTarget] = useState(false);

  const chosen = touched || (ready && !!state.date);
  const label = ownTarget
    ? <>days &mdash; your own target,<br />change it any time</>
    : 'days from today';

  return (
    <Phone>
      <Bar back="/exam" step={2} />
      <Body>
        <p className="eyebrow">Step 2 of 4</p>
        <h1>When is the exam?</h1>
        <p className="sub">Everything gets planned backward from this date.</p>
        <div className="countdown">
          <span className="n">{chosen ? state.days : '—'}</span>
          <span className="l">{label}</span>
        </div>
        <div className="field">
          <label htmlFor="d">Exam date</label>
          <input
            id="d"
            type="date"
            value={state.date ?? ''}
            onChange={e => {
              if (!e.target.value) return;
              patch({ date: e.target.value, days: daysUntil(e.target.value) });
              setOwnTarget(false);
              setTouched(true);
            }}
          />
        </div>
        <button
          className="ghost"
          onClick={() => { patch({ date: null, days: 150 }); setOwnTarget(true); setTouched(true); }}
        >
          Date not announced yet &#8594;
        </button>
      </Body>
      <Foot>
        <button className="cta" disabled={!chosen} onClick={() => router.push('/shape')}>
          Continue
        </button>
      </Foot>
    </Phone>
  );
}
