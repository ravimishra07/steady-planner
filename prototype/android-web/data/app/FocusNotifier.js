// Port of app/focus/FocusNotifier.kt.
// Stubbed: Android notifications. The ongoing/complete notification is published on `state`
// (a StateFlow the shell may render as an in-page banner/toast):
//   null | { kind: 'running' | 'paused' | 'complete', session, withSound? }
// Notification actions (Pause/Resume/Stop) map to FocusActionReceiver in FocusReceivers.js.
import { MutableStateFlow } from '../../platform/flow.js';

export class FocusNotifier {
  constructor(_context) {
    this._state = new MutableStateFlow(null);
    this.state = this._state.asStateFlow();
    this._complete = new MutableStateFlow(null);
    /** The last "Session complete" alert (ID_COMPLETE), separate from the ongoing one. */
    this.complete = this._complete.asStateFlow();
  }
  showRunning(session) { this._state.value = { kind: 'running', session }; }
  showPaused(session) { this._state.value = { kind: 'paused', session }; }
  cancelOngoing() { this._state.value = null; }
  showComplete(session, withSound) { this._complete.value = { kind: 'complete', session, withSound }; }
  /** NotificationManagerCompat.cancelAll() */
  cancelAll() { this._state.value = null; this._complete.value = null; }
}
FocusNotifier.CHANNEL_RUNNING = 'focus_running';
FocusNotifier.CHANNEL_END = 'focus_end';
FocusNotifier.CHANNEL_END_SILENT = 'focus_end_silent';
