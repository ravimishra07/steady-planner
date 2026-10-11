// Port of feature/home HomeTimeline.kt.
// Canvas drawing (summary track, dashed rail) is done with positioned divs / a repeating gradient.
// Not reproduced: animateContentSize() on the study card (expand/collapse snaps).
import { h } from '../../platform/h.js';
import {
  C, Surface, Text, Icon, Row, Column, Box, Spacer, Button, TextButton, ButtonDefaults, clickable, CircleShape, Transparent,
} from '../../design/components.js';
import { DAY_TIMELINE_START, DAY_TIMELINE_END, isActionableStudyGap } from '../../domain/DayTimeline.js';
import { formatGap } from '../../domain/TodaySchedule.js';
import { coerceAtLeast } from '../../domain/kt.js';
import {
  R, stringResource, pluralStringResource, javaShortTime, rememberMutable, rememberBox, bringIntoView,
} from './_local.js';

const railWidth = 'var(--tl-railWidth)';
const lineWidth = 'var(--space-xxs)';
const nodeSize = 'var(--size-legendDot)';

export function formatClock(minuteOfDay) {
  const clamped = ((minuteOfDay % 1440) + 1440) % 1440;
  return javaShortTime(clamped);
}

export function formatDuration(minutes) {
  if (minutes <= 0) return stringResource(R.string.home_duration_min, 0);
  if (minutes < 60) return stringResource(R.string.home_duration_min, minutes);
  const hh = Math.trunc(minutes / 60);
  const m = minutes % 60;
  return m === 0 ? stringResource(R.string.home_duration_hours, hh) : stringResource(R.string.home_duration_hours_minutes, hh, m);
}

function formatRemaining(seconds) {
  const mins = Math.trunc(seconds / 60);
  const secs = seconds % 60;
  return mins > 0 ? `${mins}:${String(secs).padStart(2, '0')}` : `0:${String(secs).padStart(2, '0')}`;
}

/** One shared dp-per-minute rate for the whole day axis — free time and study blocks read on the same scale. */
const AXIS_PX_PER_MINUTE = 1.0;
const axisHeight = (minutes) => `${minutes * AXIS_PX_PER_MINUTE}px`;
/** Duration has spatial meaning on the timeline: a 2h block reads taller than a 30min one. */
const studyBlockMinHeight = (minutes) => `max(${axisHeight(minutes)}, var(--size-topAppBarHeight))`;

const pct = (n) => `${n * 100}%`;

