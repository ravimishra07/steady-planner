'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Phone } from '@/components/Phone';

const TOTAL = 50 * 60;
const START = 32 * 60 + 47;

export default function FocusScreen() {
  const router = useRouter();
  const [left, setLeft] = useState(START);
  const [blocking, setBlocking] = useState(false);

  useEffect(() => {
    const id = setInterval(() => setLeft(s => (s > 0 ? s - 1 : 0)), 1000);
    return () => clearInterval(id);
  }, []);

  const mm = String(Math.floor(left / 60)).padStart(2, '0');
  const ss = String(left % 60).padStart(2, '0');

  return (
    <Phone>
      <div className="dark">
        <p className="eyebrow" style={{ color: 'var(--ink-3)' }}>Focus session &middot; 50 min</p>
        <div className="clock">{mm}:{ss}</div>
        <p className="att">
          <b>Geometry &mdash; Triangles: similarity</b>
          Logged against this task, not as loose minutes
        </p>
        <div className="ring">
          <i style={{ width: `${Math.round(((TOTAL - left) / TOTAL) * 100)}%` }} />
        </div>

        {blocking ? (
          <div className="perm">
            <b>4 apps blocked</b>
            <p>They come back the moment this session ends.</p>
            <div className="blocked">
              {['YouTube', 'Instagram', 'WhatsApp', 'Telegram'].map(a => <span key={a}>{a}</span>)}
            </div>
          </div>
        ) : (
          <div className="perm">
            <b>Block distracting apps?</b>
            <p>We will hide the apps you pick until this session ends. Android will ask for two permissions.</p>
            <button onClick={() => setBlocking(true)}>Choose apps to block</button>
          </div>
        )}

        <button className="stop" onClick={() => router.push('/home')}>End session</button>
      </div>
    </Phone>
  );
}
