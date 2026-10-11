// Port of feature/home HomeCalendarChrome.kt.
import { h } from '../../platform/h.js';
import { C, Text, Icon, Row, Column, Spacer, clickable } from '../../design/components.js';
import { WeekDayStatus } from '../../domain/TodaySchedule.js';
import { chunked } from '../../domain/kt.js';
import { R, stringResource, formatDatePattern } from './_local.js';

/** HomeCalendarChrome({ selectedDate, weekDays, monthDays, expanded, onToggleExpanded, onSelectDate, css? }) */
export function HomeCalendarChrome(p) {
  const { selectedDate, weekDays, monthDays, expanded, onToggleExpanded, onSelectDate } = p;
  const monthTitle = formatDatePattern(selectedDate, R.string.home_month_pattern);
  const monthStateDescription = stringResource(
    expanded ? R.string.home_calendar_state_expanded : R.string.home_calendar_state_collapsed, monthTitle);
  const toggleLabel = expanded ? R.string.home_calendar_collapse : R.string.home_calendar_expand;
  const header = Row({
    verticalAlignment: 'CenterVertically',
    css: { minHeight: 'var(--size-touchTarget)', padding: '0 var(--space-md)', alignSelf: 'flex-start' },
  },
  Text({ text: monthTitle, style: 'titleMedium', fontWeight: 'Medium', color: C.text, maxLines: 1, overflow: 'Ellipsis' }),
  Icon({
    imageVector: 'Filled.KeyboardArrowDown', contentDescription: toggleLabel, tint: C.textSecondary,
    css: { transform: expanded ? 'rotate(180deg)' : 'none' },
  }));
  clickable(header, { onClick: onToggleExpanded });
  header.setAttribute('aria-label', `${monthTitle}, ${toggleLabel}`);
  header.setAttribute('aria-description', monthStateDescription);

  return Column({ class: 'fill-w', css: p.css },
    header,
    expanded ? HomeMonthGrid({ days: monthDays, onSelectDate }) : HomeWeekStrip({ days: weekDays, onSelectDate }),
  );
}

function WeekdayHeaderRow() {
  return Row({ class: 'fill-w', css: { padding: '0 var(--space-sm)' }, horizontalArrangement: { spacedBy: 'var(--cal-gridGap)' } },
    ...[
      R.string.home_weekday_monday, R.string.home_weekday_tuesday, R.string.home_weekday_wednesday,
      R.string.home_weekday_thursday, R.string.home_weekday_friday, R.string.home_weekday_saturday, R.string.home_weekday_sunday,
    ].map((label) => Text({ text: label, style: 'labelMedium', fontWeight: 'Medium', color: C.textSecondary, textAlign: 'Center', class: 'weight' })));
}

const Hairline = () => h('div', { style: { width: '100%', height: 'var(--stroke-hairline)', background: C.hairlineSoft, flex: 'none' } });

function HomeWeekStrip({ days, onSelectDate }) {
  return Column({ class: 'fill-w' },
    WeekdayHeaderRow(),
    Spacer({ height: 'var(--space-xs)' }),
    Row({ class: 'fill-w', css: { padding: '0 var(--space-sm) var(--space-md)' }, horizontalArrangement: { spacedBy: 'var(--cal-gridGap)' } },
      ...days.map((day) => DayCell({ day, onSelectDate }))),
    Hairline(),
  );
}

function HomeMonthGrid({ days, onSelectDate }) {
  return Column({ class: 'fill-w' },
    WeekdayHeaderRow(),
    Spacer({ height: 'var(--space-xs)' }),
    ...chunked(days, 7).map((week) => Row({ class: 'fill-w', css: { padding: '0 var(--space-sm)' }, horizontalArrangement: { spacedBy: 'var(--cal-gridGap)' } },
      ...week.map((day) => (day != null ? DayCell({ day, onSelectDate }) : h('div', { class: 'weight' }))))),
    Spacer({ height: 'var(--space-sm)' }),
    Hairline(),
  );
}

function statusRes(status) {
  switch (status) {
    case WeekDayStatus.DONE: return R.string.home_calendar_status_done;
    case WeekDayStatus.PARTIAL: return R.string.home_calendar_status_partial;
    case WeekDayStatus.TODAY: return R.string.home_calendar_status_today;
    case WeekDayStatus.PLANNED: return R.string.home_calendar_status_planned;
    default: return R.string.home_calendar_status_no_progress;
  }
}

function DayCell({ day, onSelectDate }) {
  const fullDate = formatDatePattern(day.date, R.string.home_accessibility_date_pattern);
  const cell = Column({
    class: 'weight',
    horizontalAlignment: 'CenterHorizontally',
    verticalArrangement: { spacedBy: 'var(--cal-dayGap)' },
    css: {
      minHeight: 'var(--size-touchTarget)', borderRadius: 'var(--cal-dayCorner)', overflow: 'hidden',
      background: day.selected ? C.selectionContainer : 'transparent',
      paddingTop: 'var(--cal-dayTopPadding)', paddingBottom: 'var(--cal-dayBottomPadding)',
    },
  },
  Text({ text: String(day.dayOfMonth), style: 'titleMedium', fontWeight: 'Medium', color: day.selected ? C.onSelectionContainer : C.text }),
  HomeWeekMeter({ status: day.status, selected: day.selected }));
  clickable(cell, { onClick: () => onSelectDate(day.date), selected: day.selected });
  cell.setAttribute('aria-label', fullDate);
  cell.setAttribute('aria-description', statusRes(day.status));
  cell.title = stringResource(R.string.home_calendar_select_date, fullDate);
  cell.setAttribute('data-date', day.date);
  return cell;
}

function HomeWeekMeter({ status, selected }) {
  const fillFraction = status === WeekDayStatus.DONE ? 1
    : status === WeekDayStatus.PARTIAL ? 0.5
      : status === WeekDayStatus.TODAY || status === WeekDayStatus.PLANNED ? 0.2 : 0;
  const meter = h('div', { style: {
    width: 'var(--cal-meterWidth)', height: 'var(--cal-meterHeight)', borderRadius: 'var(--radius-full)',
    overflow: 'hidden', background: C.surface3, flex: 'none',
  } });
  if (fillFraction > 0) {
    meter.append(h('div', { style: {
      width: `${fillFraction * 100}%`, height: 'var(--cal-meterHeight)', background: selected ? C.onSelectionContainer : C.primary,
    } }));
  }
  return meter;
}

/** HomeDayBar({ selectedIsToday, selectedDate }) */
export function HomeDayBar({ selectedIsToday, selectedDate }) {
  const selectedDayLabel = formatDatePattern(selectedDate, R.string.home_selected_day_pattern);
  return Text({
    text: selectedIsToday ? R.string.home_today : selectedDayLabel,
    style: 'titleSmall', fontWeight: 'SemiBold', color: C.text, class: 'fill-w',
    css: { padding: 'var(--space-md) var(--space-screen) var(--space-xxs)' },
  });
}