/** TodaySummary({ entries, plannedMinutes, completedMinutes, selectedIsToday, onEditPlan, css? }) */
export function TodaySummary(p) {
  const { entries, plannedMinutes, completedMinutes, selectedIsToday, onEditPlan } = p;
  const expandedState = rememberMutable('TodaySummary.expanded', true);
  const expanded = expandedState.value;
  const studyBlocks = entries.filter((it) => it.type === 'Study').map((it) => it.block);
  const fixedBlocks = entries.filter((it) => it.type === 'Fixed').map((it) => it.block);
  const gaps = entries.filter((it) => it.type === 'Gap');
  const nowMinute = entries.find((it) => it.type === 'NowMarker')?.minuteOfDay ?? null;
  const allStarts = [...studyBlocks.map((it) => it.startMinuteOfDay), ...fixedBlocks.map((it) => it.startMinuteOfDay), ...gaps.map((it) => it.startMinuteOfDay)];
  const allEnds = [...studyBlocks.map((it) => it.endMinuteOfDay), ...fixedBlocks.map((it) => it.endMinuteOfDay), ...gaps.map((it) => it.endMinuteOfDay)];
  const dayStart = allStarts.length ? Math.min(...allStarts) : DAY_TIMELINE_START;
  const dayEnd = allEnds.length ? Math.max(...allEnds) : DAY_TIMELINE_END;
  const daySpan = coerceAtLeast(dayEnd - dayStart, 60);
  const left = coerceAtLeast(plannedMinutes - completedMinutes, 0);
  const headline = plannedMinutes === 0
    ? R.string.home_summary_nothing
    : left === 0 ? R.string.home_summary_complete : stringResource(R.string.home_summary_left, formatDuration(left));
  const fixedMinutes = fixedBlocks.reduce((s, it) => s + (it.endMinuteOfDay - it.startMinuteOfDay), 0);
  const showNow = selectedIsToday && nowMinute != null && nowMinute >= dayStart && nowMinute <= dayEnd;

  const header = Row({ verticalAlignment: 'CenterVertically', class: 'fill-w' },
    clickable(Row({ class: 'weight', verticalAlignment: 'CenterVertically' },
      Text({ text: headline, style: 'titleLarge', color: C.text }),
      Icon({ imageVector: expanded ? 'Filled.KeyboardArrowUp' : 'Filled.KeyboardArrowDown', tint: C.textSecondary }),
    ), { onClick: () => { expandedState.value = !expanded; } }),
    TextButton({ onClick: onEditPlan },
      Icon({ imageVector: 'Filled.EditCalendar', size: 'var(--size-smallIcon)' }),
      Text({ text: R.string.home_edit_plan, css: { paddingLeft: 'var(--space-xs)' } }),
    ),
  );

  const body = [];
  if (expanded) {
    // Canvas(fillMaxWidth().height(summaryTrack))
    const track = h('div', { style: { position: 'relative', width: '100%', height: 'var(--size-summaryTrack)', flex: 'none' } });
    const rect = (startMin, endMin, style) => {
      const leftF = (startMin - dayStart) / daySpan;
      const widthF = (endMin - startMin) / daySpan;
      return h('div', { style: {
        position: 'absolute', top: '0', bottom: '0', left: pct(leftF),
        width: `min(max(${pct(widthF)}, var(--space-xxs)), ${pct(1 - leftF)})`,
        borderRadius: 'calc(var(--size-summaryTrack) / 2)', ...style,
      } });
    };
    track.append(h('div', { style: { position: 'absolute', inset: '0', borderRadius: 'calc(var(--size-summaryTrack) / 2)', background: C.surface3 } }));
    fixedBlocks.forEach((b) => track.append(rect(b.startMinuteOfDay, b.endMinuteOfDay, { background: C.surface3 })));
    studyBlocks.forEach((b) => track.append(rect(b.startMinuteOfDay, b.endMinuteOfDay, b.completed
      ? { background: C.primary }
      // DrawStroke(width = Stroke.emphasis): stroke centred on the rect outline
      : { outline: `var(--stroke-emphasis) solid ${C.primary}`, outlineOffset: 'calc(var(--stroke-emphasis) / -2)' })));
    if (showNow) {
      const x = (nowMinute - dayStart) / daySpan;
      track.append(h('div', { style: {
        position: 'absolute', left: pct(x), top: 'calc(0px - var(--space-xxs))', bottom: 'calc(0px - var(--space-xxs))',
        width: 'var(--space-xxs)', transform: 'translateX(-50%)', background: C.onSurface,
      } }));
    }
    body.push(track);
    body.push(Row({ class: 'fill-w', horizontalArrangement: 'SpaceBetween' },
      Text({ text: formatClock(dayStart), style: 'labelSmall', color: C.textSecondary }),
      showNow ? Text({ text: R.string.home_summary_now, style: 'labelSmall', color: C.brand }) : null,
      Text({ text: formatClock(dayEnd), style: 'labelSmall', color: C.textSecondary }),
    ));
    // FlowRow(spacedBy lg, spacedBy sm)
    body.push(h('div', { style: { display: 'flex', flexWrap: 'wrap', columnGap: 'var(--space-lg)', rowGap: 'var(--space-sm)' } },
      SummaryFact('Filled.Check', stringResource(R.string.home_summary_logged, formatDuration(completedMinutes), formatDuration(plannedMinutes))),
      fixedMinutes > 0 ? SummaryFact('Filled.Lock', stringResource(R.string.home_summary_fixed, formatDuration(fixedMinutes))) : null,
    ));
  }

  return Surface({
    class: p.class, css: p.css,
    shape: 'var(--radius-summary)', color: C.surfaceCard,
    children: Column({
      class: 'fill-w',
      css: { padding: 'var(--space-xs) var(--space-lg) var(--space-lg) var(--space-lg)' },
      verticalArrangement: { spacedBy: 'var(--space-md)' },
    }, header, ...body),
  });
}

function SummaryFact(icon, label) {
  return Surface({
    shape: CircleShape, color: C.elevated,
    children: Row({
      css: { height: 'var(--size-compactControl)', padding: '0 var(--space-md)' },
      verticalAlignment: 'CenterVertically',
      horizontalArrangement: { spacedBy: 'var(--space-xs)' },
    },
    Icon({ imageVector: icon, size: 'var(--size-compactIcon)', tint: C.primary }),
    Text({ text: label, style: 'labelMedium', color: C.textSecondary })),
  });
}

