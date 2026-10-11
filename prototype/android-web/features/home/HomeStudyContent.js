// Port of feature/home HomeStudyContent.kt.
import { h } from '../../platform/h.js';
import { BackHandler } from '../../platform/nav.js';
import {
  C, alpha, Surface, Text, Icon, Row, Column, Box, Spacer, Button, OutlinedButton, TextButton, IconButton, ButtonDefaults,
  FloatingActionButton, FilterChip, OutlinedTextField, HorizontalDivider, LazyColumn, Dialog, TimePicker,
  rememberTimePickerState, clickable, CircleShape,
} from '../../design/components.js';
import { StudyPlacementIssue } from '../../domain/DayTimeline.js';
import { StudyActivityType } from '../../domain/StudyPlan.js';
import { currentMinuteOfDay, formatMinuteOfDay } from '../../domain/StudySession.js';
import { formatInsightDate } from '../../domain/StudyAnalytics.js';
import { topicHours } from '../../domain/Syllabus.js';
import { coerceIn, coerceAtLeast, roundToInt } from '../../domain/kt.js';
import { HomeSheet, StudyPickerLevel } from './HomeUiState.js';
import { HomeCalendarChrome } from './HomeCalendarChrome.js';
import { TodaySummary, DayTimelineSection, formatDuration } from './HomeTimeline.js';
import { TaughtTodayCard, CoachingBacklogCard } from './CoachingCards.js';
import { RescheduleSheet } from './RescheduleSheet.js';
import {
  R, stringResource, pluralStringResource, formatDatePattern, rememberMutable, outer,
} from './_local.js';

