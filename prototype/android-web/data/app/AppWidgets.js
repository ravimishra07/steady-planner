// Port of app/widget/AppWidgets.kt (Widgets.kt is Glance UI and is not ported).
// Stubbed: there is no home screen, so no widget is ever placed: hasAny() is false and updateAll()
// cancels the tick, exactly the Android path with no widget. Intent builders return { action, ... }.
export const AppWidgets = {
  async hasAny(_context) { return false; },

  /** Redraws every placed widget and arms the next refresh tick. */
  async updateAll(context) {
    if (!(await this.hasAny(context))) { this.cancelTick(context); return; }
  },

  armTick(_context, _snapshot, _now) {},
  cancelTick(_context) {},

  startBlockIntent(_context, blockId) { return { action: 'com.exam.assistant.widget.START_BLOCK', blockId }; },
  openTodayIntent(_context) { return { action: 'com.exam.assistant.widget.OPEN_TODAY' }; },
  openPaywallIntent(_context) { return { action: 'com.exam.assistant.widget.OPEN_PAYWALL' }; },
};
