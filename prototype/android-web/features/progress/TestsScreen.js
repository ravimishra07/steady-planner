// Port of feature/progress/TestsScreen.kt — TestsRoute, TestsScreen, MockTrendCard, subjectColor, subjectLabel, mockTypeLabel.
// MockTrendChart's Canvas → inline SVG redrawn at the measured size (ResizeObserver), same geometry in px (= dp).
import { h } from '../../platform/h.js';
import { viewModel, LaunchedEffect, onDispose } from '../../platform/nav.js';
import {
  C, Column, Row, Box, Spacer, Text, Icon, IconButton, Button, FilterChip, HorizontalDivider, AppCard,
  SessionListItem, ProLockCard,
} from '../../design/components.js';
import { MockSubject, MockTestType } from '../../domain/MockTest.js';
import { formatInsightDate } from '../../domain/StudyAnalytics.js';
import { TestsViewModel } from './TestsViewModel.js';
import { R, stringResource } from './strings.js';
import { RouteHost, noShrink } from './_local.js';

const sp = (k) => `var(--space-${k})`;
const sz = (k) => `var(--size-${k})`;

export function TestsRoute({
  attemptRepository, mockTestRepository, proRepository, onBack, onAddMock, onEditMock, modifier, onOpenPaywall = () => {},
}) {
  const vm = viewModel('TestsViewModel', () => new TestsViewModel(attemptRepository, mockTestRepository, proRepository));
  // Back from the form: re-read the log.
  LaunchedEffect([], () => { vm.refresh(); });
  return RouteHost(vm.state, (state) => TestsScreen({
    state, onBack, onAddMock, onEditMock, onToggleSubject: (s) => vm.toggleSubject(s), onOpenPaywall, modifier,
  }));
}

export function TestsScreen({ state, onBack, onAddMock, onEditMock, onToggleSubject, modifier, onOpenPaywall = () => {} }) {
  const header = Row({ class: 'fill-w', css: { padding: `${sp('sm')} ${sp('xs')}` }, verticalAlignment: 'CenterVertically' },
    IconButton({ onClick: onBack, children: Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: R.string.day_log_back, tint: C.textSecondary }) }),
    Text({ text: R.string.tests_title, style: 'titleLarge', color: C.text, fontWeight: 'SemiBold', class: 'weight' }),
  );
  let body;
  if (state.loading) body = TestsNote(R.string.insights_loading);
  else if (state.loadFailed) body = TestsNote(R.string.insights_load_failed);
  else if (!state.hasPlan) body = TestsNote(R.string.insights_no_plan);
  else {
    const content = state.mocks.length === 0
      ? [Text({ text: R.string.tests_empty, style: 'bodyMedium', color: C.textMuted, css: { paddingTop: sp('xxl') } })]
      : [
        SectionHeading(R.string.tests_trend, R.string.tests_trend_subtitle),
        state.proUnlocked
          ? MockTrendCard({ points: state.trend, visible: state.visibleSubjects, onToggle: onToggleSubject })
          : ProLockCard({ title: R.string.pro_lock_trend_title, body: R.string.pro_lock_trend_body, actionLabel: R.string.pro_lock_action, onUnlock: onOpenPaywall }),
        SectionHeading(R.string.tests_history, R.string.tests_history_subtitle),
        MockList(state.mocks, onEditMock),
      ];
    body = Column({ class: 'weight fill-w scroll-y', css: { padding: `0 ${sp('screen')}` } },
      Button({
        onClick: onAddMock, class: 'fill-w', css: { height: sz('ctaHeight') },
        children: [Icon({ imageVector: 'Outlined.Add', contentDescription: null, size: sz('smallIcon') }), Spacer({ width: sp('sm') }), Text(R.string.tests_add)],
      }),
      ...content,
      Spacer({ height: sp('xl') }),
    );
    body.setAttribute('data-scroll-key', 'tests');
    noShrink(body);
  }
  return Column({ modifier, class: 'weight fill-w' }, header, body);
}

// Pro: shown only when unlocked (TestsScreen).
export function MockTrendCard({ points, visible, onToggle, class: cls }) {
  const latest = points[points.length - 1] ?? null;
  const legend = Row({ class: 'scroll-x', css: { marginTop: sp('md'), maxWidth: '100%' }, horizontalArrangement: { spacedBy: sp('sm') }, verticalAlignment: 'CenterVertically' },
    LegendSwatch(C.brand, R.string.tests_total),
    ...MockSubject.entries.map((subject) => FilterChip({
      selected: visible.has(subject),
      onClick: () => onToggle(subject),
      label: Text(subjectLabel(subject)),
      leadingIcon: SwatchDot(subjectColor(subject)),
    })),
  );
  legend.setAttribute('data-scroll-key', 'tests-legend');
  return AppCard({ class: ['fill-w', cls], children: Column({ class: 'fill-w', css: { padding: sp('lg') } },
    MockTrendChart(points, visible),
    legend,
    latest != null
      ? Text({ text: stringResource(R.string.tests_latest, latest.total, latest.maxTotal, formatInsightDate(latest.date)), style: 'bodySmall', color: C.textSecondary, css: { paddingTop: sp('sm') } })
      : null,
  ) });
}

/**
 * Score as a share of its maximum, oldest mock on the left. Totals are out of the mock's
 * maximum; a subject is out of a quarter of it, so all lines share one 0–100% scale.
 */
