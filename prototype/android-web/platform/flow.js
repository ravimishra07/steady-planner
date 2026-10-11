// flow.js — kotlinx.coroutines StateFlow / MutableStateFlow equivalent.
//
// API
//   new MutableStateFlow(initial)
//   flow.value                 get/set (set notifies only when !Object.is(old,new), like StateFlow equality conflation;
//                              StateFlow uses equals(): pass {equals:(a,b)=>bool} as 2nd ctor arg to customise)
//   flow.subscribe(fn)         fn(value) called immediately + on every change; returns unsubscribe()
//   flow.update(fn)            value = fn(value)  (MutableStateFlow.update {})
//   flow.compareAndSet(e, n)
//   flow.asStateFlow()         read-only view (value getter, subscribe, map, distinctUntilChanged)
//   flow.map(fn)               derived StateFlow (lazy: upstream subscribed only while it has subscribers;
//                              .value always fresh)
//   flow.distinctUntilChanged(eq?)
//   combine(flows[], fn)       derived StateFlow of fn(...values)
//   flowOf(value)              constant StateFlow
//   stateIn(flow)              identity (all flows here are already "hot" StateFlows)

const defaultEq = Object.is;

export class StateFlow {
  constructor() { this._subs = new Set(); }
  get value() { throw new Error('abstract'); }
  subscribe(fn) {
    this._subs.add(fn);
    if (this._subs.size === 1) this._onActive?.();
    try { fn(this.value); } catch (e) { console.error(e); }
    return () => {
      if (this._subs.delete(fn) && this._subs.size === 0) this._onInactive?.();
    };
  }
  _emit(v) {
    for (const fn of [...this._subs]) {
      if (!this._subs.has(fn)) continue;
      try { fn(v); } catch (e) { console.error(e); }
    }
  }
  map(fn) { return new DerivedFlow([this], (v) => fn(v)); }
  distinctUntilChanged(eq = shallowEqual) { return new DerivedFlow([this], (v) => v, eq); }
  asStateFlow() { return new ReadOnlyFlow(this); }
  get subscriptionCount() { return this._subs.size; }
}

export class MutableStateFlow extends StateFlow {
  constructor(initial, opts = {}) {
    super();
    this._value = initial;
    this._eq = opts.equals || defaultEq;
  }
  get value() { return this._value; }
  set value(v) {
    if (this._eq(this._value, v)) return;
    this._value = v;
    this._emit(v);
  }
  update(fn) { this.value = fn(this._value); }
  compareAndSet(expect, next) {
    if (!this._eq(this._value, expect)) return false;
    this.value = next; return true;
  }
  tryEmit(v) { this.value = v; return true; }
  emit(v) { this.value = v; }
}

class ReadOnlyFlow extends StateFlow {
  constructor(src) { super(); this._src = src; }
  get value() { return this._src.value; }
  subscribe(fn) { return this._src.subscribe(fn); }
  asStateFlow() { return this; }
}

class DerivedFlow extends StateFlow {
  constructor(sources, compute, eq = defaultEq) {
    super();
    this._sources = sources;
    this._compute = compute;
    this._eq = eq;
    this._unsubs = null;
    this._cache = undefined;
    this._hasCache = false;
  }
  _calc() { return this._compute(...this._sources.map((s) => s.value)); }
  get value() {
    if (this._unsubs) return this._cache;
    return this._calc();
  }
  _onActive() {
    this._cache = this._calc();
    this._unsubs = [];
    let init = true;
    for (const s of this._sources) {
      this._unsubs.push(s.subscribe(() => {
        if (init) return;
        const next = this._calc();
        if (this._eq(this._cache, next)) return;
        this._cache = next;
        this._emit(next);
      }));
    }
    init = false;
  }
  _onInactive() {
    this._unsubs?.forEach((u) => u());
    this._unsubs = null;
  }
}

export function combine(flows, fn) { return new DerivedFlow(flows, fn); }
export function flowOf(value) { return new MutableStateFlow(value).asStateFlow(); }
export function stateIn(flow) { return flow; }

export function shallowEqual(a, b) {
  if (Object.is(a, b)) return true;
  if (typeof a !== 'object' || typeof b !== 'object' || !a || !b) return false;
  if (Array.isArray(a) !== Array.isArray(b)) return false;
  const ka = Object.keys(a), kb = Object.keys(b);
  if (ka.length !== kb.length) return false;
  for (const k of ka) if (!Object.is(a[k], b[k])) return false;
  return true;
}