/** DashedVerticalLine: TimelineMetrics.dash on, dash off, full line width. */
const dashed = (color) => `repeating-linear-gradient(to bottom, ${color} 0 var(--tl-dash), transparent var(--tl-dash) calc(var(--tl-dash) * 2))`;

function TimelineRow({
  time, lineColor, dashed: isDashed, showNode, nodeColor, drawLineBelow = true,
  minContentHeight = 'var(--space-none)', contentBottomPadding = 'var(--space-lg)', content,
}) {
  const rail = h('div', { style: { width: railWidth, flex: 'none' } },
    time != null ? Text({ text: time, style: 'labelSmall', fontWeight: 'Medium', color: C.textSecondary, css: { paddingTop: 'var(--cal-meterHeight)' } }) : null);
  const mid = h('div', { style: { width: 'var(--tl-compactRail)', flex: 'none', position: 'relative', alignSelf: 'stretch' } });
  if (drawLineBelow) {
    mid.append(h('div', { style: {
      position: 'absolute', top: 'var(--space-sm)', bottom: '0', left: '50%', width: lineWidth, transform: 'translateX(-50%)',
      background: isDashed ? dashed(lineColor) : lineColor,
    } }));
  }
  if (showNode) {
    mid.append(h('div', { style: {
      position: 'absolute', top: 'var(--space-xxs)', left: '50%', transform: 'translateX(-50%)',
      width: nodeSize, height: nodeSize, borderRadius: '50%', background: nodeColor,
    } }));
  }
  const body = h('div', { style: {
    flex: '1 1 0', minWidth: '0', display: 'flex', flexDirection: 'column', alignItems: 'stretch', boxSizing: 'border-box',
    minHeight: minContentHeight, paddingLeft: 'var(--space-sm)', paddingBottom: contentBottomPadding,
  } }, content ?? null);
  return h('div', { style: { display: 'flex', alignItems: 'stretch', width: '100%' } }, rail, mid, body);
}

function topicsLabel(block) {
  return block.subtopics.length === 1
    ? R.string.home_topic_count_one
    : pluralStringResource(R.plurals.home_topics_count, block.subtopics.length, block.subtopics.length);
}

