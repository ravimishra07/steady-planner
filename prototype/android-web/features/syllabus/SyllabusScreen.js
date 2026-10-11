// Port of feature/syllabus/SyllabusScreen.kt — SyllabusRoute + SyllabusScreen.
//
// Local Compose state (remember { mutableStateOf }) lives in one remembered `ui` flow; dialogs that hold
// their own remember{} state get a fresh identity per open (see _local.rememberFor).
// `remember(state.subjects) { selectedSubjectKey }` is reproduced literally: whenever the subject list changes
// (any tick/expand rebuilds it) the selection resets to the first subject, exactly like the Kotlin.
import { h } from '../../platform/h.js';
import { observe, viewModel, LaunchedEffect, remember } from '../../platform/nav.js';
import { deepEquals } from '../../domain/kt.js';
import { minutesToHours } from '../../domain/Metrics.js';
import { SyllabusTickState } from '../../domain/Syllabus.js';
import { PendingSyllabusPick } from '../../domain/StudySession.js';
import {
  C, alpha, clickable, Column, Row, Box, Spacer, Text, Icon, IconButton, Surface, LazyColumn,
  CircularProgressIndicator, LinearProgressIndicator, HorizontalDivider, SecondaryTabRow, Tab, TabRowDefaults,
  AppCard, AppCardTone, BorderStroke, CircleShape,
} from '../../design/components.js';
import { SyllabusViewModel } from './SyllabusViewModel.js';
import { StageChipRow, StageLabelsDialog } from './StageChips.js';
import { ChapterAction, ChapterMenuButton, RenameChapterDialog } from './ChapterActions.js';
import { CoachingDialog } from './CoachingDialog.js';
import { PriorityDetail, PriorityDetailDialog, PriorityLabel } from './PriorityUi.js';
import { R, str, plural, dp, animateContentSize } from './_local.js';

export function SyllabusRoute({
  examPackRepository, topicProgressRepository, attemptRepository, targetSyllabusRepository,
  rollingPlanRepository, chapterStageRepository, stageLabelStore, proRepository,
  onStartTopic, onOrganise, modifier, onOpenPaywall = () => {},
}) {
  const vm = viewModel('SyllabusViewModel', () => new SyllabusViewModel(
    examPackRepository, topicProgressRepository, attemptRepository, targetSyllabusRepository,
    rollingPlanRepository, chapterStageRepository, stageLabelStore, proRepository,
  ));
  LaunchedEffect([], () => { vm.refresh(); });
  return observe(vm.state, (state) => SyllabusScreen({
    state,
    onToggleExpand: (k) => vm.toggleExpand(k),
    onToggleTick: (k) => vm.toggleTick(k),
    onTapStage: (id, s) => vm.tapStage(id, s),
    onUndoRevision: (id) => vm.undoRevision(id),
    onSaveStageLabels: (l) => vm.saveStageLabels(l),
    onRenameChapter: (id, t) => vm.renameChapter(id, t),
    onToggleParked: (id) => vm.toggleParked(id),
    onSaveCoaching: (c) => vm.saveCoaching(c),
    onStartTopic,
    onOrganise,
    onOpenPaywall,
    modifier,
  }), { style: { display: 'flex', flexDirection: 'column', minHeight: '0' } });
}

const SyllabusFilter = Object.freeze({ DUE: 'DUE', CLASS_11: 'CLASS_11', CLASS_12: 'CLASS_12' });

export function SyllabusScreen(p) {
  const ui = remember('syllabus:ui', () => ({
    coachingRow: null, // { row } — a new object per open
    stageLabelsOpen: null, // {} per open, null when closed
    renameRow: null, // { row }
    filters: new Set(),
    filtersOpen: false,
    priorityDetail: null,
  }));
  const sel = remember('syllabus:selected', () => ({ subjects: undefined, key: null }));
  const scrollToTop = remember('syllabus:scrollToTop', () => ({ pending: false }));
  return observe(ui, (u) => {
    const state = p.state;
    const s = sel.value;
    if (s.subjects === undefined || !deepEquals(s.subjects, state.subjects)) {
      s.subjects = state.subjects;
      s.key = state.subjects[0]?.key ?? null;
    }
    const set = (patch) => { ui.value = { ...ui.value, ...patch }; };
    return renderScreen(p, u, s, set, scrollToTop.value);
  }, { style: { display: 'flex', flexDirection: 'column', flex: '1 1 auto', minHeight: '0' } });
}

