'use client';

import { useState } from 'react';
import { HomeHeader } from '@/components/HomeHeader';
import { Phone } from '@/components/Phone';
import { cushion, examFor, syllabusFor } from '@/lib/data';
import { usePlan } from '@/lib/state';

export default function SyllabusScreen() {
  const { state, patch } = usePlan();
  const c = cushion(state);
  const E = examFor(state.exam);
  const SY = syllabusFor(state.exam);
  const [open, setOpen] = useState<Record<number, boolean>>({});

  const tick = (i: number, j: number) =>
    patch({ done: { ...state.done, [`${i}_${j}`]: !state.done[`${i}_${j}`] } });

  return (
    <Phone>
      <HomeHeader exam={E} days={state.days} c={c} title="Syllabus" tab="syllabus" />
      <div className="pane on">
        <p className="daylabel">Tap any topic to mark it done. Add or remove anything.</p>

        {SY.map((s, i) => {
          const hrs = s.t.reduce((a, b) => a + b[1], 0);
          const dn = s.t.filter((_, j) => state.done[`${i}_${j}`]).length;
          return (
            <div className={open[i] ? 'sec open' : 'sec'} key={s.n}>
              <button onClick={() => setOpen(p => ({ ...p, [i]: !p[i] }))}>
                <div className="nm">
                  <b>{s.n}</b>
                  <span>{s.t.length} topics &middot; {hrs} hrs &middot; {s.q} questions</span>
                </div>
                <div className="mini">
                  <i style={{ width: `${Math.round((dn / s.t.length) * 100)}%` }} />
                </div>
                <span className="caret">&rsaquo;</span>
              </button>
              <div className="topics">
                {s.t.map(([name, h], j) => {
                  const isDone = !!state.done[`${i}_${j}`];
                  return (
                    <div className="tp" key={name}>
                      <div
                        className={isDone ? 'box sm chk' : 'box sm'}
                        role="checkbox"
                        aria-checked={isDone}
                        tabIndex={0}
                        onClick={() => tick(i, j)}
                      />
                      <b className={isDone ? 'done' : undefined}>{name}</b>
                      <em>{h}h</em>
                    </div>
                  );
                })}
                <button className="addrow">+ Add a topic</button>
              </div>
            </div>
          );
        })}

        <p className="note">
          Seeded from the official notification.<br />Everything here is yours to edit.
        </p>
      </div>
    </Phone>
  );
}
