// Port of app/reminder/DailyReminder.kt.
// Stubbed: AlarmManager -> setTimeout to the next trigger (only while the page is open);
// the notification -> `notification` StateFlow { title, text } | null that the shell may show as an
// in-page toast. Tapping it should set container.openTodayRequest (MainActivity.OPEN_TODAY).
import { MutableStateFlow } from '../../platform/flow.js';
import { nowMs, atStartOfDayMs, epochMsToLocalDate, plusDays, nowMinuteOfDay } from '../../domain/time.js';
import { formatAndroid } from '../common.js';
import { loadTodaySnapshot } from './TodaySnapshot.js';
import { AppWidgets } from './AppWidgets.js';

const MAX_TIMEOUT_MS = 2_147_483_647;
// app/src/main/res/values/strings.xml
const reminder_title = "Today's plan";
const reminder_body_no_plan = 'Nothing planned yet. Open Today to see your day.';
const reminder_body = (n, first) => formatAndroid(n === 1 ? '%1$d block · first: %2$s' : '%1$d blocks · first: %2$s', n, first);
const focus_notif_default_title = 'Focus session';

export class DailyReminder {
  /** [context] is the AppContainer. */
  constructor(context) {
    this.context = context;
    this._timer = null;
    this._notification = new MutableStateFlow(null);
    this.notification = this._notification.asStateFlow();
  }

  apply(setting) { if (setting.enabled) this.arm(setting.minuteOfDay); else this.cancel(); }

  /** private */
  arm(minuteOfDay) {
    if (this._timer != null) clearTimeout(this._timer);
    const delay = Math.min(Math.max(0, DailyReminder.nextTriggerAt(minuteOfDay) - nowMs()), MAX_TIMEOUT_MS);
    this._timer = setTimeout(() => {
      this._timer = null;
      new DailyReminderReceiver().onReceive(this.context, { action: DailyReminderReceiver.ACTION_FIRE });
    }, delay);
  }

  cancel() {
    if (this._timer != null) clearTimeout(this._timer);
    this._timer = null;
    this._notification.value = null;
  }

  /** Posts the reminder. */
  show(snapshot, nowMinute) {
    const upcomingAll = snapshot.blocks.filter((it) => it.endMinuteOfDay > nowMinute);
    const upcoming = upcomingAll.length ? upcomingAll : snapshot.blocks;
    let text;
    if (upcoming.length === 0) text = reminder_body_no_plan;
    else {
      const first = upcoming[0].title.trim() === '' ? focus_notif_default_title : upcoming[0].title;
      text = reminder_body(upcoming.length, first);
    }
    this._notification.value = { title: reminder_title, text };
  }

  /** The next time [minuteOfDay] comes round, strictly after now. Epoch ms. */
  static nextTriggerAt(minuteOfDay, now = nowMs()) {
    const day = epochMsToLocalDate(now);
    const todayAt = atStartOfDayMs(day) + minuteOfDay * 60_000;
    return todayAt > now ? todayAt : atStartOfDayMs(plusDays(day, 1)) + minuteOfDay * 60_000;
  }
}
DailyReminder.CHANNEL_REMINDER = 'daily_plan';

/** Fires once a day: post the reminder, arm tomorrow, refresh the widgets. */
export class DailyReminderReceiver {
  async onReceive(container, intent) {
    if (intent?.action !== DailyReminderReceiver.ACTION_FIRE) return;
    try {
      const setting = await container.settings.dailyReminderOnce();
      if (setting.enabled) container.dailyReminder.show(await loadTodaySnapshot(container), nowMinuteOfDay());
      container.dailyReminder.apply(setting);
      await AppWidgets.updateAll(container);
    } catch (e) { console.error(e); }
  }
}
DailyReminderReceiver.ACTION_FIRE = 'com.exam.assistant.reminder.FIRE';
