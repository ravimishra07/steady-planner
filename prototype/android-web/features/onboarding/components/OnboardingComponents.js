// Port of feature/onboarding/components/OnboardingComponents.kt
import { Surface, Row, Column, Text, Icon, BorderStroke, C, slot } from '../../../design/components.js';
import { stringResource } from '../res/values/strings.js';
import { insetBorder } from '../_local.js';

/**
 * OnboardingSelectableCard({ title, selected, onClick, modifier?: {class, css}, enabled=true,
 *   titleStyle='titleMedium', showUnselectedIndicator=false, trailing? })
 */
export function OnboardingSelectableCard(p) {
  const interactive = p.enabled !== false;
  const background = !interactive ? C.surfaceControl : C.elevated;
  const titleColor = !interactive ? C.textDisabled : C.text;
  const selected = !!p.selected;
  const m = p.modifier || {};
  const row = Row({
    class: 'fill-w',
    css: { padding: '0 var(--space-lg)', minHeight: 'var(--size-ctaHeight)' },
    verticalAlignment: 'CenterVertically',
  },
  Column({ class: 'weight' },
    Text({ text: p.title, style: p.titleStyle ?? 'titleMedium', color: titleColor }),
    ...slot(p.trailing)),
  selected || p.showUnselectedIndicator ? OnboardingSelectionIndicator({ selected: selected && interactive, enabled: interactive }) : null);
  const s = Surface({
    onClick: p.onClick,
    enabled: interactive,
    selected,
    role: 'radio',
    class: ['fill-w', m.class],
    css: m.css,
    shape: 'var(--radius-md)',
    color: background,
    children: row,
  });
  return insetBorder(s, selectionBorder(selected && interactive));
}

/** OnboardingSelectionIndicator({ selected, enabled=true }) */
export function OnboardingSelectionIndicator(p) {
  const size = 'var(--size-selectionIndicator)';
  if (p.selected) return Icon({ imageVector: 'Filled.CheckCircle', tint: C.primary, size });
  if (p.enabled !== false) return Icon({ imageVector: 'Outlined.RadioButtonUnchecked', tint: C.textSecondary, size });
  return Icon({ imageVector: 'Outlined.RadioButtonUnchecked', tint: C.textDisabled, size });
}

/** OnboardingHourPills({ weekdayHours, weekendHours, selected, modifier? }) */
export function OnboardingHourPills(p) {
  const pillBackground = C.elevated;
  const pillText = p.selected ? C.text : C.textSecondary;
  const m = p.modifier || {};
  return Row({ class: m.class, css: { paddingTop: 'var(--space-sm)', ...(m.css || {}) }, horizontalArrangement: { spacedBy: 'var(--space-xs)' } },
    HourPill({ text: stringResource('onboarding_shape_weekday_hours', formatHour(p.weekdayHours)), background: pillBackground, textColor: pillText }),
    HourPill({ text: stringResource('onboarding_shape_weekend_hours', formatHour(p.weekendHours)), background: pillBackground, textColor: pillText }));
}

function HourPill({ text, background, textColor }) {
  return Surface({
    color: background,
    shape: 'var(--radius-sm)',
    children: Text({ text, style: 'micro', color: textColor, css: { padding: 'var(--space-xs) var(--space-sm)' } }),
  });
}

/** Float.toString(): "4.0" prints as "4" here because Kotlin checks value == toLong() first. */
const formatHour = (value) => (value === Math.trunc(value) ? String(Math.trunc(value)) : String(value));

/** Selected = accent outline on the normal surface. Never a tinted fill with same-hue text. */
export const selectionBorder = (selected) => (selected ? BorderStroke('var(--stroke-selected)', C.primary) : null);
