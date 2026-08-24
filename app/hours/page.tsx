'use client';

import { useRouter } from 'next/navigation';
import { Bar } from '@/components/Bar';
import { Body, Foot, Phone } from '@/components/Phone';
import { usePlan } from '@/lib/state';

export default function HoursScreen() {
  const router = useRouter();
  const { state, patch } = usePlan();

  return (
    <Phone>
      <Bar back="/shape" step={4} />
      <Body>
        <p className="eyebrow">Step 4 of 4</p>
        <h1>How many hours can you really give?</h1>
        <p className="sub">Be honest. The next screen tells you if it&rsquo;s enough.</p>

        <div className="slide">
          <div className="top"><b>Weekdays</b><i>{state.wd} hrs</i></div>
          <input
            type="range" min={1} max={14} step={0.5} value={state.wd}
            onChange={e => patch({ wd: +e.target.value })}
          />
        </div>
        <div className="slide">
          <div className="top"><b>Weekends</b><i>{state.we} hrs</i></div>
          <input
            type="range" min={1} max={16} step={0.5} value={state.we}
            onChange={e => patch({ we: +e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="place">Where will you study?</label>
          <input
            id="place" className="text" placeholder="Library, terrace, corner desk"
            value={state.place}
            onChange={e => patch({ place: e.target.value })}
          />
        </div>
        <p className="note">Naming the place makes you far more likely to actually show up.</p>
      </Body>
      <Foot>
        <button
          className="cta"
          onClick={() => { patch({ place: state.place.trim() }); router.push('/cushion'); }}
        >
          Build my plan
        </button>
      </Foot>
    </Phone>
  );
}
