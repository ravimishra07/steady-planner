'use client';

import { useRouter } from 'next/navigation';

/**
 * Top bar. `back` is the route to return to; omit it for the first step.
 * `step`/`of` drive the progress dots — pass step=0 to render an empty rail.
 */
export function Bar({ back, step, of = 4 }: { back?: string; step: number; of?: number }) {
  const router = useRouter();
  return (
    <div className="bar">
      {back && (
        <button className="back" aria-label="Go back" onClick={() => router.push(back)}>
          &#8592;
        </button>
      )}
      <div className="dots">
        {Array.from({ length: of }, (_, i) => (
          <i key={i} className={i < step ? 'dot done' : 'dot'} />
        ))}
      </div>
    </div>
  );
}
