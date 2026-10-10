// Port of feature/home HomeEmptyContent.kt.
import { C, Text, Column, Button, ButtonDefaults, AppCard } from '../../design/components.js';
import { R } from './_local.js';

/** HomeEmptyContent({ onSetupPlan, css? }) */
export function HomeEmptyContent({ onSetupPlan, css }) {
  return Column({
    class: 'fill',
    css: { height: '100%', padding: 'var(--space-screen)', ...(css || {}) },
    verticalArrangement: 'Center',
    horizontalAlignment: 'CenterHorizontally',
  },
  Text({ text: R.string.home_empty_title, style: 'headlineLarge', color: C.text, class: 'fill-w', css: { paddingBottom: 'var(--space-xxl)' } }),
  AppCard({
    class: 'fill-w',
    children: Column({
      class: 'fill-w',
      css: { padding: 'var(--space-xl)' },
      horizontalAlignment: 'CenterHorizontally',
      verticalArrangement: { spacedBy: 'var(--space-md)' },
    },
    Text({ text: R.string.home_empty_body, style: 'bodyMedium', color: C.textSecondary }),
    Button({
      onClick: onSetupPlan,
      css: { width: '100%', height: 'var(--size-ctaHeight)' },
      shape: 'var(--radius-lg)',
      colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }),
    }, Text({ text: R.string.home_setup_plan, style: 'labelLarge' }))),
  }));
}
