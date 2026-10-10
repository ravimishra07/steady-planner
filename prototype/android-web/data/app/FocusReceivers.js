// Port of app/focus/FocusReceivers.kt.
// Stubbed: BroadcastReceivers become plain classes; `context` is the AppContainer and `intent` a
// plain { action } object. goAsync() is unnecessary (one JS event loop).

/** Pause, Resume and Stop from the ongoing notification. */
export class FocusActionReceiver {
  onReceive(container, intent) {
    const action = intent?.action;
    if (!action) return Promise.resolve();
    const controller = container.focusController;
    switch (action) {
      case FocusActionReceiver.ACTION_PAUSE: return controller.pause();
      case FocusActionReceiver.ACTION_RESUME: return controller.resume();
      // Stop from the shade keeps the time studied; discarding needs the in-app confirmation.
      case FocusActionReceiver.ACTION_STOP: return controller.stopSavingPartial();
      default: return Promise.resolve();
    }
  }
}
FocusActionReceiver.ACTION_PAUSE = 'com.exam.assistant.focus.PAUSE';
FocusActionReceiver.ACTION_RESUME = 'com.exam.assistant.focus.RESUME';
FocusActionReceiver.ACTION_STOP = 'com.exam.assistant.focus.STOP';

/** The session-end alarm. Completes an expired session, or re-arms if the stored end moved. */
export class FocusAlarmReceiver {
  onReceive(container, intent) {
    if (intent?.action !== FocusAlarmReceiver.ACTION_END) return Promise.resolve();
    return container.focusController.reconcile().catch((e) => console.error(e));
  }
}
FocusAlarmReceiver.ACTION_END = 'com.exam.assistant.focus.END';

/** Boot and app update clear alarms. On the web, never delivered (page load does the same work). */
export class BootReceiver {
  onReceive(container, intent) {
    switch (intent?.action) {
      case 'android.intent.action.BOOT_COMPLETED':
      case 'android.intent.action.MY_PACKAGE_REPLACED':
        return container.onBootOrUpdate();
      default: return Promise.resolve();
    }
  }
}
