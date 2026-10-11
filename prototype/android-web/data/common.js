// Port of android/core/common/src/main (Analytics.kt, AppDispatchers.kt) plus small JS helpers
// the data layer needs (not Kotlin files: Mutex, randomUUID, runCatching, formatAndroid).
//
// Stubbed:
//  - AppDispatchers: there is one JS thread. `withContext(dispatchers.io) { .. }` becomes a plain
//    call; DefaultAppDispatchers keeps the four fields as string labels for API parity.
//  - Analytics: Firebase does not exist here. Analytics.NoOp has the same API; logs nothing.
//    (`Analytics.Console` is an opt-in debug variant, not in Kotlin.)

// ---------------- Analytics.kt ----------------

/**
 * Product analytics (PRODUCTION_V1_PLAN §11.3). Events carry ids and enums only.
 * interface Analytics { log(event, params = Map/obj); setCollectionEnabled(enabled) }
 */
export const Analytics = Object.freeze({
  NoOp: Object.freeze({
    log(_event, _params = new Map()) {},
    setCollectionEnabled(_enabled) {},
  }),
  /** Debug only (not in Kotlin): prints events to the console. */
  Console: Object.freeze({
    log(event, params = new Map()) {
      const obj = params instanceof Map ? Object.fromEntries(params) : params;
      console.debug('[analytics]', event, obj);
    },
    setCollectionEnabled(enabled) { console.debug('[analytics] collection', enabled); },
  }),
});

/** Every event name, so call sites cannot drift from the list in §11.3. */
export const AnalyticsEvent = Object.freeze({
  ONBOARDING_STARTED: 'onboarding_started',
  ONBOARDING_COMPLETED: 'onboarding_completed',
  PLAN_GENERATED: 'plan_generated',
  BLOCK_STARTED: 'block_started',
  BLOCK_COMPLETED: 'block_completed',
  BLOCK_MISSED: 'block_missed',
  BLOCK_MOVED: 'block_moved',
  BLOCK_DROPPED: 'block_dropped',
  CHAPTER_LEARNED: 'chapter_learned',
  REVISION_COMPLETED: 'revision_completed',
  MOCK_LOGGED: 'mock_logged',
  PAYWALL_VIEWED: 'paywall_viewed',
  PURCHASE_STARTED: 'purchase_started',
  PURCHASE_COMPLETED: 'purchase_completed',
  RESTORE_COMPLETED: 'restore_completed',
  WIDGET_ADDED: 'widget_added',
  EXPORT_DONE: 'export_done',
});

/** Param keys. Values are enums, counts or flags. */
export const AnalyticsParam = Object.freeze({
  MODE: 'mode',
  STARTING_POINT: 'starting_point',
  COUNT: 'count',
  IS_REVISION: 'is_revision',
  ACTIVITY: 'activity',
  PLANNED: 'planned',
  ACTION: 'action',
  SOURCE: 'source',
  MOCK_TYPE: 'mock_type',
  IS_EDIT: 'is_edit',
  FOUND: 'found',
  WIDGET: 'widget',
});

// ---------------- AppDispatchers.kt ----------------

/** Stub: JS has one thread. Fields kept as labels so call sites read the same. */
export class DefaultAppDispatchers {
  constructor() {
    this.main = 'main';
    this.mainImmediate = 'main.immediate';
    this.io = 'io';
    this.default = 'default';
  }
}

/** `withContext(dispatcher) { block }` — runs block (sync or async) and returns its promise. */
export async function withContext(_dispatcher, block) { return block(); }

// ---------------- JS helpers (no Kotlin file) ----------------

/** kotlinx.coroutines.sync.Mutex: FIFO async lock. `await mutex.withLock(async () => ...)`. */
export class Mutex {
  constructor() { this._tail = Promise.resolve(); }
  withLock(fn) {
    const run = this._tail.then(() => fn());
    this._tail = run.then(() => undefined, () => undefined);
    return run;
  }
}

/** java.util.UUID.randomUUID().toString() */
export function randomUUID() {
  try { if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID(); } catch {}
  const b = new Uint8Array(16);
  if (globalThis.crypto?.getRandomValues) globalThis.crypto.getRandomValues(b);
  else for (let i = 0; i < 16; i++) b[i] = Math.floor(Math.random() * 256);
  b[6] = (b[6] & 0x0f) | 0x40; b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/** runCatching { }.getOrNull() / getOrDefault for async blocks. Returns {ok, value, error}. */
export async function runCatching(block) {
  try { return { ok: true, value: await block(), error: null }; }
  catch (error) { return { ok: false, value: null, error }; }
}

/** Kotlin/Java String.format — full java.util.Formatter grammar (%02d, %2$02d, %.1f, %,d, …): platform/format.js. */
export { formatAndroid } from '../platform/format.js';

/** Map-or-object -> Map (analytics params are passed as Map like Kotlin mapOf). */
export const mapOf = (...pairs) => new Map(pairs);

/**
 * Kotlin default + named arguments for repository funs. Call either positionally
 * `replenish(date, 21, now)` or with ONE plain object `replenish({ nowMs })`; a missing/undefined
 * argument takes its default. [defaults] are thunks so `LocalDate.now()` is evaluated per call.
 */
export function namedArgs(args, names, defaults) {
  const a0 = args[0];
  const named = args.length === 1 && a0 != null && typeof a0 === 'object' && Object.getPrototypeOf(a0) === Object.prototype;
  return names.map((n, i) => {
    const v = named ? a0[n] : args[i];
    return v !== undefined ? v : (defaults[i] ? defaults[i]() : undefined);
  });
}