function StudyBlockContent({ block, isRunning, isMissed, remainingLabel, expanded, canStartNow, onToggleExpand, onStart, onReschedule }) {
  if (block.completed && !expanded) return CompletedBlockSummary({ block, onClick: onToggleExpand });

  const containerColor = isRunning ? C.brandContainer : C.surfaceCard;
  const contentPadding = isRunning ? 'var(--space-lg)' : 'var(--space-md)';
  const hasSubtopics = block.subtopics.length > 0;

  const tagRow = Row({ verticalAlignment: 'CenterVertically' },
    isRunning ? [Icon({ imageVector: 'Filled.PlayArrow', tint: C.brandSoft, size: 'var(--space-ml)' }), Spacer({ width: 'var(--space-xs)' })] : null,
    block.isRevision ? [Icon({ imageVector: 'Filled.Repeat', tint: C.brandSoft, size: 'var(--tl-tinyIcon)' }), Spacer({ width: 'var(--space-xs)' })] : null,
    isMissed ? [Icon({ imageVector: 'Filled.WarningAmber', contentDescription: R.string.home_missed_semantics, tint: C.warning, size: 'var(--tl-tinyIcon)' }), Spacer({ width: 'var(--space-xs)' })] : null,
    Text({
      text: block.isRevision
        ? [
          R.string.home_revision_tag.toUpperCase(),
          block.subjectLabel.trim() === '' ? null : block.subjectLabel,
          block.revisionCount > 0 ? stringResource(R.string.home_revised_count, block.revisionCount) : null,
        ].filter((x) => x != null).join(' · ')
        : block.subjectLabel,
      style: 'labelMedium', color: isMissed ? C.warning : C.brandSoft, fontWeight: 'SemiBold',
    }),
  );

  const meta = Row({ verticalAlignment: 'CenterVertically', css: { paddingTop: 'var(--space-xs)' } },
    expanded
      ? Text({
        text: stringResource(R.string.home_time_range, formatClock(block.startMinuteOfDay), formatClock(block.endMinuteOfDay)),
        style: 'bodyMedium', fontWeight: 'Medium', color: C.textSecondary,
      })
      : [
        Icon({ imageVector: 'Filled.Schedule', tint: C.textSecondary, size: 'var(--tl-tinyIcon)' }),
        Spacer({ width: 'var(--space-xs)' }),
        Text({
          text: hasSubtopics
            ? stringResource(R.string.home_duration_topics, formatDuration(block.durationMinutes), topicsLabel(block))
            : formatDuration(block.durationMinutes),
          style: 'bodyMedium', fontWeight: 'Medium', color: C.textSecondary,
        }),
      ],
    Spacer({ width: 'var(--space-xs)' }),
    Icon({
      imageVector: expanded ? 'Filled.KeyboardArrowUp' : 'Filled.KeyboardArrowDown',
      contentDescription: expanded ? R.string.home_collapse_details : R.string.home_expand_details,
      tint: C.textMuted, size: 'var(--size-compactIcon)',
    }),
  );

  let trailing = null;
  if (block.completed) {
    trailing = Box({
      contentAlignment: 'Center',
      css: { width: 'var(--size-standardIcon)', height: 'var(--size-standardIcon)', borderRadius: '50%', background: C.successContainer, flex: 'none' },
    }, Icon({ imageVector: 'Filled.Check', contentDescription: R.string.home_completed_semantics, tint: C.successStrong, size: 'var(--space-ml)' }));
  } else if (canStartNow) {
    trailing = Button({
      onClick: onStart,
      contentPadding: 'var(--space-xs) var(--space-md)',
      colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }),
      shape: 'var(--radius-pill)',
    }, Text({
      text: isMissed ? R.string.home_start_now : isRunning ? R.string.home_continue_short : R.string.home_start_short,
      style: 'labelMedium',
    }));
  }

  const titleRow = Row({ verticalAlignment: 'Top', class: 'fill-w', css: { paddingTop: 'var(--space-xxs)' } },
    Column({ class: 'weight' },
      Text({ text: block.title, style: isRunning ? 'titleLarge' : 'titleMedium', fontWeight: 'SemiBold', color: C.text }),
      meta,
    ),
    Spacer({ width: 'var(--space-sm)' }),
    trailing,
  );

  const kids = [tagRow, titleRow];
  if (isRunning && remainingLabel != null) {
    kids.push(Text({ text: stringResource(R.string.home_studying_now, remainingLabel), style: 'labelMedium', color: C.brandSoft, css: { paddingTop: 'var(--space-xxs)' } }));
  }
  if (expanded) {
    kids.push(Column({ css: { paddingTop: 'var(--space-sm)' }, verticalArrangement: { spacedBy: 'var(--space-xs)' } },
      block.subtopics.length === 0
        ? Text({ text: stringResource(R.string.home_details_duration, formatDuration(block.durationMinutes)), style: 'bodyMedium', color: C.textSecondary })
        : null,
      block.subtopics.map((subtopic) => Row({ verticalAlignment: 'CenterVertically' },
        Text({ text: formatClock(subtopic.startMinuteOfDay), style: 'labelMedium', color: C.textMuted, css: { width: 'var(--tl-trailingAction)', flex: 'none' } }),
        Text({ text: subtopic.title, style: 'bodyMedium', color: C.textSecondary }),
      )),
      isMissed ? TextButton({ onClick: onReschedule }, Text({ text: R.string.home_choose_another_time })) : null,
    ));
  }

  const card = Surface({
    shape: 'var(--radius-lg)', color: containerColor, class: 'fill-w',
    children: Column({ class: 'fill-w', css: { padding: contentPadding }, verticalArrangement: { spacedBy: 'var(--space-xxs)' } }, ...kids),
  });
  clickable(card, { onClick: onToggleExpand });
  return card;
}

function CompletedBlockSummary({ block, onClick }) {
  const summary = block.subtopics.length > 0
    ? stringResource(R.string.home_completed_summary_topics, formatDuration(block.durationMinutes), topicsLabel(block))
    : stringResource(R.string.home_completed_summary, formatDuration(block.durationMinutes));
  const row = Row({ class: 'fill-w', css: { padding: 'var(--space-xs) 0' }, verticalAlignment: 'CenterVertically' },
    Icon({ imageVector: 'Filled.Check', tint: C.success, size: 'var(--size-compactIcon)' }),
    Spacer({ width: 'var(--space-sm)' }),
    Column({ class: 'weight' },
      Text({ text: stringResource(R.string.home_subject_title, block.subjectLabel, block.title), style: 'bodyMedium', color: C.textSecondary, maxLines: 1, overflow: 'Ellipsis', class: 'fill-w' }),
      Text({ text: summary, style: 'labelSmall', color: C.textMuted }),
    ),
  );
  return clickable(row, { onClick });
}

