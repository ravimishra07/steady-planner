'use client';

import { useRouter } from 'next/navigation';
import { Bar } from '@/components/Bar';
import { Body, Foot, Phone } from '@/components/Phone';
import { cushion } from '@/lib/data';
import { usePlan } from '@/lib/state';

export default function CushionScreen() {
  const router = useRouter();
  const { state } = usePlan();
  const c = cushion(state);

  return (
    <Phone>
      <Bar back="/hours" step={4} />
      <Body>
        <p className="eyebrow">Your plan</p>

        {c.short ? (
          <div className="verdict short">
            <h2>{c.gap} hours short</h2>
            <p>
              At {state.wd} hrs on weekdays you cover about {c.coverage}% of the syllabus
              before the exam. Three ways to fix it below.
            </p>
          </div>
        ) : (
          <div className="verdict ok">
            <h2>{Math.abs(c.gap)} hours to spare</h2>
            <p>
              You finish the syllabus with room left for a full extra revision pass.
              Keep this pace and do not add hours.
            </p>
          </div>
        )}

        <div className="gauge">
          <div className="cap">
            <span>Hours you have</span>
            <span>{c.have} / {c.need} hrs</span>
          </div>
          <div className="track">
            {c.short ? (
              <>
                <div className="have" style={{ width: `${c.coverage}%` }} />
                <div className="gap" style={{ width: `${100 - c.coverage}%` }} />
              </>
            ) : (
              <>
                <div className="have" style={{ width: `${Math.round((c.need / c.have) * 100)}%` }} />
                <div className="spare" style={{ width: `${100 - Math.round((c.need / c.have) * 100)}%` }} />
              </>
            )}
          </div>
          <div className="legend">
            {c.short ? (
              <>
                <span><i style={{ background: 'var(--ink)' }} />Available</span>
                <span><i style={{ background: 'var(--red)' }} />Shortfall</span>
              </>
            ) : (
              <>
                <span><i style={{ background: 'var(--ink)' }} />Syllabus</span>
                <span><i style={{ background: 'var(--green-soft)', border: '1px solid var(--green-line)' }} />Buffer</span>
              </>
            )}
          </div>
        </div>

        <p className="eyebrow">{c.short ? 'Close the gap' : 'What the buffer buys you'}</p>
        <div className="fix">
          {c.short ? (
            <>
              <div className="row">
                <b>+{c.extraPerDay}h</b>
                <span>Add {c.extraPerDay} hrs to every single day between now and the exam</span>
              </div>
              <div className="row">
                <b>&minus;{c.topicsToDrop}</b>
                <span>
                  Drop the {c.topicsToDrop} lowest-yield topics &mdash; worth about{' '}
                  {Math.round(c.topicsToDrop * 0.7)} marks last year
                </span>
              </div>
              <div className="row">
                <b>{c.daysToPush}d</b>
                <span>Or move your target date back by {c.daysToPush} days</span>
              </div>
            </>
          ) : (
            <>
              <div className="row"><b>2&times;</b><span>Full revision passes fit inside your timeline</span></div>
              <div className="row">
                <b>{c.bufferDays}d</b>
                <span>Buffer days for when life interrupts &mdash; built in, not borrowed</span>
              </div>
              <div className="row"><b>18</b><span>Full-length mocks scheduled in the last 6 weeks</span></div>
            </>
          )}
        </div>
      </Body>
      <Foot>
        <button className="cta" onClick={() => router.push('/paywall')}>Start day 1</button>
      </Foot>
    </Phone>
  );
}
