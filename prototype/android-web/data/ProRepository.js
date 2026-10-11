// Port of core/data/ProRepository.kt.
//
// Stubbed: Google Play Billing. A fake "Play account" lives in prefs('fake_play_billing') key
// `owned` (boolean) — that is what a purchase writes and what restore/refresh reads, so Play
// stays the source of truth exactly as in Kotlin and the Pro cache (prefs('pro'), keys
// is_pro / debug_force_pro / soft_prompt_shown) is only ever written from it.
//  - launchPurchase(): BUSY, then after PURCHASE_SHEET_DELAY_MS the purchases listener fires with
//    OK + PURCHASED (fake sheet; the user always buys). `fakeCancelNextPurchase()` makes the next
//    one report USER_CANCELED instead.
//  - loadProduct(): product details with formattedPrice "₹59" (PRODUCT_ID pro_lifetime).
//  - firstInstallTime: recorded in prefs('package_info') key `first_install_time` on first run.
//  - ensureConnected() always succeeds (no retries needed); Mutex kept for parity.
import { prefs } from '../platform/store.js';
import { MutableStateFlow } from '../platform/flow.js';
import { nowMs as clockNowMs } from '../domain/time.js';
import { Analytics, AnalyticsEvent, AnalyticsParam, Mutex, mapOf } from './common.js';
import { ConfigKeys } from './RemoteConfig.js';
import { enumClass } from '../domain/kt.js';

/** Kotlin: private Context.proDataStore. Deliberately not in BackupRepository. */
const proDataStore = () => prefs('pro');
const fakePlay = () => prefs('fake_play_billing');

/** What the paywall and Settings -> Pro show while billing works. */
export const ProStatus = enumClass([
  'IDLE', 'BUSY', 'PENDING', 'PURCHASED', 'RESTORED', 'NOTHING_TO_RESTORE', 'UNAVAILABLE', 'ERROR',
]);

const isProKey = 'is_pro';
const debugForceKey = 'debug_force_pro';
const softPromptShownKey = 'soft_prompt_shown';

const BillingResponseCode = Object.freeze({ OK: 0, USER_CANCELED: 1, ITEM_ALREADY_OWNED: 7, ERROR: 6 });
const PurchaseState = Object.freeze({ PURCHASED: 1, PENDING: 2 });
const PURCHASE_SHEET_DELAY_MS = 600;

export class ProRepository {
  constructor(context, dispatchers, remoteConfig, scope, isDebugBuild, analytics = Analytics.NoOp) {
    this.remoteConfig = remoteConfig;
    this.isDebugBuild = isDebugBuild;
    this.analytics = analytics;
    this.connectMutex = new Mutex();
    this._status = new MutableStateFlow(ProStatus.IDLE);
    this.status = this._status.asStateFlow();
    this._product = new MutableStateFlow(null);
    /** Play's localized price for Pro, or null until product details load. */
    this.formattedPrice = this._product.map((p) => p?.oneTimePurchaseOfferDetails?.formattedPrice ?? null);
    const ds = proDataStore();
    /** The cached Play entitlement (plus the debug-only override). */
    this.isPro = ds.data
      .map((p) => p[isProKey] === true || (this.isDebugBuild && p[debugForceKey] === true))
      .distinctUntilChanged((a, b) => a === b);
    /** What gates read: Pro, or the paywall switched off remotely. */
    this.unlocked = this.isPro.map((it) => it || !this.paywallEnabled).distinctUntilChanged((a, b) => a === b);
    /** Debug builds only: Settings switch that forces Pro on for testing. */
    this.debugForcePro = ds.data.map((p) => this.isDebugBuild && p[debugForceKey] === true);
    this._cancelNext = false;
    const pkg = prefs('package_info');
    if (pkg.get('first_install_time') == null) pkg.set('first_install_time', clockNowMs());
  }

  /** False only when remote config turns the paywall off; then everything is unlocked. */
  get paywallEnabled() { return this.remoteConfig.bool(ConfigKeys.PAYWALL_ENABLED, true); }

  async unlockedOnce() { return this.unlocked.value; }

  async setDebugForcePro(enabled) {
    if (!this.isDebugBuild) return;
    proDataStore().set(debugForceKey, enabled);
  }

  /** Stub: the fake Play connection is always available. */
  async ensureConnected() { return this.connectMutex.withLock(async () => true); }

  /** Loads Pro's product details for the localized price. Safe to call repeatedly. */
  async loadProduct() {
    if (this._product.value != null) return;
    if (!(await this.ensureConnected())) return;
    this._product.value = { productId: ProRepository.PRODUCT_ID, oneTimePurchaseOfferDetails: { formattedPrice: '₹59' } };
  }

