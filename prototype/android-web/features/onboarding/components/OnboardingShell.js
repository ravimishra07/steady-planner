// Port of feature/onboarding/components/OnboardingShell.kt
// statusBarsPadding / navigationBarsPadding = the fake 24dp system bars (--status-bar-height / --gesture-bar-height).
import { h } from '../../../platform/h.js';
import {
  Column, Row, Box, Spacer, Text, Icon, IconButton, TextButton, Button, ButtonDefaults, HorizontalDivider, C, CircleShape, slot,
} from '../../../design/components.js';
import { stringResource } from '../res/values/strings.js';
import { PROGRESS_SEGMENTS } from '../model/OnboardingUiState.js';

/**
 * OnboardingShell({ title, progressIndex, canGoBack, ctaLabel, modifier?, continueEnabled=true, onBack, onContinue,
 *   onSkip?, bottomContent?, content })
 */
export function OnboardingShell(p) {
  const m = p.modifier || {};
  const scroll = Column({
    class: 'weight fill-w scroll-y',
    css: { padding: '0 var(--space-lg)', alignItems: 'stretch' },
  },
  Text({ text: p.title, style: 'headlineMedium', color: C.text, css: { paddingBottom: 'var(--space-xxl)' } }),
  ...slot(p.content),
  Spacer({ height: 'var(--space-xxl)' }));
  scroll.setAttribute('data-scroll-key', 'onboarding-shell');

  const cta = Button({
    onClick: p.onContinue,
    enabled: p.continueEnabled !== false,
    css: { width: '100%', height: 'var(--size-ctaHeight)' },
    shape: CircleShape,
    colors: ButtonDefaults.buttonColors({
      containerColor: C.primary,
      contentColor: C.onPrimary,
      disabledContainerColor: C.surfaceControl,
      disabledContentColor: C.textDisabled,
    }),
    children: Text({ text: p.ctaLabel, style: 'titleMedium' }),
  });

  return Column({
    class: ['fill', m.class],
    css: {
      background: C.bg,
      paddingTop: 'var(--status-bar-height)',
      paddingBottom: 'var(--gesture-bar-height)',
      ...(m.css || {}),
    },
  },
  OnboardingTopBar({ progressIndex: p.progressIndex, canGoBack: p.canGoBack, onBack: p.onBack, onSkip: p.onSkip }),
  scroll,
  HorizontalDivider({ color: C.border }),
  p.bottomContent != null
    ? Box({ class: 'fill-w', css: { padding: 'var(--space-md) var(--space-lg) 0' } }, ...slot(p.bottomContent))
    : null,
  h('div', { class: 'fill-w', style: { padding: 'var(--space-md) var(--space-lg) var(--space-xxl)' } }, cta));
}

function OnboardingTopBar({ progressIndex, canGoBack, onBack, onSkip }) {
  return Row({
    class: 'fill-w',
    css: { height: 'var(--size-topAppBarHeight)', padding: '0 var(--space-xs)', flex: 'none' },
    verticalAlignment: 'CenterVertically',
  },
  canGoBack
    ? IconButton({
      onClick: onBack,
      css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' },
      children: Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: stringResource('onboarding_back'), tint: C.text }),
    })
    : Spacer({ width: 'var(--size-touchTarget)' }),
  progressIndex != null
    ? OnboardingProgressBar({ progressIndex, modifier: { class: 'weight', css: { padding: '0 var(--space-sm)' } } })
    : Spacer({ weight: 1 }),
  onSkip != null
    ? TextButton({
      onClick: onSkip,
      css: { height: 'var(--size-touchTarget)' },
      children: Text({ text: stringResource('onboarding_skip'), style: 'labelLarge', color: C.textSecondary }),
    })
    : Spacer({ width: 'var(--size-touchTarget)' }));
}

function OnboardingProgressBar({ progressIndex, modifier }) {
  return Row({
    class: modifier.class,
    css: { ...modifier.css, height: 'var(--size-progressHeight)', boxSizing: 'content-box' },
    horizontalArrangement: { spacedBy: 'var(--space-xs)' },
  },
  Array.from({ length: PROGRESS_SEGMENTS }, (_, index) => Box({
    class: 'weight',
    css: {
      height: 'var(--size-progressHeight)',
      borderRadius: CircleShape,
      background: index <= progressIndex ? C.brandDeep : C.elevated,
    },
  })));
}
