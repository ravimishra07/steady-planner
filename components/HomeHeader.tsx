'use client';

import { useRouter } from 'next/navigation';
import { Cushion, Exam } from '@/lib/data';

export function HomeHeader({
  exam, days, c, title, tab,
}: {
  exam: Exam; days: number; c: Cushion; title: string; tab: 'plan' | 'syllabus';
}) {
  const router = useRouter();
  return (
    <>
      <div className="hbar">
        <div className="r1">
          <div>
            <p className="ex">{exam.name}</p>
            <h2>{title}</h2>
          </div>
          <div className="days">
            <b>{days}</b>
            <span>days left</span>
          </div>
        </div>
        <div className="chip">
          <i style={{ background: c.short ? 'var(--red)' : 'var(--dot-green)' }} />
          <span>
            {c.short
              ? `${c.gap} hrs behind the syllabus`
              : `${Math.abs(c.gap)} hrs of buffer in hand`}
          </span>
        </div>
      </div>
      <div className="tabs">
        <button
          className={tab === 'plan' ? 'tab on' : 'tab'}
          onClick={() => tab !== 'plan' && router.push('/home')}
        >
          Plan
        </button>
        <button
          className={tab === 'syllabus' ? 'tab on' : 'tab'}
          onClick={() => tab !== 'syllabus' && router.push('/syllabus')}
        >
          Syllabus
        </button>
      </div>
    </>
  );
}
