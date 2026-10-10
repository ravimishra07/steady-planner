// Port of feature/onboarding/steps/OnboardingParitySteps.kt
//
// Local `remember { mutableStateOf() }` → rememberLocal (_local.js): reset when the composable leaves the screen.
// HorizontalPager graphicsLayer (scale/alpha by distance), the preview ring and currentPage-driven label/dots are
// updated from the pager's scroll position, like Compose reading pagerState during scroll.
import { h } from '../../../platform/h.js';
import {
  Column, Row, Box, Spacer, Text, Icon, IconButton, TextButton, OutlinedButton, Surface, RadioButton, AlertDialog, Dialog,
  TimePicker, rememberTimePickerState, DatePicker, DatePickerDialog, rememberDatePickerState,
  SingleChoiceSegmentedButtonRow, SegmentedButton, SegmentedButtonDefaults, HorizontalDivider, CircularProgressIndicator,
  HorizontalPager, rememberPagerState, AppValueStepper, AppStepperControl, BorderStroke, C, CircleShape, RoundedCornerShape,
  clickable, slot,
} from '../../../design/components.js';
import { AppTheme, AccentPalette, BackgroundAppearance } from '../../../design/colors.js';
import { PlanMode } from '../../../domain/PlanMode.js';
import { StartingPoint } from '../../../domain/ExamAttempt.js';
import { today as localToday, plusDays, plusMonths, maxDate, toEpochDay, ofEpochDay, daysBetween, year, monthValue, dayOfMonth } from '../../../domain/time.js';
import { coerceAtLeast, coerceAtMost } from '../../../domain/kt.js';
import { stringResource, pluralStringResource } from '../res/values/strings.js';
import { CapacityIssuesCard } from '../OnboardingScreen.js';
import { CommitmentIssue } from '../OnboardingViewModel.js';
import { OnboardingSelectableCard, selectionBorder } from '../components/OnboardingComponents.js';
import { DAY_SHAPES } from '../model/OnboardingUiState.js';
import { ExamCatalog } from '../model/ExamCatalog.js';
import { rememberLocal, rememberLocalKey, insetBorder, DATE_PICKER_NO_HEADER, OutlinedTextFieldWithLabelPadding } from '../_local.js';

const MS_DAY = 86_400_000;
const utcMillis = (date) => toEpochDay(date) * MS_DAY;          // date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
const fromUtcMillis = (ms) => ofEpochDay(Math.floor(ms / MS_DAY)); // Instant.ofEpochMilli(ms).atZone(UTC).toLocalDate()
const hairline = (color) => BorderStroke('var(--stroke-hairline)', color);
const tpInk = (ink, alphaVar) => `color-mix(in srgb, ${ink} calc(var(${alphaVar}) * 100%), transparent)`;

// =============================================================================================
// Theme step
// =============================================================================================
/**
 * "Choose a theme": background carousel. The centered page is the selection; the
 * accent picker lives above Continue (OnboardingAccentPicker).
 * OnboardingAppearanceStep(background, accent, onBackground)
 */
export function OnboardingAppearanceStep(background, accent, onBackground) {
  const options = BackgroundAppearance.entries;
  const pagerState = rememberPagerState({ initialPage: options.indexOf(background), pageCount: options.length, key: 'ob:themePager' });
  const accentColor = C.primary;
  const ringColor = accentColor;
  const gapColor = C.bg;

  const pageBoxes = [];
  const pager = HorizontalPager({
    state: pagerState,
    pageSize: 'var(--tp-pageWidth)',
    pageSpacing: 'var(--tp-pageSpacing)',
    contentPadding: 'calc((100% - var(--tp-pageWidth)) / 2)',
    class: 'fill-w',
    css: { paddingBlock: 'var(--space-sm)' },
    // snapshotFlow { settledPage } → onBackground when it differs.
    onPageChange: (page) => { const settled = options[page]; if (settled !== background) onBackground(settled); },
    pageContent: (page) => {
      const option = options[page];
      const name = stringResource(backgroundLabel(option));
      const box = Box({ css: { borderRadius: 'var(--tp-corner)', willChange: 'transform, opacity' } },
        BackgroundMiniature(option, accent.primaryFor(option.isDark)));
      clickable(box, { role: 'radio', onClick: () => pagerState.animateScrollToPage(page) });
      box.setAttribute('aria-label', name);
      pageBoxes.push(box);
      return box;
    },
  });
  pager.setAttribute('role', 'radiogroup');
  // BoxWithConstraints(Modifier.bleedHorizontally(Spacing.lg)): the pager ignores the screen margin.
  const bleed = h('div', { style: { marginInline: 'calc(-1 * var(--space-lg))', width: 'calc(100% + 2 * var(--space-lg))', alignSelf: 'stretch' } }, pager);

  const label = Text({ text: stringResource(backgroundLabel(options[pagerState.currentPage])), style: 'titleSmall', color: C.text, textAlign: 'Center', class: 'fill-w', css: { paddingTop: 'var(--space-md)' } });
  const dots = options.map((_, index) => Box({
    css: {
      width: index === pagerState.currentPage ? 'var(--size-pagerDotActive)' : 'var(--size-pagerDot)',
      height: 'var(--size-pagerDot)', borderRadius: CircleShape,
      background: index === pagerState.currentPage ? accentColor : C.border,
      transition: 'width var(--motion-medium1) var(--ease-standard)', // animateDpAsState default spring ≈ 250ms
    },
  }));
  const dotRow = Row({ class: 'fill-w', css: { paddingTop: 'var(--space-sm)' }, horizontalArrangement: { spacedBy: 'var(--space-sm)', alignment: 'Center' } }, ...dots);

  let shownPage = -1;
  const frame = () => {
    const el = pager;
    if (!el.isConnected || !el.clientWidth) return;
    const mid = el.scrollLeft + el.clientWidth / 2;
    const stride = pageBoxes[0]?.parentElement ? (pageBoxes[1]?.parentElement?.offsetLeft ?? 0) - pageBoxes[0].parentElement.offsetLeft : 0;
    let current = 0, best = Infinity;
    pageBoxes.forEach((box, i) => {
      const page = box.parentElement;
      const c = page.offsetLeft + page.offsetWidth / 2;
      const d = Math.abs(c - mid);
      if (d < best) { best = d; current = i; }
      const distance = Math.min(stride ? d / stride : 0, 1);
      const minScale = parseFloat(getComputedStyle(el).getPropertyValue('--tp-minScale')) || 1;
      const minAlpha = parseFloat(getComputedStyle(el).getPropertyValue('--tp-minAlpha')) || 1;
      box.style.transform = `scale(${1 - (1 - minScale) * distance})`;
      box.style.opacity = String(1 - (1 - minAlpha) * distance);
    });
    if (current !== shownPage) {
      shownPage = current;
      pageBoxes.forEach((box, i) => {
        const centered = i === current;
        box.setAttribute('aria-checked', String(centered));
        // previewRing: 2dp gap in bg, then 2dp accent ring, outside the bounds.
        box.style.boxShadow = centered ? `0 0 0 var(--tp-ringGap) ${gapColor}, 0 0 0 calc(var(--tp-ringGap) + var(--tp-ringWidth)) ${ringColor}` : 'none';
      });
      label.textContent = stringResource(backgroundLabel(options[current]));
      dots.forEach((d, i) => {
        d.style.width = i === current ? 'var(--size-pagerDotActive)' : 'var(--size-pagerDot)';
        d.style.background = i === current ? accentColor : C.border;
      });
    }
  };
  pager.addEventListener('scroll', () => requestAnimationFrame(frame), { passive: true });
  requestAnimationFrame(() => requestAnimationFrame(frame));

  return Column({ class: 'fill-w' }, bleed, label, dotRow);
}