/** HomeStudyContent({ state, on…, css? }) — props mirror the Kotlin parameters. */
export function HomeStudyContent(p) {
  const { state } = p;
  // Registered first so the overlays composed below (time pickers) take system back before it, as dialog windows do.
  BackHandler(state.sheet !== HomeSheet.None, p.onBackInStudyPicker);
  const rescheduleTarget = rememberMutable('HomeStudyContent.rescheduleTarget', null);

  const root = Box({ class: 'fill', css: { position: 'relative', height: '100%', ...(p.css || {}) } });
  root.style.alignItems = 'stretch';
  root.style.justifyItems = 'stretch';

  if (state.sheet === HomeSheet.None) {
    const scrollKids = [];
    if (state.placementIssue) {
      scrollKids.push(PlacementIssueMessage({ issue: state.placementIssue, css: { paddingBottom: 'var(--space-md)' } }));
    }
    if (state.canOpenDayLog) {
      scrollKids.push(TextButton({ onClick: () => p.onOpenDay(state.selectedDate), css: outer({ bottom: 'var(--space-sm)' }) },
        Text({ text: R.string.home_view_day_log })));
    }
    const cardGap = outer({ bottom: 'var(--space-lg)' });
    if (state.selectedIsToday) {
      if (state.freshStart) scrollKids.push(FreshStartCard({ onStartFresh: p.onStartFresh, onNotNow: p.onDismissMissed, css: cardGap }));
      if (state.missedBlocks) {
        scrollKids.push(MissedBlocksPrompt({ missed: state.missedBlocks, onRebalance: p.onRebalanceMissed, onNotNow: p.onDismissMissed, css: cardGap }));
      }
      if (state.planFit) scrollKids.push(PlanFitBanner({ fit: state.planFit, onAdjust: p.onAdjustPlan, css: cardGap }));
      if (state.coaching) {
        scrollKids.push(TaughtTodayCard({ coaching: state.coaching, onNextLecture: p.onNextLecture, onLogClass: p.onLogClass, css: cardGap }));
        scrollKids.push(CoachingBacklogCard({ coaching: state.coaching, css: cardGap }));
      }
    }
    // One recovery surface at a time: start fresh, then the missed-days prompt, then today's.
    const recovery = state.missedDayRecovery;
    if (recovery && state.freshStart == null && state.missedBlocks == null) {
      scrollKids.push(MissedDayRecoveryPanel({
        recovery,
        onStartNextNow: () => p.onStartMissedSession(recovery.nextSessionId),
        onReplan: p.onReplanRestOfToday,
        css: cardGap,
      }));
    }
    scrollKids.push(DayTimelineSection({
      entries: state.dayTimeline,
      activeSprint: state.activeSprint,
      onStartScheduled: p.onStartScheduledSession,
      onStartMissed: p.onStartMissedSession,
      onRequestReschedule: (block) => { rescheduleTarget.value = block; },
      onOpenAdd: p.onOpenAdd,
      onOpenAddInGap: p.onOpenAddInGap,
    }));
    scrollKids.push(Spacer({ height: 'var(--size-themePreview)' }));

    const scroll = Column({
      class: 'weight fill-w scroll-y',
      css: { padding: '0 var(--space-screen)' },
    }, ...scrollKids);
    scroll.setAttribute('data-scroll-key', 'home-day');
    noShrink(scroll);

    root.append(Column({ class: 'fill', css: { height: '100%' } },
      HomeCalendarChrome({
        selectedDate: state.selectedDate,
        weekDays: state.weekDays,
        monthDays: state.monthDays,
        expanded: state.calendarExpanded,
        onToggleExpanded: p.onToggleCalendarExpanded,
        onSelectDate: p.onSelectDate,
      }),
      h('div', { style: { padding: 'var(--space-md) var(--space-lg) var(--space-xl)', alignSelf: 'stretch', flex: 'none' } },
        TodaySummary({
          entries: state.dayTimeline,
          plannedMinutes: state.plannedTodayMinutes,
          completedMinutes: state.completedTodayMinutes,
          selectedIsToday: state.selectedIsToday,
          onEditPlan: p.onEditPlan,
          class: 'fill-w',
        })),
      scroll,
    ));

    const block = rescheduleTarget.value;
    if (block) {
      root.append(...RescheduleSheet({
        block,
        onMoveToNextSlot: () => { p.onRescheduleToNextSlot(block.id); rescheduleTarget.value = null; },
        onMoveToTomorrow: () => { p.onRescheduleToTomorrow(block.id); rescheduleTarget.value = null; },
        onChooseTime: (minute) => { p.onRescheduleToTime(block.id, minute); rescheduleTarget.value = null; },
        onDismiss: () => { rescheduleTarget.value = null; },
        onSkip: () => { p.onSkipSession(block.id); rescheduleTarget.value = null; },
      }));
    }
    if (state.selectedIsToday) {
      const fab = FloatingActionButton({
        onClick: p.onOpenAdd,
        containerColor: C.brandDeep,
        contentColor: C.onBrand,
        shape: CircleShape,
        css: { position: 'absolute', right: 'var(--space-screen)', bottom: 'var(--space-md)' },
      }, Icon({ imageVector: 'Filled.Add', contentDescription: R.string.home_add_study }));
      root.append(fab);
    }
  } else {
    root.append(StudyPickerFrame({
      state,
      onBack: p.onBackInStudyPicker,
      onClose: p.onDismissSheet,
      onSetQuery: p.onSetPickerQuery,
      onSelectSection: p.onSelectPickerSection,
      onOpenSubtopics: p.onOpenPickerSubtopics,
      onPickTopic: p.onPickTopic,
      onSetDuration: p.onSetDuration,
      onSetActivityType: p.onSetActivityType,
      onSetScheduledMinute: p.onSetScheduledMinute,
      onConfirmStart: p.onConfirmStart,
      onConfirmAddToPlan: p.onConfirmAddToPlan,
    }));
  }
  return root;
}

const StudyPickerRow = (o) => Object.freeze(o);

/** Scrolling columns measure children at full height (verticalScroll / LazyColumn never squeeze items). */
function noShrink(scroller) {
  for (const c of scroller.children) c.style.flexShrink = '0';
  return scroller;
}

