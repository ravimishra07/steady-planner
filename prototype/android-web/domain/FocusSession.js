// Port of domain/FocusSession.kt
import { dataClass, enumClass, lazyDefault, coerceAtLeast, coerceAtMost, coerceIn, toIntOrNull, padStart2, idiv } from './kt.js';
import { nowMs } from './time.js';

export const FocusStatus = enumClass(['IDLE', 'RUNNING', 'PAUSED', 'DONE']);

/** tag: BlockTag? */
export const FocusBlockRef = dataClass({ id: undefined, title: undefined, subtitle: undefined, tag: undefined, sessionId: '', nodeKey: '', isRevision: false });

export const DEFAULT_FOCUS_DURATION_SEC = 50 * 60;

export const FocusSession = dataClass(
  {
    status: lazyDefault(() => FocusStatus.IDLE),
    durationSec: DEFAULT_FOCUS_DURATION_SEC,
    remainingSec: DEFAULT_FOCUS_DURATION_SEC,
    endsAtMs: null,
    block: null,
    completedToday: 0,
  },
  {
    remainingSecondsAt(now) {
      if (this.status !== FocusStatus.RUNNING || this.endsAtMs == null) return coerceAtLeast(this.remainingSec, 0);
      const millisLeft = this.endsAtMs - now;
      if (millisLeft <= 0) return 0;
      return coerceAtMost(idiv(millisLeft - 1, 1000) + 1, this.durationSec);
    },
    focusedSecondsAt(now) {
      return coerceIn(this.durationSec - this.remainingSecondsAt(now), 0, this.durationSec);
    },
    isExpiredAt(now) {
      return this.status === FocusStatus.RUNNING && this.endsAtMs != null && this.endsAtMs <= now;
    },
    withClockAt(now) {
      if (this.status !== FocusStatus.RUNNING || this.endsAtMs == null) return this;
      const left = this.remainingSecondsAt(now);
      return this.isExpiredAt(now)
        ? this.copy({ status: FocusStatus.DONE, remainingSec: 0, endsAtMs: null })
        : this.copy({ remainingSec: left });
    },
    withClockNow() { return this.withClockAt(nowMs()); },
  },
);

/** Focus length presets (§6.4). */
export const FOCUS_LENGTH_PRESETS_MINUTES = Object.freeze([25, 50, 90]);
export const FOCUS_LENGTH_MIN_MINUTES = 5;
export const FOCUS_LENGTH_MAX_MINUTES = 180;

/** A typed custom length, clamped; null when not a number. */
export function focusLengthMinutesOrNull(input) {
  const v = toIntOrNull(input.trim());
  return v == null ? null : coerceIn(v, FOCUS_LENGTH_MIN_MINUTES, FOCUS_LENGTH_MAX_MINUTES);
}

export function formatFocusClock(seconds) {
  const safe = coerceAtLeast(seconds, 0);
  return `${padStart2(idiv(safe, 60))}:${padStart2(safe % 60)}`;
}