function renderScreen(p, u, sel, set, scrollToTop) {
  const { state } = p;
  const onChapterAction = (row, action) => {
    switch (action) {
      case ChapterAction.RENAME: set({ renameRow: { row } }); break;
      case ChapterAction.RESET_NAME: p.onRenameChapter(row.key, null); break;
      case ChapterAction.PARK: p.onToggleParked(row.key); break;
      case ChapterAction.COACHING: set({ coachingRow: { row } }); break;
      default:
    }
  };
  const filters = u.filters;
  const selectedSubjectKey = sel.key;
  const visibleSubjects = state.subjects.filter((subject) => subject.key === selectedSubjectKey);
  const selectedSubject = visibleSubjects[0] ?? null;
  const dueCount = selectedSubject ? selectedSubject.chapterStats.filter((it) => !it.complete).length : 0;
  const shownChapterCount = selectedSubject ? selectedSubject.chapterStats.filter((c) => statMatches(c, filters)).length : 0;

  const column = Column({ class: 'fill' },
    Row({
      class: 'fill-w', verticalAlignment: 'CenterVertically',
      css: { height: 'var(--size-tabBarHeight)', paddingLeft: 'var(--space-lg)', paddingRight: 'var(--space-xs)', flex: 'none' },
    },
    Text({ text: R.string.syllabus_title, style: 'titleLarge', color: C.text, class: 'weight' }),
    IconButton({ onClick: () => set({ filtersOpen: true }), children: Icon({ imageVector: 'Filled.FilterAlt', contentDescription: R.string.syllabus_filter, tint: C.textSecondary }) }),
    IconButton({ onClick: () => set({ stageLabelsOpen: {} }), children: Icon({ imageVector: 'Filled.Tune', contentDescription: R.string.syllabus_stage_labels_title, tint: C.textSecondary }) }),
    IconButton({ onClick: p.onOrganise, children: Icon({ imageVector: 'Filled.EditNote', contentDescription: R.string.syllabus_edit, tint: C.textSecondary }) })),
  );

  if (state.loading) {
    column.append(Box({ class: 'weight fill-w', contentAlignment: 'Center' }, CircularProgressIndicator()));
  } else if (state.isEmpty) {
    column.append(EmptySyllabus({ onOrganise: p.onOrganise }));
  } else {
    column.append(SubjectTabs({
      subjects: state.subjects,
      selectedKey: selectedSubjectKey,
      onSelect: (subject) => {
        sel.key = subject.key;
        scrollToTop.pending = true;
        set({});
      },
    }));
    const list = LazyColumn({
      class: 'weight fill-w',
      scrollKey: 'syllabus-list',
      contentPadding: 'var(--space-lg) var(--space-lg) var(--space-xxl) var(--space-lg)',
      verticalArrangement: { spacedBy: 'var(--space-md)' },
    }, ...visibleSubjects.map((subject) => SubjectCard({
      subject,
      onToggleExpand: p.onToggleExpand,
      onToggleTick: p.onToggleTick,
      onTapStage: p.onTapStage,
      onUndoRevision: p.onUndoRevision,
      onStartTopic: p.onStartTopic,
      filters,
      pyqFirstYear: state.pyqFirstYear,
      pyqLastYear: state.pyqLastYear,
      onPriorityClick: (detail) => set({ priorityDetail: detail }),
      onChapterAction,
    })));
    if (scrollToTop.pending) {
      scrollToTop.pending = false;
      // listState.animateScrollToItem(0), after the re-render restored the old offset.
      requestAnimationFrame(() => list.scrollTo({ top: 0, behavior: 'smooth' }));
    }
    column.append(list);
  }

  const box = Box({ modifier: p.modifier, class: 'fill' }, column);
  box.style.flex = '1 1 auto';
  box.style.minHeight = '0';

  if (u.priorityDetail) {
    box.append(PriorityDetailDialog({
      detail: u.priorityDetail, firstYear: state.pyqFirstYear, lastYear: state.pyqLastYear,
      onDismiss: () => set({ priorityDetail: null }),
    }));
  }
  if (u.coachingRow) {
    const row = u.coachingRow.row;
    box.append(CoachingDialog({
      openId: u.coachingRow, chapterId: row.key, chapterName: row.name, initial: row.coaching,
      onSave: p.onSaveCoaching, onDismiss: () => set({ coachingRow: null }),
    }));
  }
  if (u.renameRow) {
    const row = u.renameRow.row;
    box.append(RenameChapterDialog({
      openId: u.renameRow, currentName: row.name, originalName: row.originalName,
      onSave: (name) => p.onRenameChapter(row.key, name), onDismiss: () => set({ renameRow: null }),
    }));
  }
  if (u.stageLabelsOpen) {
    box.append(StageLabelsDialog({
      openId: u.stageLabelsOpen,
      labels: state.stageLabels,
      customLocked: !state.proUnlocked && state.stageLabels.customLabel == null,
      onUnlockCustom: () => { set({ stageLabelsOpen: null }); p.onOpenPaywall(); },
      onSave: p.onSaveStageLabels,
      onDismiss: () => set({ stageLabelsOpen: null }),
    }));
  }
  if (u.filtersOpen && selectedSubject != null) {
    box.append(SyllabusFilterPopup({
      filters, dueCount, shownChapterCount,
      onToggle: (selected) => {
        const next = new Set(filters);
        if (next.has(selected)) next.delete(selected); else next.add(selected);
        set({ filters: next });
      },
      onReset: () => set({ filters: new Set() }),
      onDismiss: () => set({ filtersOpen: false }),
    }));
  }
  return box;
}