/** Five accent circles in one pill outline, docked above Continue on the theme step. */
export function OnboardingAccentPicker(accent, onAccent, modifier = {}) {
  const dark = AppTheme.colors.isDark;
  const row = Row({
    class: ['fill-w', modifier.class],
    css: {
      border: `var(--stroke-hairline) solid ${C.outline}`, borderRadius: CircleShape,
      padding: 'var(--space-xs) var(--space-md)', ...(modifier.css || {}),
    },
    horizontalArrangement: 'SpaceBetween',
    verticalAlignment: 'CenterVertically',
  },
  AccentPalette.entries.map((option) => {
    const selected = option === accent;
    const name = stringResource(accentLabel(option));
    const dot = Box({ contentAlignment: 'Center', css: { width: 'var(--size-accentDot)', height: 'var(--size-accentDot)', borderRadius: CircleShape, background: option.primaryFor(dark) } },
      selected ? Icon({ imageVector: 'Filled.Check', tint: option.onPrimaryFor(dark), size: 'var(--size-accentCheck)' }) : null);
    const target = Box({ contentAlignment: 'Center', css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)', borderRadius: CircleShape } }, dot);
    clickable(target, { role: 'radio', checked: selected, onClick: () => onAccent(option) });
    target.setAttribute('aria-label', name);
    return target;
  }));
  row.setAttribute('role', 'radiogroup');
  return row;
}

function AppearanceLabel(labelRes) {
  return Text({ text: stringResource(labelRes), style: 'titleSmall', color: C.textSecondary });
}

/** A Today screen in miniature: header, hero card, two block rows, bottom nav. */
function BackgroundMiniature(option, accent) {
  const ink = option.previewInk;
  const bar = (width, height, color, css = {}) => Box({ css: { width, height, borderRadius: CircleShape, background: color, flex: 'none', ...css } });
  const hero = Column({
    verticalArrangement: 'SpaceBetween',
    css: {
      marginLeft: 'var(--tp-sideMargin)', marginTop: 'var(--tp-heroTop)',
      width: 'calc(100% - 2 * var(--tp-sideMargin))', height: 'var(--tp-heroHeight)',
      borderRadius: 'var(--tp-heroCorner)', background: tpInk(ink, '--tp-inkFill'), padding: 'var(--tp-heroPadding)',
      boxSizing: 'border-box', overflow: 'hidden',
    },
  },
  bar('calc(var(--tp-heroAccentWidth) * 100%)', 'var(--tp-heroBarHeight)', accent),
  bar('calc(var(--tp-heroInkWidth) * 100%)', 'var(--tp-heroBarHeight)', tpInk(ink, '--tp-inkSoft')));
  const items = ['--tp-itemBarWidth', '--tp-itemBarShortWidth'].map((fraction) => Row({
    verticalAlignment: 'CenterVertically',
    horizontalArrangement: { spacedBy: 'var(--tp-itemGap)' },
    css: {
      marginLeft: 'var(--tp-sideMargin)', marginTop: 'var(--tp-itemTop)',
      width: 'calc(100% - 2 * var(--tp-sideMargin))', height: 'var(--tp-itemHeight)',
      borderRadius: 'var(--tp-itemCorner)', background: tpInk(ink, '--tp-inkFill'), padding: '0 var(--tp-itemPadding)',
      boxSizing: 'border-box', overflow: 'hidden', flexWrap: 'nowrap',
    },
  },
  Box({ css: { width: 'var(--tp-itemRing)', height: 'var(--tp-itemRing)', flex: 'none', borderRadius: CircleShape, border: `var(--tp-itemRingStroke) solid ${accent}`, boxSizing: 'border-box' } }),
  // (block - itemPadding*2) * fraction: the row's content width times the fraction.
  h('div', { style: { flex: 'none', width: `calc((var(--tp-mini-block) - 2 * var(--tp-itemPadding)) * var(${fraction}))`, height: 'var(--tp-itemBarHeight)', borderRadius: CircleShape, background: tpInk(ink, '--tp-inkStrong') } })));
  const content = Column({ class: 'fill-w', css: { paddingTop: 'var(--tp-topPadding)' } },
    bar('calc(var(--tp-headerWidth) * 100%)', 'var(--tp-headerHeight)', ink, { marginLeft: 'var(--tp-sideMargin)' }),
    bar('calc(var(--tp-lineWidth) * 100%)', 'var(--tp-lineHeight)', tpInk(ink, '--tp-inkSoft'), { marginLeft: 'var(--tp-sideMargin)', marginTop: 'var(--tp-lineTop)' }),
    hero, ...items);
  const nav = Row({
    horizontalArrangement: 'SpaceAround',
    verticalAlignment: 'CenterVertically',
    css: { position: 'absolute', left: 0, right: 0, bottom: 0, height: 'var(--tp-navHeight)', background: tpInk(ink, '--tp-inkNav'), padding: '0 var(--tp-sideMargin)' },
  },
  bar('var(--tp-navActiveWidth)', 'var(--tp-navIcon)', accent),
  [0, 1, 2].map(() => Box({ css: { width: 'var(--tp-navIcon)', height: 'var(--tp-navIcon)', borderRadius: 'var(--tp-navIconCorner)', background: tpInk(ink, '--tp-inkSoft') } })));
  const root = h('div', {
    style: {
      position: 'relative', width: '100%', aspectRatio: 'var(--tp-aspectRatio)', borderRadius: 'var(--tp-corner)', overflow: 'hidden',
      background: option.previewBackground, border: `var(--stroke-hairline) solid ${C.outline}`, boxSizing: 'border-box',
      // inner width (maxWidth after the hairline padding) − 2 × sideMargin
      '--tp-mini-block': 'calc(var(--tp-pageWidth) - 2 * var(--stroke-hairline) - 2 * var(--tp-sideMargin))',
    },
  }, content, nav);
  return root;
}

// =============================================================================================
// Coaching step
// =============================================================================================
const primaryCoachingChoices = [
  { id: 'self', nameRes: 'onboarding_coaching_self', icon: 'Filled.Person' },
  { id: 'school', nameRes: 'onboarding_coaching_school', icon: 'Filled.School' },
  { id: 'coaching', nameRes: 'onboarding_coaching_choice', icon: 'Filled.Groups' },
];

/** M3 Expressive grouped list: rows joined by 2dp gaps, 20dp outer corners, 4dp inner. */
export function OnboardingCoachingStep(selectedId, onSelect) {
  const col = Column({ class: 'fill-w', css: { paddingTop: 'var(--space-sm)' }, verticalArrangement: { spacedBy: 'var(--space-xxs)' } },
    primaryCoachingChoices.map((choice, index) => CoachingRow(choice, selectedId === choice.id, groupShape(index, primaryCoachingChoices.length), () => onSelect(choice.id))));
  col.setAttribute('role', 'radiogroup');
  return col;
}

function groupShape(index, count) {
  const top = index === 0 ? 'var(--radius-group)' : 'var(--radius-extraSmall)';
  const bottom = index === count - 1 ? 'var(--radius-group)' : 'var(--radius-extraSmall)';
  return RoundedCornerShape({ topStart: top, topEnd: top, bottomStart: bottom, bottomEnd: bottom });
}

function CoachingRow(choice, selected, shape, onClick) {
  const primary = C.primary;
  const row = Row({
    class: 'fill-w',
    css: { minHeight: 'var(--size-listRow)', padding: 'var(--space-sm) var(--space-lg)' },
    verticalAlignment: 'CenterVertically',
    horizontalArrangement: { spacedBy: 'var(--space-lg)' },
  },
  Icon({ imageVector: choice.icon, tint: selected ? primary : C.textSecondary, size: 'var(--size-standardIcon)' }),
  Text({ text: stringResource(choice.nameRes), style: 'bodyLarge', fontWeight: selected ? 'Medium' : 'Normal', color: C.text, class: 'weight' }),
  RadioButton({ selected, onClick: null, colors: { selectedColor: primary, unselectedColor: C.outline } }));
  clickable(row, { role: 'radio', checked: selected, onClick });
  return Surface({ class: 'fill-w', shape, color: C.elevated, children: row });
}

// =============================================================================================
// Fixed times step
// =============================================================================================
const ADD_KINDS = ['school', 'coaching', 'lecture', 'tuition', 'work', 'meal', 'commute', 'other'];

/** OnboardingCommitmentsStep(state, issues, onAdd, onRemove, onRename, onToggleDay, onStart, onEnd, onWake, onSleep) */
export function OnboardingCommitmentsStep(state, issues, onAdd, onRemove, onRename, onToggleDay, onStart, onEnd, onWake, onSleep) {
  const showAddOptions = rememberLocal('Commitments/showAddOptions', false);
  const children = [];
  children.push(Row({ class: 'fill-w', verticalAlignment: 'CenterVertically', horizontalArrangement: 'SpaceBetween' },
    Row({ verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      Icon({ imageVector: 'Outlined.Lightbulb', tint: C.textSecondary, size: 'var(--size-smallIcon)' }),
      AppearanceLabel('onboarding_commitments_suggested')),
    OutlinedButton({
      onClick: () => { showAddOptions.value = !showAddOptions.value; },
      children: [
        Icon({ imageVector: 'Filled.Add', size: 'var(--size-smallIcon)' }),
        Text({ text: stringResource('onboarding_commitments_add'), css: { paddingLeft: 'var(--space-sm)' } }),
      ],
    })));
  if (showAddOptions.value) {
    children.push(Row({ class: 'fill-w', css: { flexWrap: 'wrap', rowGap: 'var(--space-sm)' }, horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      ADD_KINDS.map((kind) => insetBorder(Surface({
        onClick: () => { onAdd(kind); showAddOptions.value = false; },
        shape: CircleShape,
        color: 'transparent',
        children: Text({ text: stringResource(commitmentKindLabel(kind)), style: 'labelLarge', css: { padding: 'var(--space-sm) var(--space-md)' } }),
      }), hairline(C.outline)))));
  }
  if (state.commitments.length === 0) {
    children.push(insetBorder(Surface({
      shape: 'var(--radius-md)', color: 'transparent', css: { alignSelf: 'flex-start' },
      children: Row({ css: { padding: 'var(--space-lg)' }, horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
        Icon({ imageVector: 'Filled.EventAvailable' }),
        Text({ text: stringResource('onboarding_commitments_empty') })),
    }), hairline(C.border)));
  } else {
    state.commitments.forEach((commitment) => children.push(CommitmentCard(
      commitment, issues.filter((it) => it.commitmentId === commitment.id), onRemove, onRename, onToggleDay, onStart, onEnd,
    )));
  }
  children.push(AppearanceLabel('onboarding_commitments_awake'));
  children.push(Row({ class: 'fill-w', horizontalArrangement: { spacedBy: 'var(--space-md)' } },
    TimeField('Commitments/wake', stringResource('onboarding_commitments_up_at'), state.wakeMinute, onWake, { class: 'weight' }),
    TimeField('Commitments/sleep', stringResource('onboarding_commitments_lights_out'), state.sleepMinute, onSleep, { class: 'weight' })));
  if (issues.includes(CommitmentIssue.WakeAfterSleep)) children.push(InlineCommitmentIssue(CommitmentIssue.WakeAfterSleep));
  children.push(Row({ class: 'fill-w', verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
    Icon({ imageVector: 'Filled.EventAvailable', tint: C.brand }),
    Text({ text: stringResource('onboarding_commitments_plan_around'), style: 'bodyMedium', color: C.textSecondary })));
  return Column({ class: 'fill-w', css: { alignItems: 'stretch' }, verticalArrangement: { spacedBy: 'var(--space-lg)' } }, ...children);
}

function CommitmentCard(commitment, issues, onRemove, onRename, onToggleDay, onStart, onEnd) {
  const defaultLabel = stringResource(commitment.labelRes);
  const label = commitment.customLabel ?? defaultLabel;
  const showRename = rememberLocal(`Commitment/${commitment.id}/showRename`, false);
  const draftLabel = rememberLocal(`Commitment/${commitment.id}/draftLabel`, label);
  const smallIconButton = { width: 'var(--size-dayControl)', height: 'var(--size-dayControl)', minWidth: 'var(--size-dayControl)', minHeight: 'var(--size-dayControl)' };
  const header = Row({ class: 'fill-w', verticalAlignment: 'CenterVertically' },
    Icon({ imageVector: 'Filled.Groups', tint: C.brand }),
    Text({ text: label, class: 'weight', css: { paddingLeft: 'var(--space-sm)' }, style: 'titleMedium' }),
    // Start and end are already in the fields below; the header shows only the length.
    Text({
      text: stringResource('onboarding_commitments_duration', planHour(((commitment.endMinute - commitment.startMinute + 24 * 60) % (24 * 60)) / 60)),
      style: 'labelMedium', color: C.textSecondary,
    }),
    IconButton({
      onClick: () => { draftLabel.value = label; showRename.value = true; },
      css: smallIconButton,
      children: Icon({ imageVector: 'Filled.Edit', contentDescription: stringResource('onboarding_commitments_rename', label) }),
    }),
    IconButton({
      onClick: () => onRemove(commitment.id),
      css: smallIconButton,
      children: Icon({ imageVector: 'Filled.Close', contentDescription: stringResource('onboarding_commitments_remove', label) }),
    }));
  const times = Row({ class: 'fill-w', horizontalArrangement: { spacedBy: 'var(--space-md)' } },
    TimeField(`Commitment/${commitment.id}/start`, stringResource('onboarding_commitments_starts'), commitment.startMinute, (it) => onStart(commitment.id, it), { class: 'weight' }),
    TimeField(`Commitment/${commitment.id}/end`, stringResource('onboarding_commitments_ends'), commitment.endMinute, (it) => onEnd(commitment.id, it), { class: 'weight' }));
  const days = Row({ class: 'fill-w', horizontalArrangement: { spacedBy: 'var(--space-xs)' } },
    dayOptions.map(([day, labelRes]) => {
      const selected = commitment.days.has(day);
      const s = Surface({
        onClick: () => onToggleDay(commitment.id, day),
        role: 'checkbox',
        checked: selected,
        class: 'weight',
        // .touch-target is flex:none; Modifier.weight(1f) wins in Compose.
        css: { flex: '1 1 0', height: 'var(--size-dayControl)', minHeight: 'var(--size-dayControl)', minWidth: 'var(--space-none)' },
        shape: CircleShape,
        color: selected ? C.primary : 'transparent',
        children: Box({ contentAlignment: 'Center', class: 'fill' },
          Text({ text: stringResource(labelRes), style: 'labelLarge', color: selected ? C.onPrimary : C.text })),
      });
      s.firstElementChild.setAttribute('aria-label', stringResource(dayNames[day]));
      return insetBorder(s, selected ? null : hairline(C.outline));
    }));
  const card = Surface({
    class: 'fill-w',
    shape: 'var(--radius-md)',
    color: C.elevated,
    children: Column({ class: 'fill-w', css: { padding: 'var(--space-md) var(--space-lg)', alignItems: 'stretch' }, verticalArrangement: { spacedBy: 'var(--space-md)' } },
      header, times, days, ...issues.map((issue) => InlineCommitmentIssue(issue))),
  });
  const out = [card];
  if (showRename.value) {
    out.push(AlertDialog({
      onDismissRequest: () => { showRename.value = false; },
      title: Text({ text: stringResource('onboarding_commitments_rename_title') }),
      text: OutlinedTextFieldWithLabelPadding({
        key: `rename-${commitment.id}`,
        value: draftLabel.value,
        onValueChange: (it) => draftLabel.set(it.slice(0, 40), it.length > 40),
        label: Text({ text: stringResource('onboarding_commitments_rename_hint') }),
        singleLine: true,
      }),
      confirmButton: TextButton({
        onClick: () => { onRename(commitment.id, draftLabel.value); showRename.value = false; },
        children: Text({ text: stringResource('onboarding_commitments_rename_save') }),
      }),
      dismissButton: TextButton({
        onClick: () => { showRename.value = false; },
        children: Text({ text: stringResource('onboarding_commitments_rename_cancel') }),
      }),
    }));
  }
  return out;
}

function InlineCommitmentIssue(issue) {
  return Text({ text: issueText(issue), style: 'labelMedium', color: C.danger });
}

function issueText(issue) {
  switch (issue.type) {
    case 'WakeAfterSleep': return stringResource('onboarding_commitments_wake_after_sleep');
    case 'NoDays': return stringResource('onboarding_commitments_no_days', stringResource(issue.labelRes));
    case 'InvalidRange': return stringResource('onboarding_commitments_invalid_range', stringResource(issue.labelRes));
    default: return stringResource('onboarding_commitments_outside_waking', stringResource(issue.labelRes));
  }
}

/** M3 outlined, read-only time field. The whole field opens the time picker. */
function TimeField(id, label, minute, onChange, modifier = {}) {
  const open = rememberLocal(`TimeField/${id}/open`, false);
  const value = clockLabel(minute);
  const openLabel = stringResource('onboarding_time_select');
  const field = OutlinedTextFieldWithLabelPadding({
    key: `time-${id}`,
    value,
    onValueChange: () => {},
    readOnly: true,
    singleLine: true,
    label: Text({ text: label }),
    trailingIcon: Icon({ imageVector: 'Outlined.Schedule' }),
    textStyle: 'bodyLarge',
    shape: 'var(--radius-extraSmall)',
    colors: {
      focusedTextColor: C.text, unfocusedTextColor: C.text,
      focusedBorderColor: C.outline, unfocusedBorderColor: C.outline,
      focusedLabelColor: C.text, unfocusedLabelColor: C.text,
      focusedTrailingIconColor: C.text, unfocusedTrailingIconColor: C.text,
    },
    class: 'fill-w',
    fieldCss: { minHeight: 'var(--size-textField)' },
  });
  // Clickable overlay so a tap anywhere on the field (not just the icon) opens the picker.
  const overlay = h('div', { style: { position: 'absolute', inset: 'var(--space-sm) 0 0 0', borderRadius: 'var(--radius-extraSmall)' } });
  clickable(overlay, { onClick: () => { open.value = true; } });
  overlay.setAttribute('aria-label', `${label}, ${value}`);
  overlay.title = openLabel;
  const box = h('div', { class: modifier.class, style: { position: 'relative', ...(modifier.css || {}) } }, field, overlay);
  const out = [box];
  if (open.value) {
    out.push(MinutePickerDialog(rememberLocalKey(`TimeField/${id}/picker`), minute, () => { open.value = false; }, (it) => { onChange(it); open.value = false; }));
  }
  return out;
}

/** M3 time picker in a basic dialog: "Select time", dial, Cancel / OK. */
function MinutePickerDialog(stateKey, initialMinute, onDismiss, onConfirm) {
  const state = rememberTimePickerState({ initialHour: Math.trunc(initialMinute / 60) % 24, initialMinute: initialMinute % 60, is24Hour: false, key: stateKey });
  return Dialog({
    onDismissRequest: onDismiss,
    properties: { usePlatformDefaultWidth: false },
    children: Surface({
      shape: 'var(--radius-extraLarge)',
      color: C.surfaceContainerHigh,
      children: Column({ css: { padding: 'var(--space-xxl)' } },
        Text({ text: stringResource('onboarding_time_select'), style: 'labelMedium', color: C.text, css: { paddingBottom: 'var(--space-xl)' } }),
        TimePicker({
          state,
          colors: {
            selectorColor: C.primary,
            timeSelectorSelectedContainerColor: C.primary,
            timeSelectorSelectedContentColor: C.onPrimary,
            periodSelectorSelectedContainerColor: C.primary,
            periodSelectorSelectedContentColor: C.onPrimary,
            clockDialSelectedContentColor: C.onPrimary,
          },
        }),
        Row({ css: { alignSelf: 'flex-end' }, horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
          TextButton({ onClick: onDismiss, children: Text({ text: stringResource('onboarding_time_cancel') }) }),
          TextButton({ onClick: () => onConfirm(state.hour * 60 + state.minute), children: Text({ text: stringResource('onboarding_time_ok') }) }))),
    }),
  });
}

// =============================================================================================
// Finish-by step
// =============================================================================================
/** "Finish the syllabus by": Pick a date is deadline mode (preselected), Not sure yet is steady mode. */
export function OnboardingModeStep(state, onMode, onDate, onExamDate) {
  const row = SingleChoiceSegmentedButtonRow({ class: 'fill-w', css: { width: '100%', paddingBlock: 'var(--space-none)' } },
    [[PlanMode.DEADLINE, 'onboarding_mode_yes'], [PlanMode.STEADY, 'onboarding_mode_not_yet']].map(([mode, label], index) => SegmentedButton({
      selected: state.planMode === mode,
      onClick: () => onMode(mode),
      shape: SegmentedButtonDefaults.itemShape({ index, count: 2 }),
      colors: SegmentedButtonDefaults.colors({
        activeContainerColor: C.brandDeep,
        activeContentColor: C.onBrand,
        activeBorderColor: C.brandDeep,
        inactiveContainerColor: 'transparent',
        inactiveContentColor: C.text,
        inactiveBorderColor: C.outline,
      }),
      css: { height: 'var(--size-touchTarget)' },
      label: Text({ text: stringResource(label), style: 'labelLarge' }),
    })));
  const children = [row];
  if (state.planMode === PlanMode.DEADLINE) {
    children.push(Spacer({ height: 'var(--space-xxxl)' }));
    children.push(OnboardingTargetDateStep('ModeStep', state.targetDate, state.examDate, state.examDateOfficial, state.examDateIsConfigured, onDate, onExamDate));
  } else {
    children.push(Spacer({ height: 'var(--space-lg)' }));
    children.push(...ExamDateRow('ModeStep', state.examDate, state.examDateOfficial, state.examDateIsConfigured, onExamDate));
  }
  return Column({ class: 'fill-w', css: { alignItems: 'stretch' } }, ...children);
}

/** OnboardingTargetDateStep(owner, targetDate, examDate, examDateOfficial, examDateIsConfigured, onDate, onExamDate) — owner keys its remembered state. */
export function OnboardingTargetDateStep(owner, targetDate, examDate, examDateOfficial, examDateIsConfigured, onDate, onExamDate) {
  const today = localToday();
  const days = coerceAtLeast(daysBetween(today, targetDate), 1);
  const minMillis = utcMillis(plusDays(today, 7));
  const maxMillis = utcMillis(maxDate(examDate, plusDays(today, 7)));
  const datePickerState = rememberDatePickerState({
    key: rememberLocalKey(`${owner}/TargetDate/picker`),
    initialSelectedDateMillis: utcMillis(targetDate),
    selectableDates: { isSelectableDate: (ms) => ms >= minMillis && ms <= maxMillis, isSelectableYear: () => true },
  });
  const picker = DatePicker({
    state: datePickerState,
    showModeToggle: false,
    title: null,
    headline: null,
    colors: { containerColor: 'transparent' },
    class: ['fill-w', DATE_PICKER_NO_HEADER],
    css: { width: '100%' },
    // LaunchedEffect(selectedDateMillis): report the pick when it differs from targetDate.
    onSelectionChange: (millis) => { const selected = fromUtcMillis(millis); if (selected !== targetDate) onDate(selected); },
  });
  return Column({ class: 'fill-w', css: { alignItems: 'stretch' } },
    Text({ text: formatDate(targetDate, 'd MMM yyyy'), style: 'titleLarge', color: C.text }),
    Text({ text: pluralStringResource('onboarding_date_days_to_go', days, days), style: 'bodyMedium', color: C.textSecondary, css: { paddingTop: 'var(--space-xs)' } }),
    Spacer({ height: 'var(--space-lg)' }),
    picker,
    Spacer({ height: 'var(--space-sm)' }),
    HorizontalDivider({ color: C.border }),
    Spacer({ height: 'var(--space-sm)' }),
    ...ExamDateRow(`${owner}/TargetDate`, examDate, examDateOfficial, examDateIsConfigured, onExamDate));
}

/** The exam date with its honesty label ("expected" until it is official or the student's own), and a way to change it. */
function ExamDateRow(owner, examDate, official, isConfigured, onExamDate) {
  const editing = rememberLocal(`${owner}/ExamDateRow/editing`, false);
  const row = Row({
    class: 'fill-w',
    css: { minHeight: 'var(--size-ctaHeight)' },
    verticalAlignment: 'CenterVertically',
    horizontalArrangement: { spacedBy: 'var(--space-lg)' },
  },
  Icon({ imageVector: 'Filled.CalendarToday', tint: C.textSecondary }),
  Text({
    text: stringResource(official || !isConfigured ? 'onboarding_date_exam_official' : 'onboarding_date_exam_expected', formatDate(examDate, 'd MMM yyyy')),
    style: 'bodyMedium', color: C.textSecondary, class: 'weight',
  }),
  TextButton({ onClick: () => { editing.value = true; }, css: { minHeight: 'var(--size-touchTarget)' }, children: Text({ text: stringResource('onboarding_exam_date_change') }) }));
  const out = [row];
  if (editing.value) {
    const today = localToday();
    const minMillis = utcMillis(plusDays(today, 7));
    const maxMillis = utcMillis(plusMonths(today, 36));
    const state = rememberDatePickerState({
      key: rememberLocalKey(`${owner}/ExamDateRow/picker`),
      initialSelectedDateMillis: utcMillis(examDate),
      selectableDates: { isSelectableDate: (ms) => ms >= minMillis && ms <= maxMillis, isSelectableYear: () => true },
    });
    out.push(DatePickerDialog({
      onDismissRequest: () => { editing.value = false; },
      confirmButton: TextButton({
        onClick: () => {
          if (state.selectedDateMillis != null) onExamDate(fromUtcMillis(state.selectedDateMillis));
          editing.value = false;
        },
        children: Text({ text: stringResource('onboarding_ok') }),
      }),
      dismissButton: TextButton({ onClick: () => { editing.value = false; }, children: Text({ text: stringResource('onboarding_cancel') }) }),
      children: DatePicker({ state, showModeToggle: false }),
    }));
  }
  return out;
}

// =============================================================================================
// Which class step
// =============================================================================================
/** "Which class / attempt?" A dropper starts with the syllabus marked finished once. */
export function OnboardingStartStep({ state, onSelectStartingPoint }) {
  const col = Column({ class: 'fill-w', css: { alignItems: 'stretch' }, verticalArrangement: { spacedBy: 'var(--space-sm)' } },
    [
      [StartingPoint.CLASS_11, 'onboarding_start_class_11', 'onboarding_start_class_11_detail'],
      [StartingPoint.CLASS_12, 'onboarding_start_class_12', 'onboarding_start_class_12_detail'],
      [StartingPoint.DROPPER, 'onboarding_start_dropper', 'onboarding_start_dropper_detail'],
    ].map(([point, title, detail]) => OnboardingSelectableCard({
      title: stringResource(title),
      selected: state.startingPoint === point,
      onClick: () => onSelectStartingPoint(point),
      showUnselectedIndicator: true,
      trailing: Text({ text: stringResource(detail), style: 'bodyMedium', color: C.textSecondary }),
    })));
  return col;
}

// =============================================================================================
// Syllabus step
// =============================================================================================
export function OnboardingSyllabusStep({ state, onToggleSubject, onToggleChapter }) {
  const expandedSubjectId = rememberLocal(`Syllabus/${state.examId}/expanded`, null);
  const chapterCount = state.syllabusSubjects.reduce((s, it) => s + it.chapters.length, 0);
  const selectedCount = state.selectedChapterIds.size;
  const children = [];
  if (ExamCatalog.hasBundledSyllabus(state.examId)) {
    children.push(Text({ text: stringResource('onboarding_syllabus_selected_count', selectedCount, chapterCount), style: 'bodyMedium', color: C.textSecondary }));
    state.syllabusSubjects.forEach((subject) => {
      const subjectIds = subject.chapters.map((it) => it.id);
      const subjectSelectedCount = subjectIds.filter((it) => state.selectedChapterIds.has(it)).length;
      const allSubjectSelected = subjectIds.length > 0 && subjectSelectedCount === subjectIds.length;
      const expanded = expandedSubjectId.value === subject.id;
      const inner = [Row({
        class: 'fill-w',
        css: { padding: 'var(--space-sm) var(--space-xs) var(--space-sm) var(--space-lg)' },
        verticalAlignment: 'CenterVertically',
      },
      Column({ class: 'weight' },
        Text({ text: subject.name, style: 'titleMedium' }),
        Text({ text: stringResource('onboarding_syllabus_subject_selected', subjectSelectedCount, subjectIds.length), style: 'bodySmall', color: C.textSecondary })),
      TextButton({ onClick: () => onToggleSubject(subject.id), children: Text({ text: stringResource(allSubjectSelected ? 'onboarding_syllabus_clear_all' : 'onboarding_syllabus_select_all') }) }),
      IconButton({
        onClick: () => { expandedSubjectId.value = expanded ? null : subject.id; },
        children: Icon({
          imageVector: expanded ? 'Filled.KeyboardArrowUp' : 'Filled.KeyboardArrowDown',
          contentDescription: stringResource(expanded ? 'onboarding_syllabus_collapse_subject' : 'onboarding_syllabus_expand_subject', subject.name),
        }),
      }))];
      if (expanded) {
        const groups = new Map();
        subject.chapters.forEach((c) => { const k = c.classNumber ?? null; if (!groups.has(k)) groups.set(k, []); groups.get(k).push(c); });
        for (const [classNumber, chapters] of groups) {
          inner.push(Text({
            text: classNumber != null ? stringResource('onboarding_syllabus_class', classNumber) : stringResource('onboarding_syllabus_other'),
            style: 'labelLarge', color: C.textSecondary, css: { padding: 'var(--space-sm) var(--space-lg)' },
          }));
          chapters.forEach((chapter) => inner.push(OnboardingSelectableCard({
            title: chapter.name,
            selected: state.selectedChapterIds.has(chapter.id),
            onClick: () => onToggleChapter(chapter.id),
            modifier: { css: { padding: 'var(--space-xs) var(--space-sm)' } },
            showUnselectedIndicator: true,
          })));
        }
        inner.push(Box({ css: { height: 'var(--space-sm)' } }));
      }
      children.push(insetBorder(Surface({
        class: 'fill-w',
        shape: 'var(--radius-md)',
        color: C.elevated,
        children: Column({ class: 'fill-w', css: { alignItems: 'stretch' } }, ...inner),
      }), hairline(C.border)));
    });
  } else {
    children.push(insetBorder(Surface({
      shape: 'var(--radius-md)', color: 'transparent', css: { alignSelf: 'flex-start' },
      children: Text({ text: stringResource('onboarding_syllabus_blank'), css: { padding: 'var(--space-lg)' }, style: 'bodyMedium', color: C.textSecondary }),
    }), hairline(C.border)));
  }
  return Column({ class: 'fill-w', css: { alignItems: 'stretch' }, verticalArrangement: { spacedBy: 'var(--space-md)' } }, ...children);
}

// =============================================================================================
// Study hours step
// =============================================================================================
export function OnboardingHoursParityStep({ state, capacityIssues, onSelectWork, onWeekdayChange, onWeekendChange }) {
  const weeklyHours = state.weekdayHours * 5 + state.weekendHours * 2;
  return Column({ class: 'fill-w', css: { alignItems: 'stretch' }, verticalArrangement: { spacedBy: 'var(--space-xxl)' } },
    Column({ class: 'fill-w', verticalArrangement: { spacedBy: 'var(--space-sm)' } },
      AppearanceLabel('onboarding_hours_start_schedule'),
      Row({ class: 'fill-w', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
        DAY_SHAPES.map((shape) => {
          const selected = shape.id === state.workId;
          return insetBorder(Surface({
            onClick: () => onSelectWork(shape.id),
            selected,
            role: 'radio',
            class: 'weight',
            css: { flex: '1 1 0' },
            shape: 'var(--radius-md)',
            color: 'transparent',
            children: Column({ css: { padding: 'var(--space-md)' }, verticalArrangement: { spacedBy: 'var(--space-xs)' } },
              Text({ text: stringResource(shape.labelRes), style: 'labelLarge' }),
              Text({ text: stringResource('onboarding_shape_weekday_hours', planHour(shape.weekdayHours)), style: 'bodySmall' }),
              Text({ text: stringResource('onboarding_shape_weekend_hours', planHour(shape.weekendHours)), style: 'bodySmall' })),
          }), selectionBorder(selected) ?? hairline(C.outline));
        }))),
    HoursStepper(stringResource('onboarding_hours_weekdays'), state.weekdayHours, 1, 14, onWeekdayChange),
    HoursStepper(stringResource('onboarding_hours_weekends'), state.weekendHours, 1, 16, onWeekendChange),
    Surface({
      class: 'fill-w', shape: 'var(--radius-md)', color: C.elevated,
      children: Row({ class: 'fill-w', css: { padding: 'var(--space-md) var(--space-lg)' }, verticalAlignment: 'CenterVertically' },
        Text({ text: stringResource('onboarding_hours_each_week'), class: 'weight', style: 'titleMedium' }),
        Text({ text: stringResource('onboarding_hours_weekly_value', planHour(weeklyHours)), style: 'titleMedium', color: C.text })),
    }),
    capacityIssues.length ? CapacityIssuesCard(capacityIssues) : null);
}

function HoursStepper(label, value, minimum, maximum, onChange) {
  return AppValueStepper({
    label,
    value: stringResource('onboarding_hours_value', planHour(value)),
    decreaseContentDescription: stringResource('onboarding_hours_decrease', label),
    increaseContentDescription: stringResource('onboarding_hours_increase', label),
    onDecrease: () => onChange(coerceAtLeast(value - 0.5, minimum)),
    onIncrease: () => onChange(coerceAtMost(value + 0.5, maximum)),
    decreaseEnabled: value > minimum,
    increaseEnabled: value < maximum,
  });
}

// =============================================================================================
// Plan step
// =============================================================================================
export function OnboardingPlanStep({ state, onWeekdayChange, onWeekendChange, onDate, onExamDate, onApplyFitOption }) {
  const editing = rememberLocal('Plan/editing', false);
  const shortDatePattern = stringResource('onboarding_plan_short_date_pattern');
  const targetLabel = formatDate(state.targetDate, shortDatePattern);
  const todayLabel = formatDate(localToday(), shortDatePattern);
  const fit = state.fit;
  const children = [];

  if (!state.useProvidedSyllabus) children.push(PlanVerdict(stringResource('onboarding_plan_verdict_empty')));
  else if (state.planMode === PlanMode.STEADY) {
    children.push(PlanVerdict(stringResource('onboarding_plan_steady')));
    if (state.projectionLoading) children.push(CircularProgressIndicator());
    else if (state.projectedFinish != null) {
      children.push(PlanStatRow(stringResource('onboarding_plan_projected_finish'),
        stringResource('onboarding_plan_projected_value', formatDate(state.projectedFinish, shortDatePattern))));
    } else children.push(Text({ text: stringResource('onboarding_plan_projected_none'), style: 'bodyMedium', color: C.textSecondary }));
  } else if (fit == null) children.push(CircularProgressIndicator());
  else {
    const estimateLabel = stringResource(fit.calibrated ? 'onboarding_plan_at_your_pace' : 'onboarding_plan_estimate_label');
    children.push(PlanVerdict(fit.fits
      ? stringResource('onboarding_plan_fits', targetLabel)
      : stringResource('onboarding_plan_short', planHour(fit.shortByMinutes / 60), estimateLabel, targetLabel)));
    children.push(Column({ class: 'fill-w', css: { alignItems: 'stretch' }, verticalArrangement: { spacedBy: 'var(--space-sm)' } },
      PlanStatRow(stringResource('onboarding_plan_stat_learn'), stringResource('onboarding_plan_hours_value', planHour(fit.learnMinutes / 60))),
      PlanStatRow(stringResource('onboarding_plan_stat_practice'), stringResource('onboarding_plan_hours_value', planHour(fit.practiceMinutes / 60))),
      PlanStatRow(stringResource('onboarding_plan_stat_revision'), fit.revisionWindowDays > 0
        ? pluralStringResource('onboarding_plan_revision_days', fit.revisionWindowDays, fit.revisionWindowDays)
        : stringResource('onboarding_plan_revision_none'))));
    if (!fit.fits) children.push(FitOptions(fit, targetLabel, shortDatePattern, state.fitLoading, onApplyFitOption));
  }

  const weekdaysLabel = stringResource('onboarding_hours_weekdays');
  const weekendsLabel = stringResource('onboarding_hours_weekends');
  children.push(Column({ class: 'fill-w', css: { alignItems: 'stretch' }, verticalArrangement: { spacedBy: 'var(--space-sm)' } },
    PlanSummaryRow('Filled.CalendarToday', stringResource('onboarding_plan_every_weekday'), stringResource('onboarding_plan_hour_value', planHour(state.weekdayHours)), editing.value,
      () => AppStepperControl({
        value: stringResource('onboarding_plan_hour_value', planHour(state.weekdayHours)),
        decreaseContentDescription: stringResource('onboarding_hours_decrease', weekdaysLabel),
        increaseContentDescription: stringResource('onboarding_hours_increase', weekdaysLabel),
        onDecrease: () => onWeekdayChange(coerceAtLeast(state.weekdayHours - 0.5, 1)),
        onIncrease: () => onWeekdayChange(coerceAtMost(state.weekdayHours + 0.5, 14)),
        decreaseEnabled: state.weekdayHours > 1,
        increaseEnabled: state.weekdayHours < 14,
      })),
    PlanSummaryRow('Filled.Weekend', stringResource('onboarding_plan_every_weekend'), stringResource('onboarding_plan_hour_value', planHour(state.weekendHours)), editing.value,
      () => AppStepperControl({
        value: stringResource('onboarding_plan_hour_value', planHour(state.weekendHours)),
        decreaseContentDescription: stringResource('onboarding_hours_decrease', weekendsLabel),
        increaseContentDescription: stringResource('onboarding_hours_increase', weekendsLabel),
        onDecrease: () => onWeekendChange(coerceAtLeast(state.weekendHours - 0.5, 1)),
        onIncrease: () => onWeekendChange(coerceAtMost(state.weekendHours + 0.5, 16)),
        decreaseEnabled: state.weekendHours > 1,
        increaseEnabled: state.weekendHours < 16,
      })),
    PlanSummaryRow('Filled.Flag', stringResource('onboarding_plan_starts_ends', todayLabel), targetLabel, editing.value,
      () => OnboardingTargetDateStep('Plan', state.targetDate, state.examDate, state.examDateOfficial, state.examDateIsConfigured, onDate, onExamDate))));
  children.push(OutlinedButton({
    onClick: () => { editing.value = !editing.value; },
    css: { width: '100%', height: 'var(--size-ctaHeight)' },
    shape: CircleShape,
    children: [
      Icon({ imageVector: editing.value ? 'Filled.Check' : 'Filled.Edit' }),
      Text({ text: stringResource(editing.value ? 'onboarding_plan_done_editing' : 'onboarding_plan_edit'), css: { paddingLeft: 'var(--space-sm)' } }),
    ],
  }));
  // §11.3: the usage-data disclosure, on the last step before the plan is made.
  children.push(Text({ text: stringResource('onboarding_usage_data_note'), style: 'bodySmall', color: C.textMuted }));
  return Column({ class: 'fill-w', css: { alignItems: 'stretch' }, verticalArrangement: { spacedBy: 'var(--space-xxl)' } }, ...children);
}

function PlanVerdict(text) {
  return Text({ text, style: 'titleMedium', color: C.textSecondary });
}

function PlanStatRow(label, value) {
  return Row({ class: 'fill-w', verticalAlignment: 'CenterVertically' },
    Text({ text: label, class: 'weight', style: 'bodyMedium', color: C.textSecondary }),
    Text({ text: value, style: 'titleSmall', color: C.text }));
}

/** The four ways out of a plan that is short. Each button applies a choice the planner has already verified. */
function FitOptions(fit, targetLabel, datePattern, loading, onApply) {
  return Column({ class: 'fill-w', css: { alignItems: 'stretch' }, verticalArrangement: { spacedBy: 'var(--space-sm)' } },
    Text({ text: stringResource('onboarding_plan_options_title'), style: 'titleSmall' }),
    fit.options.map((option) => {
      let title, detail, action;
      switch (option.type) {
        case 'AddMinutesPerDay':
          title = stringResource('onboarding_plan_option_add', option.minutes);
          detail = stringResource('onboarding_plan_option_add_detail', targetLabel);
          action = 'onboarding_plan_option_apply'; break;
        case 'ParkChapters':
          title = pluralStringResource('onboarding_plan_option_park', option.count, option.count);
          detail = stringResource('onboarding_plan_option_park_detail', planHour(option.minutesFreed / 60));
          action = 'onboarding_plan_option_apply'; break;
        case 'MoveFinishBy':
          title = stringResource('onboarding_plan_option_move', formatDate(option.date, datePattern));
          detail = stringResource('onboarding_plan_option_move_detail');
          action = 'onboarding_plan_option_apply'; break;
        default:
          title = stringResource('onboarding_plan_option_accept', option.percent);
          detail = stringResource('onboarding_plan_option_accept_detail');
          action = 'onboarding_plan_option_continue';
      }
      return Surface({
        class: 'fill-w', shape: 'var(--radius-md)', color: C.elevated,
        children: Row({ class: 'fill-w', css: { padding: 'var(--space-md) var(--space-lg)' }, verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-md)' } },
          Column({ class: 'weight' },
            Text({ text: title, style: 'titleSmall', color: C.text }),
            Text({ text: detail, style: 'bodySmall', color: C.textSecondary })),
          TextButton({ onClick: () => onApply(option), enabled: !loading, children: Text({ text: stringResource(action) }) })),
      });
    }));
}

function PlanSummaryRow(icon, label, value, editing, editor) {
  return Surface({
    class: 'fill-w', shape: 'var(--radius-md)', color: C.elevated,
    children: Column({ class: 'fill-w', css: { alignItems: 'stretch' } },
      Row({ class: 'fill-w', css: { height: 'var(--size-ctaHeight)', padding: '0 var(--space-lg)' }, verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-lg)' } },
        Icon({ imageVector: icon, tint: C.textSecondary }),
        Text({ text: label, class: 'weight', style: 'bodyLarge' }),
        Text({ text: value, style: 'titleMedium', color: C.brand })),
      editing
        ? Row({ class: 'fill-w', css: { padding: 'var(--space-md) var(--space-lg)' }, horizontalArrangement: 'End' }, ...slot(editor))
        : null),
  });
}

// =============================================================================================
// Helpers
// =============================================================================================
/** NumberFormat.getNumberInstance() with maximumFractionDigits = 1. */
export function planHour(value) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value);
}

const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
/** DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH) for the patterns used here: d, MMM, yyyy. */
export function formatDate(date, pattern) {
  return pattern.replace(/yyyy|MMM|d/g, (t) => (t === 'yyyy' ? String(year(date)) : t === 'MMM' ? MONTHS_SHORT[monthValue(date) - 1] : String(dayOfMonth(date))));
}

const dayOptions = [
  [1, 'onboarding_day_monday'], [2, 'onboarding_day_tuesday'], [3, 'onboarding_day_wednesday'], [4, 'onboarding_day_thursday'],
  [5, 'onboarding_day_friday'], [6, 'onboarding_day_saturday'], [0, 'onboarding_day_sunday'],
];

const dayNames = [
  'onboarding_day_sunday_full', 'onboarding_day_monday_full', 'onboarding_day_tuesday_full', 'onboarding_day_wednesday_full',
  'onboarding_day_thursday_full', 'onboarding_day_friday_full', 'onboarding_day_saturday_full',
];

function accentLabel(accent) {
  switch (accent) {
    case AccentPalette.Blue: return 'onboarding_accent_blue';
    case AccentPalette.Purple: return 'onboarding_accent_purple';
    case AccentPalette.Green: return 'onboarding_accent_green';
    case AccentPalette.Amber: return 'onboarding_accent_amber';
    default: return 'onboarding_accent_rose';
  }
}

function backgroundLabel(background) {
  switch (background) {
    case BackgroundAppearance.System: return 'onboarding_background_system';
    case BackgroundAppearance.Light: return 'onboarding_background_light';
    case BackgroundAppearance.Dark: return 'onboarding_background_dark';
    case BackgroundAppearance.Grey: return 'onboarding_background_grey';
    default: return 'onboarding_background_slate';
  }
}

function commitmentKindLabel(kind) {
  switch (kind) {
    case 'school': return 'onboarding_commitment_school';
    case 'coaching': return 'onboarding_commitment_coaching';
    case 'lecture': return 'onboarding_commitment_lecture';
    case 'tuition': return 'onboarding_commitment_tuition';
    case 'work': return 'onboarding_commitment_job';
    case 'meal': return 'onboarding_commitment_meal';
    case 'commute': return 'onboarding_commitment_commute';
    default: return 'onboarding_commitment_other';
  }
}

/** 12-hour clock, lowercase am/pm: "4:00 pm". */
function clockLabel(minuteOfDay) {
  const hour = Math.trunc(minuteOfDay / 60) % 24;
  const minute = minuteOfDay % 60;
  const hour12 = hour % 12 === 0 ? 12 : hour % 12;
  return stringResource('onboarding_time_value', hour12, minute, stringResource(hour < 12 ? 'onboarding_time_am' : 'onboarding_time_pm'));
}
