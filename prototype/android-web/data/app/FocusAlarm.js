// Port of app/focus/FocusAlarm.kt.
// Stubbed: AlarmManager -> setTimeout. When it fires, FocusAlarmReceiver runs reconcile(), which
// completes the session from ends_at (not from when the timer fired), exactly like Android. A page
// reload drops the timer; AppContainer's startup reconcile() re-arms it (or completes an expired run).
import { nowMs } from '../../domain/time.js';
import { FocusAlarmReceiver } from './FocusReceivers.js';

const MAX_TIMEOUT_MS = 2_147_483_647;

export class FocusAlarm {
  /** [context] is the AppContainer (the "application context" receivers reach the graph through). */
  constructor(context) { this.context = context; this._timer = null; }

  arm(endsAtMs) {
    this.cancel();
    const delay = Math.min(Math.max(0, endsAtMs - nowMs()), MAX_TIMEOUT_MS);
    this._timer = setTimeout(() => {
      this._timer = null;
      new FocusAlarmReceiver().onReceive(this.context, { action: FocusAlarmReceiver.ACTION_END });
    }, delay);
  }

  cancel() {
    if (this._timer != null) clearTimeout(this._timer);
    this._timer = null;
  }
}