function MockTrendChart(points, visible) {
  const box = h('div', { style: { width: '100%', alignSelf: 'stretch', height: sz('progressChartTall'), flex: 'none', position: 'relative' } });
  const svg = h('svg', { attrs: { width: '100%', height: '100%' } });
  svg.style.cssText = 'display:block;overflow:visible;position:absolute;inset:0';
  box.append(svg);
  const cssPx = (name) => parseFloat(getComputedStyle(box).getPropertyValue(name)) || 0;
  const draw = () => {
    const width = box.clientWidth, height = box.clientHeight;
    if (!width || !height) return;
    svg.replaceChildren();
    const strokeWidth = cssPx('--stroke-emphasis');
    const dot = cssPx('--stroke-selected') * 1.5;
    const hairline = cssPx('--stroke-hairline');
    for (const fraction of [0, 0.5, 1]) {
      const y = height * (1 - fraction);
      svg.append(h('svg:line', { x1: 0, y1: y, x2: width, y2: y, stroke: C.borderSubtle, 'stroke-width': hairline }));
    }
    if (points.length === 0) return;
    const x = (index) => (points.length === 1 ? width / 2 : width * index / (points.length - 1));
    const y = (value, max) => height * (1 - Math.min(Math.max(value / Math.max(max, 1), 0), 1));
    const series = (color, values) => {
      if (values.length > 1) {
        const d = values.map(([, at], i) => `${i === 0 ? 'M' : 'L'}${at.x} ${at.y}`).join(' ');
        svg.append(h('svg:path', { d, fill: 'none', stroke: color, 'stroke-width': strokeWidth }));
      }
      for (const [, at] of values) svg.append(h('svg:circle', { cx: at.x, cy: at.y, r: dot, fill: color }));
    };
    for (const subject of MockSubject.entries.filter((it) => visible.has(it))) {
      const values = [];
      points.forEach((p, i) => {
        const v = p.subjects.get(subject);
        if (v != null) values.push([i, { x: x(i), y: y(v, Math.trunc(p.maxTotal / MockSubject.entries.length)) }]);
      });
      series(subjectColor(subject), values);
    }
    series(C.brand, points.map((p, i) => [i, { x: x(i), y: y(p.total, p.maxTotal) }]));
  };
  const ro = new ResizeObserver(draw);
  ro.observe(box);
  onDispose(() => ro.disconnect());
  return box;
}

function MockList(mocks, onEdit) {
  return AppCard({ class: 'fill-w', children: mocks.flatMap((mock, index) => {
    const type = mockTypeLabel(mock.type);
    const blank = mock.name.trim().length === 0;
    return [
      SessionListItem({
        time: formatInsightDate(mock.date),
        title: blank ? type : mock.name,
        detail: blank ? '' : type,
        duration: stringResource(R.string.tests_score, mock.total, mock.maxTotal),
        onClick: () => onEdit(mock.id),
      }),
      index < mocks.length - 1 ? HorizontalDivider({ color: C.borderSubtle }) : null,
    ];
  }).filter(Boolean) });
}

function SectionHeading(title, subtitle) {
  return Column({ css: { paddingTop: sp('xxl'), paddingBottom: sp('md') } },
    Text({ text: title, style: 'titleLarge', color: C.text, fontWeight: 'SemiBold' }),
    Text({ text: subtitle, style: 'bodySmall', color: C.textMuted, css: { paddingTop: sp('xs') } }),
  );
}

function LegendSwatch(color, label) {
  return Row({ verticalAlignment: 'CenterVertically', css: { minHeight: sz('touchTarget'), flex: 'none' } },
    SwatchDot(color),
    Spacer({ width: sp('xs') }),
    Text({ text: label, style: 'labelMedium', color: C.textSecondary }),
  );
}

function SwatchDot(color) {
  return h('div', { style: { width: sz('legendDot'), height: sz('legendDot'), borderRadius: '50%', background: color, flex: 'none' } });
}

function TestsNote(text) {
  return Box({ class: 'weight fill-w', css: { padding: sp('screen') }, contentAlignment: 'Center' },
    Text({ text, style: 'bodyLarge', color: C.textMuted }));
}

/** internal fun subjectColor(subject) */
export function subjectColor(subject) {
  switch (subject) {
    case MockSubject.PHYSICS: return C.accentCyan;
    case MockSubject.CHEMISTRY: return C.warning;
    case MockSubject.BOTANY: return C.success;
    default: return C.info;
  }
}

/** internal fun subjectLabel(subject) → label string */
export function subjectLabel(subject) {
  switch (subject) {
    case MockSubject.PHYSICS: return R.string.tests_physics;
    case MockSubject.CHEMISTRY: return R.string.tests_chemistry;
    case MockSubject.BOTANY: return R.string.tests_botany;
    default: return R.string.tests_zoology;
  }
}

/** internal fun mockTypeLabel(type) → label string */
export function mockTypeLabel(type) {
  switch (type) {
    case MockTestType.UNIT_TEST: return R.string.tests_type_unit;
    case MockTestType.PART_TEST: return R.string.tests_type_part;
    case MockTestType.FULL_MOCK: return R.string.tests_type_full;
    default: return R.string.tests_type_pyq;
  }
}