function statMatches(stat, filters) {
  if (filters.has(SyllabusFilter.DUE) && stat.complete) return false;
  return matchesClass(stat.classNumber, filters);
}
function rowMatches(row, filters) {
  if (filters.has(SyllabusFilter.DUE) && row.tickState === SyllabusTickState.ALL) return false;
  return matchesClass(row.classNumber, filters);
}
function matchesClass(classNumber, filters) {
  const class11 = filters.has(SyllabusFilter.CLASS_11);
  const class12 = filters.has(SyllabusFilter.CLASS_12);
  if (class11 === class12) return true;
  if (class11) return classNumber === 11;
  return classNumber === 12;
}

function EmptySyllabus({ onOrganise }) {
  return Column({
    class: 'weight fill-w', verticalArrangement: 'Center', horizontalAlignment: 'CenterHorizontally',
    css: { padding: 'var(--space-screen)' },
  },
  Text({ text: R.string.syllabus_empty_title, style: 'headlineSmall', color: C.text }),
  Text({ text: R.string.syllabus_empty_body, style: 'bodyLarge', color: C.textSecondary, css: { paddingTop: 'var(--space-sm)' } }),
  Surface({
    onClick: onOrganise, shape: 'var(--radius-pill)', color: C.brandDeep, css: { marginTop: 'var(--space-lg)' },
    children: Text({ text: R.string.syllabus_add_first_chapter, color: C.onBrand, style: 'labelLarge', css: { padding: 'var(--space-md) var(--space-lg)' } }),
  }));
}

function subjectIcon(subjectId) {
  switch (subjectId.toLowerCase()) {
    case 'physics': return 'Filled.Bolt';
    case 'chemistry': return 'Filled.Science';
    case 'botany': return 'Filled.LocalFlorist';
    case 'zoology': return 'Filled.Pets';
    default: return 'AutoMirrored.Filled.MenuBook';
  }
}

function SubjectTabs({ subjects, selectedKey, onSelect }) {
  const selectedIndex = Math.max(subjects.findIndex((it) => it.key === selectedKey), 0);
  return SecondaryTabRow({
    selectedTabIndex: selectedIndex,
    class: 'fill-w',
    containerColor: C.surface,
    contentColor: C.textSecondary,
    indicator: TabRowDefaults.SecondaryIndicator({ height: 'var(--stroke-tabIndicator)', color: C.primary }),
    divider: HorizontalDivider({ thickness: 'var(--stroke-hairline)', color: C.outlineVariant }),
    children: subjects.map((subject) => Tab({
      selected: subject.key === selectedKey,
      onClick: () => onSelect(subject),
      css: { height: 'var(--size-syllabusSubjectTabHeight)' },
      selectedContentColor: C.primary,
      unselectedContentColor: C.textSecondary,
      icon: Icon({ imageVector: subjectIcon(subject.subjectId), contentDescription: null, size: 'var(--size-standardIcon)' }),
      text: Text({ text: subject.name, style: 'labelMedium', maxLines: 1 }),
    })),
  });
}

