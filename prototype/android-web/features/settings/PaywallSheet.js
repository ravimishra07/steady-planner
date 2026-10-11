// Port of feature/settings/PaywallSheet.kt. Rendered by app/paywall.js (PaywallHost).
import { dataClass } from '../../domain/kt.js';
import { ProStatus } from '../../data/ProRepository.js';
import {
  ModalBottomSheet, rememberModalBottomSheetState, Column, Row, Text, Icon, Button, TextButton, ButtonDefaults,
} from '../../design/components.js';
import { stringResource } from './_local.js';

/** Everything the paywall sheet shows. Built by `:app`'s paywall host. */
export const PaywallUiState = dataClass({
  /** Play's localized price; null falls back to the "₹59 one-time" text. */
  price: null,
  isPro: false,
  status: ProStatus.IDLE,
});

/** The Pro paywall (PRODUCTION_V1_PLAN §11.2): what Pro adds, the one-time price, Buy and Restore. */
export function PaywallSheet({ state, onBuy, onRestore, onDismiss }) {
  const message = statusMessage(state.status);
  const busy = state.status === ProStatus.BUSY;
  return ModalBottomSheet({
    onDismissRequest: onDismiss,
    sheetState: rememberModalBottomSheetState({ skipPartiallyExpanded: true }),
    containerColor: 'var(--c-surface)',
    children: Column({
      class: 'fill-w scroll-y',
      css: { padding: '0 var(--space-screen) var(--space-xl)' },
      verticalArrangement: { spacedBy: 'var(--space-sm)' },
      children: [
        Text({
          text: stringResource(state.isPro ? 'paywall_title_owned' : 'paywall_title'),
          style: 'headlineSmall', color: 'var(--c-text)', fontWeight: 'Bold',
        }),
        Text({ text: stringResource('paywall_subtitle'), style: 'bodyMedium', color: 'var(--c-textSecondary)' }),
        ...PAYWALL_FEATURES.map((f) => FeatureLine(stringResource(f))),
        Text({ text: stringResource('paywall_free_note'), style: 'bodySmall', color: 'var(--c-textMuted)', css: { paddingTop: 'var(--space-sm)' } }),
        message != null
          ? Text({ text: message, style: 'bodySmall', color: 'var(--c-textSecondary)', css: { paddingTop: 'var(--space-sm)' } })
          : null,
        ...(!state.isPro
          ? [
            Button({
              onClick: onBuy, enabled: !busy, class: 'fill-w',
              css: { height: 'var(--size-ctaHeight)', paddingTop: 'var(--space-sm)' },
              shape: 'var(--radius-lg)',
              colors: ButtonDefaults.buttonColors({ containerColor: 'var(--c-brandDeep)', contentColor: 'var(--c-onBrand)' }),
              children: Text(busy ? stringResource('paywall_working')
                : stringResource('paywall_buy', state.price ?? stringResource('paywall_price_fallback'))),
            }),
            TextButton({ onClick: onRestore, enabled: !busy, class: 'fill-w', children: Text(stringResource('paywall_restore')) }),
          ]
          : [TextButton({ onClick: onDismiss, class: 'fill-w', children: Text(stringResource('paywall_close')) })]),
      ].filter(Boolean),
    }),
  });
}

function FeatureLine(text) {
  return Row({ verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
    Icon({ imageVector: 'Outlined.CheckCircle', contentDescription: null, tint: 'var(--c-success)', size: 'var(--size-smallIcon)' }),
    Text({ text, style: 'bodyMedium', color: 'var(--c-text)' }));
}

/** internal fun statusMessage(status) — shared with MoreScreen's Pro section. */
export function statusMessage(status) {
  switch (status) {
    case ProStatus.PENDING: return stringResource('pro_status_pending');
    case ProStatus.PURCHASED: return stringResource('pro_status_purchased');
    case ProStatus.RESTORED: return stringResource('pro_status_restored');
    case ProStatus.NOTHING_TO_RESTORE: return stringResource('pro_status_nothing');
    case ProStatus.UNAVAILABLE: return stringResource('pro_status_unavailable');
    case ProStatus.ERROR: return stringResource('pro_status_error');
    default: return null; // IDLE, BUSY
  }
}

const PAYWALL_FEATURES = [
  'paywall_feature_tests',
  'paywall_feature_mistakes',
  'paywall_feature_insights',
  'paywall_feature_widget',
  'paywall_feature_stage',
];