function StudyPickerFrame(p) {
  const { state } = p;
  const picking = state.sheet === HomeSheet.PickTopic;
  const query = state.pickerQuery.trim();

  const col = Column({ class: 'fill', css: { height: '100%', background: C.bg } });
  col.append(StudyPickerTopBar({
    title: picking ? R.string.home_start_study : R.string.home_how_long,
    showBack: (picking && (query !== '' || state.pickerLevel !== StudyPickerLevel.Subjects)) || !picking,
    onBack: p.onBack,
    onClose: p.onClose,
  }));

  if (picking) {
    col.append(h('div', { style: { padding: 'var(--space-md) var(--space-screen)', alignSelf: 'stretch', flex: 'none' } },
      OutlinedTextField({
        key: 'home-picker-search',
        value: state.pickerQuery,
        onValueChange: p.onSetQuery,
        class: 'fill-w',
        singleLine: true,
        shape: 'var(--radius-lg)',
        placeholder: Text({ text: R.string.home_picker_search_hint }),
        leadingIcon: Icon({ imageVector: 'Filled.Search', tint: C.textMuted }),
        colors: {
          focusedBorderColor: C.brandDeep,
          unfocusedBorderColor: C.border,
          focusedContainerColor: C.surface,
          unfocusedContainerColor: C.surface,
          focusedTextColor: C.text,
          unfocusedTextColor: C.text,
          focusedPlaceholderColor: C.textDisabled,
          unfocusedPlaceholderColor: C.textDisabled,
        },
      })));

    let rows;
    if (query !== '') rows = searchRows(state.sections, query);
    else if (state.pickerLevel === StudyPickerLevel.Subjects) rows = subjectRows(state.sections);
    else if (state.pickerLevel === StudyPickerLevel.Topics) {
      rows = state.pickerSectionIndex == null ? [] : topicRows(state.sections, state.pickerSectionIndex);
    } else {
      const sectionIndex = state.pickerSectionIndex;
      const topicIndex = state.pickerTopicPath[0];
      if (sectionIndex == null || topicIndex == null) rows = [];
      else {
        const root = state.sections[sectionIndex]?.topics?.[topicIndex] ?? null;
        rows = root == null ? [] : leafRows({
          node: root,
          sectionIndex,
          rootPath: state.pickerTopicPath,
          ancestors: [root.name],
          sectionName: state.sections[sectionIndex].name,
          subjectId: state.sections[sectionIndex].id ?? '',
        });
      }
    }

    let label;
    if (query !== '') label = R.string.home_picker_suggestions;
    else if (state.pickerLevel === StudyPickerLevel.Subjects) label = R.string.home_picker_browse_subject;
    else if (state.pickerLevel === StudyPickerLevel.Topics) label = state.sections[state.pickerSectionIndex ?? -1]?.name ?? '';
    else label = state.sections[state.pickerSectionIndex ?? -1]?.topics?.[state.pickerTopicPath[0] ?? -1]?.name ?? '';

    const items = [Text({ text: label, style: 'labelSmall', color: C.textMuted, css: { paddingBottom: 'var(--space-xs)' } })];
    if (rows.length === 0) {
      items.push(Text({ text: R.string.home_picker_no_match, style: 'bodyMedium', color: C.textMuted, css: { padding: 'var(--space-xxl) 0' } }));
    } else {
      for (const row of rows) {
        const selectRow = () => p.onPickTopic(row.nodeId, row.title, state.sections[row.sectionIndex].name, row.subjectId, row.topicPath);
        items.push(StudyPickerRowView({
          row,
          onSelect: row.path.length === 0 ? null : selectRow,
          onBrowse: row.opensSubtopics
            ? () => { if (row.path.length === 0) p.onSelectSection(row.sectionIndex); else p.onOpenSubtopics(row.path); }
            : null,
        }));
      }
    }
    const list = LazyColumn({
      class: 'weight fill-w',
      scrollKey: `home-picker-${state.pickerLevel.name}-${state.pickerSectionIndex}-${state.pickerTopicPath.join('.')}-${query}`,
      contentPadding: '0 var(--space-screen) var(--space-xxl)',
      verticalArrangement: { spacedBy: 'var(--space-sm)' },
    }, ...items);
    noShrink(list);
    col.append(list);
  } else {
    col.append(noShrink(LazyColumn({
      class: 'weight fill-w',
      scrollKey: 'home-duration',
      contentPadding: 'var(--space-md) var(--space-screen)',
    }, DurationPickerContent({
      topicTitle: state.pendingTopic?.title ?? '',
      topicPath: (state.pendingTopic?.topicPath?.trim() ? state.pendingTopic.topicPath : null) ?? state.pendingTopic?.sectionName ?? '',
      selectedMinutes: state.selectedDurationMinutes,
      onSelectMinutes: p.onSetDuration,
      activityType: state.pendingTopic?.activityType ?? StudyActivityType.LEARN,
      onSelectActivityType: p.onSetActivityType,
      customEndMinuteOfDay: state.customEndMinuteOfDay,
      onSelectEndMinute: p.onSetScheduledMinute,
      placementIssue: state.placementIssue,
      revisionDue: state.pendingRevisionDue,
    }))));
    const cta = { css: { width: '100%', height: 'var(--size-ctaHeight)' }, shape: 'var(--radius-lg)' };
    col.append(Column({
      class: 'fill-w',
      css: { padding: 'var(--space-md) var(--space-screen)', flex: 'none' },
      verticalArrangement: { spacedBy: 'var(--space-sm)' },
    },
    state.selectedIsToday
      ? Button({ ...cta, onClick: p.onConfirmStart, colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }) },
        Text({ text: R.string.home_start_now }))
      : null,
    OutlinedButton({ ...cta, onClick: p.onConfirmAddToPlan }, Text({ text: R.string.home_add_to_plan }))));
  }
  return col;
}

