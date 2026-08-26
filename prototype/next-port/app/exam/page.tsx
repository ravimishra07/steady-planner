'use client';

import { useRouter } from 'next/navigation';
import { Bar } from '@/components/Bar';
import { Body, Foot, Phone } from '@/components/Phone';
import { EXAMS } from '@/lib/data';
import { usePlan } from '@/lib/state';

export default function ExamScreen() {
  const router = useRouter();
  const { state, patch } = usePlan();

  return (
    <Phone>
      <Bar step={1} />
      <Body>
        <p className="eyebrow">Step 1 of 4</p>
        <h1>Which exam are you preparing for?</h1>
        <p className="sub">Pick the one you&rsquo;re actually sitting. You can add a second exam later.</p>
        {EXAMS.map(e => (
          <button
            key={e.id}
            className={state.exam === e.id ? 'exam sel' : 'exam'}
            onClick={() => patch({ exam: e.id })}
          >
            <div>
              <h3>{e.name}</h3>
              <p>{e.meta}</p>
            </div>
            <div className="tick" />
          </button>
        ))}
      </Body>
      <Foot>
        <button className="cta" disabled={!state.exam} onClick={() => router.push('/date')}>
          Continue
        </button>
      </Foot>
    </Phone>
  );
}
