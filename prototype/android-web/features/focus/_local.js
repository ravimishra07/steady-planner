// _local.js — focus-only stand-ins for shared primitives (not a Kotlin file).
//
// DeterminateCircularProgress: design/components.js CircularProgressIndicator draws a determinate arc only on
// creation/resize; it has no way to redraw when `progress` changes without rebuilding the node. The focus dial
// must update every second without re-rendering the screen (ARCHITECTURE §7), so this is the same M3 1.3.1
// determinate drawing (round cap, 4dp track gap, track starting after the indicator) with a setProgress().
import { h, cx, styleOf } from '../../platform/h.js';

/**
 * DeterminateCircularProgress({ progress, color, trackColor, strokeWidth (px number or 'var(--token)'), class?, css? })
 * → Element with `.setProgress(p)` and `.setColor(c)`.
 */
export function DeterminateCircularProgress(p) {
  const gapSize = 4; // ProgressIndicatorDefaults.CircularIndicatorTrackGapSize
  let progress = p.progress;
  let color = p.color;
  const track = p.trackColor;
  const el = h('div', { class: cx('m3-circular-progress', p.class), role: 'progressbar' });
  Object.assign(el.style, { position: 'relative', flex: 'none' });
  styleOf(el, p.css);
  const svg = h('svg');
  svg.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;overflow:visible;display:block';
  el.append(svg);
  const arc = (S, start, sweep, col) => {
    if (sweep <= 0) return '';
    const r = (S - sw) / 2, c = S / 2;
    if (sweep >= 359.999) return `<circle cx="${c}" cy="${c}" r="${r}" fill="none" style="stroke:${col}" stroke-width="${sw}"/>`;
    const a0 = (start * Math.PI) / 180, a1 = ((start + sweep) * Math.PI) / 180;
    const x0 = c + r * Math.cos(a0), y0 = c + r * Math.sin(a0), x1 = c + r * Math.cos(a1), y1 = c + r * Math.sin(a1);
    return `<path d="M${x0},${y0} A${r},${r} 0 ${sweep > 180 ? 1 : 0} 1 ${x1},${y1}" fill="none" style="stroke:${col}" stroke-width="${sw}" stroke-linecap="round"/>`;
  };
  const strokePx = () => {
    const m = /^var\((--[\w-]+)\)$/.exec(String(p.strokeWidth));
    return m ? parseFloat(getComputedStyle(el).getPropertyValue(m[1])) || 0 : parseFloat(p.strokeWidth) || 0;
  };
  let sw = 0;
  const draw = () => {
    const S = el.clientWidth; if (!S) return;
    sw = strokePx();
    const prog = Math.min(1, Math.max(0, progress));
    const sweep = prog * 360;
    const gapSweep = ((gapSize + sw) / (Math.PI * S)) * 360;
    const g = Math.min(sweep, gapSweep);
    svg.innerHTML = arc(S, 270 + sweep + g, 360 - sweep - g * 2, track) + arc(S, 270, sweep, color);
    el.setAttribute('aria-valuenow', String(prog));
  };
  new ResizeObserver(draw).observe(el);
  queueMicrotask(draw);
  el.setProgress = (v) => { if (v === progress) return; progress = v; draw(); };
  el.setColor = (c) => { color = c; draw(); };
  return el;
}