/** Clickable Surface whose explicit size is below 48dp: Compose clamps the touch-target layout to that size. */
const exactSize = (w, hgt) => ({ ...(w ? { width: w, minWidth: w } : {}), ...(hgt ? { height: hgt, minHeight: hgt } : {}) });

function SyllabusFilterPopup({ filters, dueCount, shownChapterCount, onToggle, onReset, onDismiss }) {
  const layer = h('div', { style: { position: 'absolute', inset: '0', zIndex: '5' } });
  const scrim = h('div', {
    style: {
      position: 'absolute', inset: '0',
      background: 'color-mix(in srgb, var(--c-scrim) calc(var(--opacity-modalScrim) * 100%), transparent)',
    },
  });
  clickable(scrim, { onClick: onDismiss });
  const popup = Surface({
    shape: 'var(--radius-extraLarge)',
    color: C.surfaceContainerHigh,
    tonalElevation: dp('elev-floatingTonal'),
    shadowElevation: dp('elev-floatingShadow'),
    css: {
      position: 'absolute', top: 'var(--size-syllabusFilterPopupTop)', right: 'var(--space-md)',
      width: 'calc(100% - 2 * var(--space-md))', maxWidth: 'var(--size-syllabusFilterPopupWidth)',
    },
    children: Column({ class: 'fill-w', css: { padding: '0 var(--space-lg) var(--space-lg) var(--space-lg)' } },
      Row({ class: 'fill-w', verticalAlignment: 'CenterVertically', css: { minHeight: 'var(--size-touchTarget)' } },
        Text({ text: R.string.syllabus_filter, style: 'titleMedium', color: C.text, class: 'weight' }),
        IconButton({
          onClick: onDismiss, css: exactSize('var(--size-timeField)', 'var(--size-timeField)'),
          children: Icon({ imageVector: 'Filled.Close', contentDescription: R.string.syllabus_close_filters, tint: C.textSecondary }),
        })),
      Row({ css: { paddingTop: 'var(--space-sm)' }, horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
        FilterPill({ label: str(R.string.syllabus_filter_due_compact, dueCount), selected: filters.has(SyllabusFilter.DUE), onClick: () => onToggle(SyllabusFilter.DUE) }),
        FilterPill({ label: R.string.syllabus_class_11, selected: filters.has(SyllabusFilter.CLASS_11), onClick: () => onToggle(SyllabusFilter.CLASS_11) }),
        FilterPill({ label: R.string.syllabus_class_12, selected: filters.has(SyllabusFilter.CLASS_12), onClick: () => onToggle(SyllabusFilter.CLASS_12) })),
      Row({ class: 'fill-w', css: { paddingTop: 'var(--space-lg)' }, horizontalArrangement: { spacedBy: 'var(--space-sm)' }, verticalAlignment: 'CenterVertically' },
        Surface({
          onClick: onReset, shape: 'var(--radius-pill)', color: 'transparent', contentColor: C.primary,
          css: exactSize(null, 'var(--size-timeField)'),
          children: Box({ contentAlignment: 'Center', css: { padding: '0 var(--space-md)', height: '100%' } },
            Text({ text: R.string.syllabus_reset_filters, style: 'labelLarge', color: C.primary })),
        }),
        Surface({
          onClick: onDismiss, shape: 'var(--radius-pill)', color: C.primary,
          class: 'weight', css: { ...exactSize(null, 'var(--size-timeField)'), flex: '1 1 0' }, // .touch-target's flex:none would beat .weight
          children: Box({ contentAlignment: 'Center', class: 'fill' },
            Text({ text: plural(R.plurals.syllabus_show_chapters, shownChapterCount, shownChapterCount), style: 'labelLarge', color: C.onPrimary })),
        }))),
  });
  layer.append(scrim, popup);
  return layer;
}

function FilterPill({ label, selected, onClick }) {
  return Surface({
    onClick,
    shape: 'var(--radius-small)',
    color: selected ? C.selectionContainer : 'transparent',
    contentColor: selected ? C.onSelectionContainer : C.textSecondary,
    border: selected ? null : BorderStroke('var(--stroke-hairline)', C.outlineVariant),
    css: exactSize(null, 'var(--size-compactControl)'),
    children: Row({
      class: 'fill-h', css: { padding: '0 var(--space-md)' },
      horizontalArrangement: { spacedBy: 'var(--space-icon)' }, verticalAlignment: 'CenterVertically',
    },
    selected ? Icon({ imageVector: 'Filled.Check', contentDescription: null, tint: C.onSelectionContainer, size: 'var(--size-smallIcon)' }) : null,
    Text({ text: label, style: 'labelLarge', fontWeight: 'Medium', color: selected ? C.onSelectionContainer : C.textSecondary })),
  });
}

function SubjectCard({
  subject, onToggleExpand, onToggleTick, onTapStage, onUndoRevision, onStartTopic, filters,
  pyqFirstYear, pyqLastYear, onPriorityClick, onChapterAction,
}) {
  const content = Column({ class: 'fill-w', css: { padding: 'var(--space-lg)' } });
  const ring = Box({ contentAlignment: 'Center', css: { width: 'var(--size-syllabusRing)', height: 'var(--size-syllabusRing)', flex: 'none' } },
    CircularProgressIndicator({
      progress: subject.percent / 100,
      css: { width: 'var(--size-syllabusRing)', height: 'var(--size-syllabusRing)' },
      color: C.primary,
      trackColor: alpha(C.text, 0.12),
      strokeWidth: dp('size-syllabusRingStroke'),
    }),
    Text({ text: str(R.string.syllabus_percent_value, subject.percent), style: 'titleSmall', fontWeight: 'SemiBold', color: C.text }));
  content.append(Row({ class: 'fill-w', verticalAlignment: 'CenterVertically' },
    ring,
    Spacer({ width: 'var(--space-md)' }),
    Column({ class: 'weight' },
      Text({ text: subject.name, style: 'titleLarge', fontWeight: 'Bold', color: C.text }),
      Text({
        text: plural(R.plurals.syllabus_subject_summary, subject.totalChapterCount,
          subject.doneChapterCount, subject.totalChapterCount, minutesToHours(subject.totalEstimatedMinutes), subject.questions),
        style: 'bodyMedium', color: C.textSecondary, css: { paddingTop: 'var(--space-xxs)' },
      })),
    Spacer({ width: 'var(--space-sm)' }),
    PlayButton({
      size: 'var(--size-syllabusPrimaryAction)',
      filled: true,
      description: str(R.string.syllabus_continue_topic, subject.firstTopicTitle),
      onClick: () => {
        const topicKey = subject.firstTopicKey;
        if (topicKey == null) return;
        onStartTopic(PendingSyllabusPick({
          nodeKey: topicKey, title: subject.firstTopicTitle, sectionName: subject.name,
          subjectId: subject.subjectId, topicPath: subject.name,
        }));
      },
    })));
  const details = Row({ verticalAlignment: 'CenterVertically' },
    Text({ text: subject.expanded ? R.string.syllabus_hide_details : R.string.syllabus_see_details, style: 'labelLarge', color: C.textMuted }),
    Icon({ imageVector: subject.expanded ? 'Filled.KeyboardArrowUp' : 'Filled.KeyboardArrowDown', contentDescription: null, tint: C.textMuted, size: 'var(--size-smallIcon)' }));
  details.style.marginTop = 'var(--space-sm)'; // padding(top) before clickable: outside the click area
  clickable(details, { onClick: () => onToggleExpand(subject.key) });
  content.append(details);

  if (subject.expanded) {
    content.append(Spacer({ height: 'var(--space-md)' }), HorizontalDivider({ color: C.outlineVariant }));
    const rows = withVisibleBranchEnds(subject.rows.filter((row) => rowMatches(row, filters)));
    content.append(Column({ class: 'fill-w', css: { paddingTop: 'var(--space-sm)' } },
      ...rows.map((row) => SyllabusRowView({
        row, onToggleExpand, onToggleTick, onTapStage, onUndoRevision, onStartTopic,
        pyqFirstYear, pyqLastYear, onPriorityClick, onChapterAction,
      }))));
  }
  const card = AppCard({ tone: AppCardTone.Raised, class: 'fill-w', key: subject.key, children: content });
  card.style.flex = 'none';
  return animateContentSize(card, 'syllabus-card:' + subject.key);
}

function withVisibleBranchEnds(rows) {
  return rows.map((row, index) => {
    if (row.depth === 0) return row;
    let hasLaterSibling = false;
    for (let i = index + 1; i < rows.length; i++) {
      const candidate = rows[i];
      if (candidate.depth < row.depth) break;
      if (candidate.depth === row.depth) { hasLaterSibling = true; break; }
    }
    return row.copy({ isLastChild: !hasLaterSibling });
  });
}

function SyllabusRowView({
  row, onToggleExpand, onToggleTick, onTapStage, onUndoRevision, onStartTopic,
  pyqFirstYear, pyqLastYear, onPriorityClick, onChapterAction,
}) {
  const muted = row.tickState === SyllabusTickState.ALL || row.excluded;
  const rowName = row.isClassGroup ? str(R.string.syllabus_class_number, row.classNumber ?? 0) : row.name;

  const el = Row({ class: 'fill-w', verticalAlignment: 'CenterVertically', css: { minHeight: 'var(--size-syllabusRowMinHeight)' }, key: 'row:' + row.key });
  if (row.hasChildren) clickable(el, { onClick: () => onToggleExpand(row.key) });
  if (row.depth > 0) el.append(TreeConnectors({ row }));
  if (row.hasChildren) {
    el.append(
      Icon({
        imageVector: row.expanded ? 'Filled.KeyboardArrowUp' : 'Filled.KeyboardArrowDown',
        contentDescription: row.expanded ? R.string.syllabus_collapse : R.string.syllabus_expand,
        tint: C.textSecondary,
        size: 'var(--size-smallIcon)',
        css: { marginTop: 'var(--space-xxs)', flex: 'none' },
      }),
      Spacer({ width: 'var(--space-xs)' }),
    );
  }
  const col = Column({ class: 'weight', css: { paddingRight: 'var(--space-sm)' } },
    Text({
      text: rowName,
      style: row.depth === 0 ? 'titleMedium' : row.depth === 1 ? 'bodyLarge' : 'bodyMedium',
      fontWeight: row.depth === 0 ? 'SemiBold' : 'Medium',
      color: muted ? C.textMuted : C.text,
    }));
  if (row.excluded) col.append(Text({ text: R.string.syllabus_parked, style: 'labelMedium', color: C.textMuted }));
  if (row.priority) {
    const priority = row.priority;
    col.append(PriorityLabel({ priority, firstYear: pyqFirstYear, lastYear: pyqLastYear, onClick: () => onPriorityClick(PriorityDetail(rowName, priority)) }));
  }
  if (row.depth <= 1) {
    let label;
    if (row.depth === 1) {
      const hours = Math.trunc(row.estimatedMinutes / 60);
      const minutes = row.estimatedMinutes % 60;
      label = minutes === 0 ? str(R.string.syllabus_hours_short, hours) : str(R.string.syllabus_hours_minutes, hours, minutes);
    } else label = str(R.string.syllabus_percent_value, row.percent);
    const bar = LinearProgressIndicator({
      progress: row.percent / 100,
      class: 'weight',
      css: { height: 'var(--size-progressHeight)', borderRadius: 'var(--space-xxs)', overflow: 'hidden' },
      color: muted ? C.success : C.brandSoft,
      trackColor: alpha(C.text, 0.1),
    });
    col.append(Row({ class: 'fill-w', verticalAlignment: 'CenterVertically', css: { paddingTop: 'var(--space-sm)' } },
      bar, Spacer({ width: 'var(--space-sm)' }), Text({ text: label, style: 'labelSmall', color: C.textMuted, css: { flex: 'none' } })));
  }
  if (row.coaching) {
    const c = row.coaching;
    col.append(Text({
      text: str(R.string.coaching_row_summary, c.lecturesDone, c.lectures, c.dppsDone, c.dpps),
      style: 'labelMedium', color: C.textSecondary, css: { paddingTop: 'var(--space-xs)' },
    }));
  }
  if (row.stageChips.length > 0 && !row.excluded) {
    col.append(StageChipRow({
      chapterId: row.key, chips: row.stageChips, onTapStage, onUndoRevision,
      modifier: { css: { paddingTop: 'var(--space-sm)' } },
    }));
  }
  el.append(col);
  el.append(StatusDot({ state: row.tickState, onClick: () => onToggleTick(row.key) }));
  if (row.isChapter) {
    el.append(ChapterMenuButton({
      menuKey: row.key, renamed: row.renamed, parked: row.excluded, showCoaching: true,
      onAction: (action) => onChapterAction(row, action),
    }));
  }
  if (!row.hasChildren) {
    el.append(Spacer({ width: 'var(--space-sm)' }), PlayButton({
      size: 'var(--size-syllabusRowAction)',
      filled: false,
      onClick: () => onStartTopic(PendingSyllabusPick({
        nodeKey: row.key, title: rowName, sectionName: row.sectionName, subjectId: row.subjectId, topicPath: row.topicPath,
      })),
    }));
  }
  return el;
}

/** Canvas: ancestor rails, the row's own rail and the rounded hook into the row. */
function TreeConnectors({ row }) {
  const branchWidth = dp('size-syllabusBranchIndent');
  const railOffset = dp('size-syllabusRailOffset');
  const hookTop = dp('size-syllabusHookTop');
  const hookHeight = dp('size-syllabusHookHeight');
  const hookWidth = dp('size-syllabusHookWidth');
  const strokeWidth = dp('stroke-hairline');
  const color = 'var(--c-outlineVariant)';
  const box = h('div', { style: { width: `calc(var(--size-syllabusBranchIndent) * ${row.depth})`, alignSelf: 'stretch', flex: 'none', position: 'relative' } });
  const svg = h('svg');
  svg.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;overflow:visible;display:block';
  const line = (x, y2) => h('svg:line', { x1: x, y1: 0, x2: x, y2, stroke: color, 'stroke-width': strokeWidth });
  for (let level = 0; level < Math.max(row.depth - 1, 0); level++) svg.append(line(branchWidth * level + railOffset, '100%'));
  const currentRailX = branchWidth * (row.depth - 1) + railOffset;
  const hookBottom = hookTop + hookHeight;
  svg.append(line(currentRailX, row.isLastChild && !(row.expanded && row.hasChildren) ? hookBottom : '100%'));
  svg.append(h('svg:path', {
    d: `M${currentRailX} ${hookTop} C${currentRailX} ${hookBottom} ${currentRailX} ${hookBottom} ${currentRailX + hookHeight} ${hookBottom} L${currentRailX + hookWidth} ${hookBottom}`,
    fill: 'none', stroke: color, 'stroke-width': strokeWidth,
  }));
  box.append(svg);
  return box;
}

function StatusDot({ state, onClick }) {
  const all = state === SyllabusTickState.ALL;
  return Surface({
    onClick,
    shape: CircleShape,
    color: all ? C.success : C.surfaceControl,
    css: { width: 'var(--size-syllabusStatus)', height: 'var(--size-syllabusStatus)', flex: 'none' },
    children: Box({ contentAlignment: 'Center', class: 'fill' },
      Icon({ imageVector: 'Filled.Check', contentDescription: R.string.syllabus_mark_done, tint: all ? C.onSuccess : C.textMuted, css: { margin: 'var(--space-icon)' } })),
  });
}

function PlayButton({ size, filled, description = null, onClick }) {
  return Surface({
    onClick,
    shape: CircleShape,
    color: filled ? C.selectionContainer : 'transparent',
    border: filled ? null : BorderStroke('var(--stroke-emphasis)', C.primary),
    css: { width: size, height: size, flex: 'none' },
    children: Box({ contentAlignment: 'Center', class: 'fill' },
      Icon({
        imageVector: 'Filled.PlayArrow',
        contentDescription: description ?? R.string.syllabus_start_topic,
        tint: filled ? C.onSelectionContainer : C.primary,
        size: `calc(${size} * 0.5)`,
      })),
  });
}
