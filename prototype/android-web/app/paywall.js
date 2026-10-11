// paywall.js — port of android/app/.../paywall/PaywallHost.kt (PaywallViewModel + PaywallHost).
//
// Stubs: Google Play Billing lives in data/ProRepository.js (local fake). `activity` is not a thing on the web:
// buy() passes null to proRepository.launchPurchase. PaywallSheet/PaywallUiState come from
// features/settings/PaywallSheet.js; until that file exists a placeholder sheet is shown.
import { combine } from '../platform/flow.js';
import { LaunchedEffect, observe } from '../platform/nav.js';
import { AnalyticsEvent } from '../data/common.js';
import { ModalBottomSheet, Column, Text, TextButton, Button } from '../design/components.js';

const STOP_TIMEOUT_MS = 5_000; // SharingStarted.WhileSubscribed(5s): derived flows here are lazy already.

let sheetModule = null;
/** Starts loading the feature's PaywallSheet so the first showing renders synchronously. */
export function preloadPaywallSheet() {
  return import('../features/settings/PaywallSheet.js').then((m) => { sheetModule = m; return m; }).catch(() => null);
}

export class PaywallViewModel {
  constructor(proRepository, analytics) {
    this.proRepository = proRepository;
    this.analytics = analytics;
    const make = (o) => (sheetModule?.PaywallUiState ? sheetModule.PaywallUiState(o) : { price: null, isPro: false, status: 'IDLE', ...o });
    this.state = combine([proRepository.isPro, proRepository.formattedPrice, proRepository.status],
      (isPro, price, status) => make({ price, isPro, status }));
    proRepository.loadProduct();
  }
  buy(activity) { this.proRepository.launchPurchase(activity); }
  restore() { this.proRepository.refreshPurchases(true); }
  onDismissed() { return this.proRepository.clearStatus(); }
  /** Each time the sheet opens; the ViewModel itself outlives one showing. */
  onShown() { return this.analytics.log(AnalyticsEvent.PAYWALL_VIEWED); }
}

let activityViewModel = null; // viewModel() here is Activity-scoped: one instance for the app's lifetime.

/** PaywallHost({ proRepository, analytics, onDismiss, viewModel? }) — the paywall sheet over whatever screen asked for it. */
export function PaywallHost({ proRepository, analytics, onDismiss, viewModel }) {
  const vm = viewModel || (activityViewModel ||= new PaywallViewModel(proRepository, analytics));
  LaunchedEffect([], () => { vm.onShown(); });
  const Sheet = sheetModule?.PaywallSheet || PlaceholderPaywallSheet;
  return observe(vm.state, (state) => Sheet({
    state,
    onBuy: () => vm.buy(null),
    onRestore: () => vm.restore(),
    onDismiss: () => { vm.onDismissed(); onDismiss(); },
  }), { style: { display: 'contents' } });
}

function PlaceholderPaywallSheet({ state, onBuy, onRestore, onDismiss }) {
  return ModalBottomSheet({
    onDismissRequest: onDismiss,
    containerColor: 'var(--c-surface)',
    children: Column({ css: { padding: '0 var(--space-screen) var(--space-xl)' }, verticalArrangement: { spacedBy: 'var(--space-sm)' }, class: 'fill-w' },
      Text({ text: 'PaywallSheet (features/settings/PaywallSheet.js not ported yet)', style: 'titleMedium' }),
      Text({ text: `price=${state.price} isPro=${state.isPro} status=${state.status}`, style: 'bodySmall', color: 'var(--c-textMuted)' }),
      Button({ onClick: onBuy, children: Text('Buy') }),
      TextButton({ onClick: onRestore, children: Text('Restore') }),
    ),
  });
}