function GapContent({ minutes, actionable, css }) {
  return Surface({
    shape: 'var(--radius-pill)', color: C.surfaceCard, css,
    children: Row({ css: { padding: 'var(--space-xs) var(--space-md)' }, verticalAlignment: 'CenterVertically' },
      Text({
        text: stringResource(actionable ? R.string.home_gap_free : R.string.home_gap_break, formatGap(minutes)),
        style: 'labelMedium', color: C.textMuted,
      }),
      actionable ? [
        Spacer({ width: 'var(--space-sm)' }),
        Icon({ imageVector: 'Filled.Add', tint: C.brandSoft, size: 'var(--tl-tinyIcon)' }),
        Spacer({ width: 'var(--space-xs)' }),
        Text({ text: R.string.home_add_something, style: 'labelMedium', color: C.brandSoft }),
      ] : null,
    ),
  });
}

/**
 * A free stretch of the day rendered as continuous hour ruler segments (so the axis
 * exists whether or not anything is scheduled), with one centered "add something" chip
 * for the whole stretch. Tapping anywhere in it opens the add flow prefilled to this range.
 */
function GapSegment({ startMinute, endMinute, onAdd }) {
  const actionable = isActionableStudyGap(endMinute - startMinute);
  const breakpoints = [startMinute];
  let hour = (Math.trunc(startMinute / 60) + 1) * 60;
  while (hour < endMinute) { breakpoints.push(hour); hour += 60; }
  breakpoints.push(endMinute);
  const rows = [];
  for (let i = 0; i < breakpoints.length - 1; i++) {
    const segStart = breakpoints[i];
    const segEnd = breakpoints[i + 1];
    const isHourMark = segStart % 60 === 0 && segStart !== startMinute;
    rows.push(TimelineRow({
      time: isHourMark ? formatClock(segStart) : null,
      lineColor: C.hairlineSoft, dashed: true, showNode: false, nodeColor: Transparent,
      minContentHeight: axisHeight(segEnd - segStart), contentBottomPadding: 'var(--space-none)',
    }));
  }
  const box = Box({ contentAlignment: 'Center', class: 'fill-w' },
    Column({ class: 'fill-w', css: { width: '100%' } }, ...rows),
    GapContent({ minutes: endMinute - startMinute, actionable }),
  );
  if (actionable) clickable(box, { onClick: onAdd });
  return box;
}

function NowMarkerContent() {
  return Row({ class: 'fill-w', verticalAlignment: 'CenterVertically', css: { padding: 'var(--space-xs) 0' } },
    Text({ text: R.string.home_now_marker.toUpperCase(), style: 'labelMedium', color: C.brandSoft, fontWeight: 'Bold' }),
    Spacer({ width: 'var(--space-sm)' }),
    h('div', { style: { flex: '1 1 0', height: 'var(--space-xxs)', background: C.brandSoft } }),
  );
}

function EmptyDayTimeline({ onPlanMyDay }) {
  return TimelineRow({
    time: null, lineColor: C.hairlineSoft, dashed: true, showNode: false, nodeColor: Transparent,
    content: Column({ class: 'fill-w', css: { padding: 'var(--space-xxxl) 0' } },
      Text({ text: R.string.home_empty_today_title, style: 'titleSmall', color: C.text }),
      Spacer({ height: 'var(--space-md)' }),
      Button({
        onClick: onPlanMyDay,
        colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }),
        shape: 'var(--radius-lg)',
      }, Text({ text: R.string.home_plan_my_day })),
    ),
  });
}