  /**
   * Re-reads what this (fake) Google account owns. App start (userInitiated false) and
   * Settings -> Restore purchase (true). Only a successful query changes the cache.
   */
  async refreshPurchases(userInitiated = false) {
    if (userInitiated) this._status.value = ProStatus.BUSY;
    let ok;
    try {
      if (!(await this.ensureConnected())) ok = false;
      else {
        const owned = this.queryPurchases();
        const hasPro = owned.some((it) => it.purchaseState === PurchaseState.PURCHASED);
        await this.setPro(hasPro);
        if (userInitiated) {
          this._status.value = hasPro ? ProStatus.RESTORED
            : owned.some((it) => it.purchaseState === PurchaseState.PENDING) ? ProStatus.PENDING
              : ProStatus.NOTHING_TO_RESTORE;
          this.analytics.log(AnalyticsEvent.RESTORE_COMPLETED, mapOf([AnalyticsParam.FOUND, hasPro]));
        }
        ok = true;
      }
    } catch { ok = false; }
    if (!ok && userInitiated) this._status.value = ProStatus.UNAVAILABLE;
  }

  /** Opens the (fake) purchase sheet for Pro. The result arrives through the purchases listener. */
  async launchPurchase(_activity) {
    this._status.value = ProStatus.BUSY;
    let launched = false;
    try {
      if (await this.ensureConnected()) {
        await this.loadProduct();
        if (this._product.value != null) {
          this.analytics.log(AnalyticsEvent.PURCHASE_STARTED);
          if (this.queryPurchases().length > 0) {
            await this.refreshPurchases(true); // ITEM_ALREADY_OWNED
          } else {
            const cancel = this._cancelNext; this._cancelNext = false;
            setTimeout(() => {
              if (cancel) { this.onPurchasesUpdated(BillingResponseCode.USER_CANCELED, []); return; }
              fakePlay().set('owned', true);
              this.onPurchasesUpdated(BillingResponseCode.OK, this.queryPurchases());
            }, PURCHASE_SHEET_DELAY_MS);
          }
          launched = true;
        }
      }
    } catch { launched = false; }
    if (!launched) this._status.value = ProStatus.UNAVAILABLE;
  }

  /** Test/demo hook (not in Kotlin): the next fake purchase sheet is dismissed. */
  fakeCancelNextPurchase() { this._cancelNext = true; }

  /** private: what the fake Play account owns. */
  queryPurchases() {
    return fakePlay().get('owned', false)
      ? [{ products: [ProRepository.PRODUCT_ID], purchaseState: PurchaseState.PURCHASED, isAcknowledged: true }]
      : [];
  }

  async onPurchasesUpdated(responseCode, purchases) {
    switch (responseCode) {
      case BillingResponseCode.OK: {
        const pro = purchases.filter((it) => it.products.includes(ProRepository.PRODUCT_ID));
        if (pro.some((it) => it.purchaseState === PurchaseState.PURCHASED)) {
          await this.setPro(true);
          this._status.value = ProStatus.PURCHASED;
          this.analytics.log(AnalyticsEvent.PURCHASE_COMPLETED);
        } else if (pro.some((it) => it.purchaseState === PurchaseState.PENDING)) this._status.value = ProStatus.PENDING;
        else this._status.value = ProStatus.IDLE;
        break;
      }
      case BillingResponseCode.USER_CANCELED: this._status.value = ProStatus.IDLE; break;
      case BillingResponseCode.ITEM_ALREADY_OWNED: await this.refreshPurchases(true); break;
      default: this._status.value = ProStatus.ERROR;
    }
  }

  async setPro(value) { proDataStore().set(isProKey, value); }

  /** The status line was shown; back to idle. */
  clearStatus() { this._status.value = ProStatus.IDLE; }

  /**
   * The one soft prompt (§11.2): a free student with at least MIN_ACTIVE_DAYS study days, at least
   * MIN_DAYS_INSTALLED days after install, shown once ever.
   */
  async shouldShowSoftPrompt(activeStudyDays, nowMs = clockNowMs()) {
    if (!this.paywallEnabled || activeStudyDays < ProRepository.MIN_ACTIVE_DAYS) return false;
    if (await this.unlockedOnce()) return false;
    if (proDataStore().get(softPromptShownKey) === true) return false;
    const installedAt = prefs('package_info').get('first_install_time', nowMs);
    return nowMs - installedAt >= ProRepository.MIN_DAYS_INSTALLED * DAY_MS;
  }

  async markSoftPromptShown() { proDataStore().set(softPromptShownKey, true); }
}
ProRepository.PRODUCT_ID = 'pro_lifetime';
ProRepository.MIN_ACTIVE_DAYS = 5;
ProRepository.MIN_DAYS_INSTALLED = 7;
const DAY_MS = 24 * 60 * 60 * 1000;
