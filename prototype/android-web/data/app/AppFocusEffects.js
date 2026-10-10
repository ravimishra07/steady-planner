// Port of app/focus/AppFocusEffects.kt. Maps a Focus state to its alarm and notifications.
// Idempotent: safe to apply the same state twice. (Alarm/notifier are the web stubs.)
import { FocusStatus } from '../../domain/FocusSession.js';

export class AppFocusEffects {
  constructor(notifier, alarm, settings) { this.notifier = notifier; this.alarm = alarm; this.settings = settings; }

  async onSessionChanged(session) {
    switch (session.status) {
      case FocusStatus.RUNNING: {
        const endsAt = session.endsAtMs;
        if (endsAt == null) return;
        this.alarm.arm(endsAt);
        this.notifier.showRunning(session);
        break;
      }
      case FocusStatus.PAUSED:
        this.alarm.cancel();
        this.notifier.showPaused(session);
        break;
      default: // IDLE, DONE
        this.alarm.cancel();
        this.notifier.cancelOngoing();
    }
  }

  async onSessionCompleted(session) {
    this.notifier.showComplete(session, await this.settings.focusEndAlertOnce());
  }
}