/** DayTimelineSection({ entries, activeSprint, onStartScheduled, onStartMissed, onRequestReschedule, onOpenAdd, onOpenAddInGap }) */
export function DayTimelineSection(p) {
  const { entries, activeSprint, onStartScheduled, onStartMissed, onRequestReschedule, onOpenAdd, onOpenAddInGap } = p;
  const hasAnyBlock = entries.some((it) => it.type === 'Study' || it.type === 'Fixed');
  const expandedIds = rememberMutable('DayTimelineSection.expandedIds', () => new Map());
  const nowMinute = entries.find((it) => it.type === 'NowMarker')?.minuteOfDay ?? null;
  // LaunchedEffect(nowMinute != null) { if (nowMinute != null) nowRequester.bringIntoView() }
  const nowEffect = rememberBox('DayTimelineSection.nowEffect', () => ({ key: undefined }));
  let nowRow = null;

  const col = Column({ class: 'fill-w', css: p.css });
  if (!hasAnyBlock) {
    col.append(EmptyDayTimeline({ onPlanMyDay: onOpenAdd }));
  } else {
    for (const entry of entries) {
      if (entry.type === 'Study') {
        const block = entry.block;
        const isRunning = activeSprint?.sessionId === block.id;
        const expandedNow = expandedIds.value.get(block.id) === true;
        const isMissed = !block.completed && !isRunning && nowMinute != null && block.endMinuteOfDay <= nowMinute;
        const color = block.completed ? C.success : isRunning ? C.brandSoft : isMissed ? C.warning : C.border;
        col.append(TimelineRow({
          time: formatClock(block.startMinuteOfDay),
          lineColor: color, dashed: false, showNode: true, nodeColor: color,
          minContentHeight: (block.completed && !expandedNow) || (block.subtopics.length > 0 && !expandedNow)
            ? 'var(--space-none)'
            : studyBlockMinHeight(block.durationMinutes),
          content: StudyBlockContent({
            block, isRunning, isMissed,
            remainingLabel: isRunning ? formatRemaining(activeSprint?.remainingSec ?? 0) : null,
            expanded: expandedNow,
            canStartNow: nowMinute != null,
            onToggleExpand: () => {
              const next = new Map(expandedIds.value);
              next.set(block.id, !(expandedIds.value.get(block.id) ?? false));
              expandedIds.value = next;
            },
            onStart: () => { if (isMissed) onStartMissed(block.id); else onStartScheduled(block.id); },
            onReschedule: () => onRequestReschedule(block),
          }),
        }));
      } else if (entry.type === 'Fixed') {
        const block = entry.block;
        col.append(TimelineRow({
          time: formatClock(block.startMinuteOfDay),
          lineColor: C.border, dashed: false, showNode: true, nodeColor: C.textSecondary,
          minContentHeight: studyBlockMinHeight(block.endMinuteOfDay - block.startMinuteOfDay),
          content: FixedCommitmentContent(block),
        }));
      } else if (entry.type === 'Gap') {
        col.append(GapSegment({
          startMinute: entry.startMinuteOfDay, endMinute: entry.endMinuteOfDay,
          onAdd: () => onOpenAddInGap(entry.startMinuteOfDay, entry.endMinuteOfDay),
        }));
      } else if (entry.type === 'NowMarker') {
        nowRow = TimelineRow({
          time: formatClock(entry.minuteOfDay),
          lineColor: C.brandSoft, dashed: false, showNode: true, nodeColor: C.brandSoft,
          content: NowMarkerContent(),
        });
        col.append(nowRow);
      }
    }
  }
  const key = nowMinute != null;
  if (nowEffect.value.key !== key) {
    nowEffect.value.key = key;
    if (key) {
      // After NavHost / observe restore their scroll positions (both run in the next frames).
      requestAnimationFrame(() => requestAnimationFrame(() => {
        const live = document.querySelector('[data-home-now-row]');
        bringIntoView(live);
      }));
    }
  }
  if (nowRow) nowRow.setAttribute('data-home-now-row', '');
  return col;
}

function FixedCommitmentContent(block) {
  return Surface({
    shape: 'var(--radius-lg)', color: C.elevated, class: 'fill-w',
    children: Column({ class: 'fill-w', css: { padding: 'var(--space-md)' }, verticalArrangement: { spacedBy: 'var(--space-xs)' } },
      Text({ text: R.string.home_fixed_tag.toUpperCase(), style: 'labelMedium', color: C.textSecondary, fontWeight: 'SemiBold' }),
      Text({ text: block.customLabel ?? fixedCommitmentLabel(block.kind), style: 'titleMedium', color: C.text, fontWeight: 'SemiBold' }),
      Text({
        text: stringResource(R.string.home_time_range, formatClock(block.startMinuteOfDay), formatClock(block.endMinuteOfDay)),
        style: 'bodyMedium', color: C.textSecondary,
      }),
    ),
  });
}

function fixedCommitmentLabel(kind) {
  switch (kind) {
    case 'school': return R.string.home_fixed_school;
    case 'coaching': return R.string.home_fixed_coaching;
    case 'lecture': return R.string.home_fixed_lecture;
    case 'tuition': return R.string.home_fixed_tuition;
    case 'work': return R.string.home_fixed_work;
    case 'commute': return R.string.home_fixed_commute;
    case 'other': return R.string.home_fixed_other;
    default: return R.string.home_fixed_meal;
  }
}