function StudyPickerTopBar({ title, showBack, onBack, onClose }) {
  const square = { css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' } };
  return Column({ class: 'fill-w', css: { flex: 'none' } },
    Row({ class: 'fill-w', css: { padding: 'var(--space-xs)' }, verticalAlignment: 'CenterVertically' },
      showBack
        ? IconButton({ ...square, onClick: onBack },
          Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: R.string.home_picker_back, tint: C.text }))
        : Spacer({ width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' }),
      Text({ text: title, style: 'bodyLarge', color: C.text, class: 'weight' }),
      IconButton({ ...square, onClick: onClose },
        Icon({ imageVector: 'Filled.Close', contentDescription: R.string.home_picker_close, tint: C.text })),
    ),
    HorizontalDivider({ color: C.hairlineSoft }),
  );
}

function StudyPickerRowView({ row, onSelect, onBrowse }) {
  const texts = Column({ class: 'weight', css: { padding: 'var(--space-sm) 0' } },
    Text({ text: row.title, style: 'bodyLarge', color: C.text }),
    row.subtitle.trim() !== ''
      ? Text({ text: row.subtitle, style: 'bodySmall', color: C.textMuted, maxLines: 2, overflow: 'Ellipsis' })
      : null,
  );
  clickable(texts, { onClick: onBrowse ?? onSelect ?? (() => {}) });
  return Surface({
    key: row.id,
    shape: 'var(--radius-lg)', color: C.surface, class: 'fill-w',
    children: Row({
      class: 'fill-w',
      css: { padding: 'var(--space-sm) var(--space-sm) var(--space-sm) var(--space-lg)' },
      verticalAlignment: 'CenterVertically',
    },
    texts,
    onSelect != null && onBrowse != null ? TextButton({ onClick: onSelect }, Text({ text: R.string.home_picker_choose })) : null,
    onBrowse != null
      ? IconButton({ onClick: onBrowse, css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' } },
        Icon({ imageVector: 'Filled.ChevronRight', contentDescription: stringResource(R.string.home_picker_browse_deeper, row.title), tint: C.textMuted }))
      : null),
  });
}

function subjectRows(sections) {
  return sections.map((section, index) => StudyPickerRow({
    id: `subject-${index}`,
    title: section.name,
    subtitle: pluralStringResource(
      R.plurals.home_picker_subject_summary, section.topics.length, section.topics.length,
      roundToInt(section.topics.reduce((s, it) => s + topicHours(it), 0)),
    ),
    sectionIndex: index,
    path: [],
    topicPath: section.name,
    opensSubtopics: true,
    nodeId: section.topics[0]?.id ?? '',
    subjectId: section.id ?? '',
  }));
}

function topicRows(sections, sectionIndex) {
  const section = sections[sectionIndex];
  if (!section) return [];
  return section.topics.map((topic, index) => StudyPickerRow({
    id: `topic-${sectionIndex}-${index}`,
    title: topic.name,
    subtitle: topic.children.length === 0
      ? formatTopicHours(topic)
      : pluralStringResource(R.plurals.home_picker_subtopic_summary, countLeaves(topic), countLeaves(topic), formatTopicHours(topic)),
    sectionIndex,
    path: [index],
    topicPath: topic.name,
    opensSubtopics: topic.children.length > 0,
    nodeId: topic.id ?? '',
    subjectId: section.id ?? '',
  }));
}

function searchRows(sections, query) {
  const q = query.toLowerCase();
  return sections.flatMap((section, sectionIndex) => section.topics.flatMap((topic, topicIndex) => leafRows({
    node: topic,
    sectionIndex,
    rootPath: [topicIndex],
    ancestors: [topic.name],
    sectionName: section.name,
    subjectId: section.id ?? '',
  }))).filter((row) => [row.title, row.topicPath, row.subtitle].some((it) => it.toLowerCase().includes(q))).slice(0, 40);
}

function leafRows({ node, sectionIndex, rootPath, ancestors, sectionName, subjectId }) {
  if (node.children.length === 0) {
    const topicPath = pathOf(ancestors);
    return [StudyPickerRow({
      id: `search-${sectionIndex}-${rootPath.join('-')}`,
      title: node.name,
      subtitle: stringResource(R.string.home_picker_path, topicPath, sectionName),
      sectionIndex,
      path: rootPath,
      topicPath: stringResource(R.string.home_picker_path, sectionName, topicPath),
      opensSubtopics: false,
      nodeId: node.id ?? '',
      subjectId,
    })];
  }
  return node.children.flatMap((child, index) => leafRows({
    node: child,
    sectionIndex,
    rootPath: [...rootPath, index],
    ancestors: [...ancestors, child.name],
    sectionName,
    subjectId,
  }));
}

const countLeaves = (node) => (node.children.length === 0 ? 1 : node.children.reduce((s, c) => s + countLeaves(c), 0));

function DurationPickerContent(p) {
  const options = [15, 30, 45];
  const showTimePicker = rememberMutable('DurationPickerContent.showTimePicker', false);
  const pickerOpenings = rememberMutable('DurationPickerContent.pickerOpenings', 0);
  const liveEndMinute = coerceIn(p.customEndMinuteOfDay ?? (currentMinuteOfDay() + p.selectedMinutes), 0, 24 * 60 - 1);

  const endsRow = Row({ class: 'fill-w', css: { padding: 'var(--space-lg)' }, verticalAlignment: 'CenterVertically' },
    Text({ text: R.string.home_ends_at, style: 'bodyLarge', color: C.textMuted, class: 'weight' }),
    Text({ text: formatMinuteOfDay(liveEndMinute), style: 'titleSmall', color: C.text }),
  );
  clickable(endsRow, { onClick: () => { pickerOpenings.value = pickerOpenings.value + 1; showTimePicker.value = true; } });

  const activityOptions = [
    [StudyActivityType.LEARN, R.string.home_activity_learn],
    [StudyActivityType.PRACTICE, R.string.home_activity_practice],
    [StudyActivityType.REVISION, R.string.home_activity_revision],
    [StudyActivityType.MOCK_TEST, R.string.home_activity_mock],
  ];

  const col = Column({ class: 'fill-w', verticalArrangement: { spacedBy: 'var(--space-md)' } },
    Surface({
      shape: 'var(--radius-lg)', color: C.surface, class: 'fill-w',
      children: Column({ class: 'fill-w', css: { padding: 'var(--space-lg)' } },
        Text({ text: p.topicTitle, style: 'titleSmall', color: C.text }),
        p.topicPath.trim() !== ''
          ? Text({ text: p.topicPath, style: 'bodySmall', color: C.textMuted, css: { paddingTop: 'var(--space-xs)' } })
          : null),
    }),
    Text({ text: R.string.home_activity_type, style: 'bodyLarge', color: C.text }),
    Row({ horizontalArrangement: { spacedBy: 'var(--space-xs)' } },
      ...activityOptions.map(([type, label]) => FilterChip({
        selected: p.activityType === type,
        onClick: () => p.onSelectActivityType(type),
        label: Text({ text: label }),
      }))),
    Text({ text: R.string.home_sprint_length, style: 'bodyLarge', color: C.text }),
    Row({ horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      ...options.map((minutes) => FilterChip({
        selected: p.selectedMinutes === minutes,
        onClick: () => p.onSelectMinutes(minutes),
        label: Text({ text: stringResource(R.string.home_duration_min, minutes) }),
      }))),
    Surface({
      shape: 'var(--radius-lg)', color: C.surface, class: 'fill-w', css: outer({ top: 'var(--space-lg)' }),
      children: Column({ class: 'fill-w' },
        endsRow,
        p.revisionDue != null ? [
          HorizontalDivider({ color: alpha(C.textMuted, 0.14) }),
          Row({ class: 'fill-w', css: { padding: 'var(--space-lg)' }, verticalAlignment: 'CenterVertically' },
            Text({ text: R.string.home_revision_due, style: 'bodyLarge', color: C.textMuted, class: 'weight' }),
            Text({ text: formatDatePattern(p.revisionDue, R.string.home_revision_date_pattern), style: 'titleSmall', color: C.text })),
        ] : null),
    }),
    p.placementIssue ? PlacementIssueMessage({ issue: p.placementIssue }) : null,
  );

  if (!showTimePicker.value) return col;
  const timePickerState = rememberTimePickerState({
    key: `home:durationTime:${pickerOpenings.value}`,
    initialHour: Math.trunc(liveEndMinute / 60),
    initialMinute: liveEndMinute % 60,
    is24Hour: false,
  });
  const dialog = Dialog({
    key: 'home:durationTime',
    onDismissRequest: () => { showTimePicker.value = false; },
    children: Surface({
      shape: 'var(--radius-lg)', color: C.surfaceCard,
      children: Column({ css: { padding: 'var(--space-lg)' }, horizontalAlignment: 'CenterHorizontally' },
        TimePicker({ state: timePickerState }),
        Row({ class: 'fill-w', css: { paddingTop: 'var(--space-md)' }, horizontalArrangement: 'End' },
          TextButton({ onClick: () => { showTimePicker.value = false; } }, Text({ text: R.string.home_cancel })),
          TextButton({
            onClick: () => {
              p.onSelectEndMinute(timePickerState.hour * 60 + timePickerState.minute);
              showTimePicker.value = false;
            },
          }, Text({ text: R.string.home_ok })))),
    }),
  });
  col.append(dialog);
  return col;
}

function PlacementIssueMessage({ issue, css }) {
  const message = {
    [StudyPlacementIssue.INVALID_DURATION.name]: R.string.home_error_duration,
    [StudyPlacementIssue.OUTSIDE_DAY.name]: R.string.home_error_outside_day,
    [StudyPlacementIssue.OVERLAPS_STUDY.name]: R.string.home_error_study_overlap,
    [StudyPlacementIssue.OVERLAPS_FIXED.name]: R.string.home_error_fixed_overlap,
    [StudyPlacementIssue.NO_AVAILABLE_TIME.name]: R.string.home_error_no_available_time,
  }[issue.name];
  return Text({ text: message, style: 'bodyMedium', color: C.warning, css });
}

const primaryPill = () => ({
  colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }),
  shape: 'var(--radius-pill)',
});

function promptCard(css, ...kids) {
  return Surface({
    shape: 'var(--radius-lg)', color: C.elevated, class: 'fill-w', css,
    children: Column({ class: 'fill-w', css: { padding: 'var(--space-lg)' }, verticalArrangement: { spacedBy: 'var(--space-sm)' } }, ...kids),
  });
}

function MissedBlocksPrompt({ missed, onRebalance, onNotNow, css }) {
  return promptCard(css,
    Text({ text: pluralStringResource(R.plurals.home_missed_blocks_title, missed.count, missed.count), style: 'titleMedium', color: C.text }),
    Text({ text: R.string.home_missed_blocks_body, style: 'bodyMedium', color: C.textSecondary }),
    Row({ horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      Button({ ...primaryPill(), onClick: onRebalance }, Text({ text: R.string.home_missed_blocks_yes })),
      TextButton({ onClick: onNotNow }, Text({ text: R.string.home_missed_blocks_not_now }))));
}

/** "Start fresh from today" (§5.5). No count and no warning colour: it is a restart, not a reckoning. */
function FreshStartCard({ onStartFresh, onNotNow, css }) {
  return promptCard(css,
    Text({ text: R.string.home_fresh_start_title, style: 'titleMedium', color: C.text }),
    Text({ text: R.string.home_fresh_start_body, style: 'bodyMedium', color: C.textSecondary }),
    Row({ horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      Button({ ...primaryPill(), onClick: onStartFresh }, Text({ text: R.string.home_fresh_start_action })),
      TextButton({ onClick: onNotNow }, Text({ text: R.string.home_missed_blocks_not_now }))));
}

/** Deadline mode only: part of the remaining work cannot be placed before finish-by. */
function PlanFitBanner({ fit, onAdjust, css }) {
  const hours = coerceAtLeast(roundToInt(fit.shortByMinutes / 60), 1);
  return Surface({
    shape: 'var(--radius-lg)', color: C.elevated, class: 'fill-w', css,
    children: Row({
      class: 'fill-w',
      css: { padding: 'var(--space-md) var(--space-lg)' },
      verticalAlignment: 'CenterVertically',
      horizontalArrangement: { spacedBy: 'var(--space-md)' },
    },
    Text({
      text: stringResource(fit.calibrated ? R.string.home_fit_banner_calibrated : R.string.home_fit_banner, hours, formatInsightDate(fit.finishBy)),
      style: 'bodyMedium', color: C.text, class: 'weight',
    }),
    TextButton({ onClick: onAdjust }, Text({ text: R.string.home_fit_adjust }))),
  });
}

function MissedDayRecoveryPanel({ recovery, onStartNextNow, onReplan, css }) {
  return promptCard(css,
    Text({ text: R.string.home_recovery_title, style: 'titleMedium', color: C.text }),
    Text({
      text: pluralStringResource(R.plurals.home_recovery_body, recovery.missedCount, recovery.missedCount, formatDuration(recovery.availableTodayMinutes)),
      style: 'bodyMedium', color: C.textSecondary,
    }),
    Text({ text: stringResource(R.string.home_recovery_next, recovery.nextSessionTitle), style: 'labelLarge', color: C.text, maxLines: 2, overflow: 'Ellipsis' }),
    Button({ ...primaryPill(), onClick: onStartNextNow, enabled: recovery.canStartNow }, Text({ text: R.string.home_start_next_now })),
    TextButton({ onClick: onReplan }, Text({ text: R.string.home_replan_rest_today })),
    !recovery.canStartNow ? Text({ text: R.string.home_recovery_busy_now, style: 'bodySmall', color: C.textMuted }) : null);
}

const pathOf = (parts) => (parts.length === 0 ? '' : parts.reduce((path, part) => stringResource(R.string.home_picker_path, path, part)));

function formatTopicHours(node) {
  const hours = topicHours(node);
  return hours > 0 ? stringResource(R.string.home_estimated_hours, coerceAtLeast(roundToInt(hours), 1)) : '';
}
