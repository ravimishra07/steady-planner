// nav.js — Navigation-Compose NavHost/NavController + the small composition runtime screens use.
//
// ── NavController ────────────────────────────────────────────────────────────────────────────────
//   const nav = createNavController({ graph, startDestination })
//     graph: [{ route: 'day/{epochDay}', arguments: { epochDay: { type: 'long'|'string'|'int'|'bool', nullable?, defaultValue? } } }]
//            routes use the Kotlin pattern syntax: path args `a/{x}`, query args `b?x={x}&y={y}`.
//   nav.navigate(path, { popUpTo?: routePattern|0, inclusive?, saveState?, launchSingleTop?, restoreState? })
//       path is concrete ('day/20371', 'log-session?epochDay=20371', 'mock-form'); popUpTo 0 = whole graph.
//       Semantics follow androidx.navigation: popUpTo pops above (inclusive: including) the newest entry of that
//       route, saveState stores the popped entries (with their ViewModels/saved state) keyed by each popped route;
//       restoreState brings such a saved stack back when navigating to one of those routes; launchSingleTop
//       reuses the top entry when it has the same route.
//   nav.popBackStack(route?, inclusive = false) → boolean    (no arg: pop the top entry; false when only one left)
//   nav.backStack          StateFlow<NavBackStackEntry[]>
//   nav.currentBackStackEntry  StateFlow<NavBackStackEntry|null>
//   nav.currentRoute()     route pattern of the top entry
//   NavBackStackEntry: { id, route (pattern), path (concrete), arguments: {name: typedValue}, savedState: Map,
//                        viewModelStore: Map }
//
// ── NavHost ─────────────────────────────────────────────────────────────────────────────────────
//   NavHost({ navController, destinations: { [routePattern]: (entry) => Node | Promise<Node> }, class?, style? }) → Element
//     Renders only the top entry (EnterTransition.None / ExitTransition.None). Leaving a destination disposes its
//     composition (onDispose callbacks run) but keeps its entry state; returning restores scroll positions.
//     Popped (not saved) entries clear their ViewModels (vm.onCleared?.()).
//
// ── Composition runtime (call while a destination or an observe() render is being built) ─────────
//   onDispose(fn)                      run fn when the current composition/render pass is torn down
//   observe(flow, render, opts?) → Element   host that re-renders render(value) on every emission; keeps scroll
//                                      positions and the focused field (+selection) across re-renders
//                                      opts: { tag='div', class, style }
//   remember(key, init) → MutableStateFlow     lives while the destination is on screen (whole-destination memo)
//   rememberScoped(key, init, deps?) → MutableStateFlow   Compose `remember(deps) { mutableStateOf(init) }`:
//                                      lives while the composable that calls it stays in the composition, i.e.
//                                      while every render pass of its observe() host (or of an ancestor host that
//                                      re-creates it) calls it again. A pass that no longer calls it forgets it, so
//                                      a dialog / branch that leaves and comes back starts fresh. `deps` (array,
//                                      compared with Object.is) re-initialises the state when they change.
//                                      Writing `.value` re-renders the observe() host that read it (recomposition);
//                                      in a destination builder outside any observe() the state simply lives until
//                                      the destination leaves (observe it yourself there).
//                                      Keys are per destination: use a distinct key per call site / list item.
//   rememberSaveable(key, init) → MutableStateFlow  lives while the entry is on the back stack (rememberSaveable)
//   viewModel(key, factory) → instance        one per back stack entry (ViewModelStoreOwner); onCleared() on pop
//   BackHandler(enabled, onBack) → { setEnabled(b), remove() }  LIFO; auto-removed on dispose
//   currentEntry()                         the NavBackStackEntry being composed
//   currentNavController()
//   LaunchedEffect(keys[], fn)             fn() once per composition (re-run when keys change across observe renders);
//                                      may return a cleanup fn
//
// ── Writing a feature route (what app/shell.js calls) ─────────────────────────────────────────────
//   export function HomeRoute({ examPackRepository, …, onOpenDay, modifier }) {
//     const vm = viewModel('HomeViewModel', () => new HomeViewModel(examPackRepository, …));
//     BackHandler(true, () => …)                  // optional
//     return observe(vm.uiState, (state) => HomeScreen({ state, onOpenDay, modifier, … }));
//   }
//   Local UI state (Compose `remember { mutableStateOf() }`): inside an observe() render,
//   const open = rememberScoped('Card.open:' + id, false); read open.value, write open.value = true → that host
//   re-renders. Destination-wide state that must survive branches leaving: remember('open', false) + observe it.
//   Give scroll containers data-scroll-key and text fields a `key` so they survive re-renders.
//
// ── System back ────────────────────────────────────────────────────────────────────────────────
//   systemBack()   browser Back button and Escape both call this: newest enabled BackHandler, else popBackStack().
//   installSystemBack(nav)  hook browser history + Escape (shell calls once).
import { MutableStateFlow } from './flow.js';

// ---------------------------------------------------------------------------------------------
// Route patterns
function compilePattern(route) {
  const [pathPart, queryPart] = route.split('?');
  const segs = pathPart.split('/').filter(Boolean);
  const query = {};
  if (queryPart) for (const kv of queryPart.split('&')) {
    const [k, v] = kv.split('=');
    const m = /^\{(\w+)\}$/.exec(v || '');
    query[k] = m ? m[1] : null;
  }
  return { route, segs, query };
}

function coerce(raw, spec) {
  if (raw === undefined || raw === null) {
    if (spec && 'defaultValue' in spec) return spec.defaultValue;
    return null;
  }
  const t = spec?.type || 'string';
  const s = decodeURIComponent(raw);
  if (spec?.nullable && s === 'null') return null;
  if (t === 'long' || t === 'int') {
    // Long.MIN_VALUE does not fit a JS number exactly; keep the sentinel string-comparable via Number.
    const n = Number(s); return Number.isNaN(n) ? (spec?.defaultValue ?? null) : n;
  }
  if (t === 'float') return parseFloat(s);
  if (t === 'bool') return s === 'true';
  return s;
}

function matchRoute(compiled, args, path) {
  const [p, q] = path.split('?');
  const segs = p.split('/').filter(Boolean);
  if (segs.length !== compiled.segs.length) return null;
  const out = {};
  for (let i = 0; i < segs.length; i++) {
    const pat = compiled.segs[i];
    const m = /^\{(\w+)\}$/.exec(pat);
    if (m) out[m[1]] = segs[i];
    else if (pat !== segs[i]) return null;
  }
  const qs = new URLSearchParams(q || '');
  for (const k in compiled.query) {
    const name = compiled.query[k];
    if (name && qs.has(k)) out[name] = qs.get(k);
  }
  const typed = {};
  for (const name of new Set([...Object.keys(args || {}), ...Object.keys(out)])) typed[name] = coerce(out[name], args?.[name]);
  return typed;
}

let _entryId = 0;
function makeEntry(dest, path, args) {
  return {
    id: ++_entryId,
    route: dest.route,
    path,
    arguments: args,
    savedState: new Map(),      // rememberSaveable + scroll positions
    viewModelStore: new Map(),
  };
}
function clearEntry(entry) {
  for (const vm of entry.viewModelStore.values()) { try { vm?.onCleared?.(); } catch (e) { console.error(e); } }
  entry.viewModelStore.clear();
  entry.savedState.clear();
}

export function createNavController({ graph, startDestination }) {
  const dests = graph.map((d) => ({ ...d, compiled: compilePattern(d.route) }));
  const findDest = (path) => {
    for (const d of dests) {
      const args = matchRoute(d.compiled, d.arguments, path);
      if (args) return { dest: d, args };
    }
    return null;
  };
  const backStack = new MutableStateFlow([]);
  const current = new MutableStateFlow(null);
  const savedStacks = new Map();   // stackId -> entries[]
  const savedStackOf = new Map();  // route -> stackId
  let stackSeq = 0;

  const commit = (stack) => {
    backStack.value = stack;
    current.value = stack[stack.length - 1] || null;
  };

  const start = findDest(startDestination);
  if (!start) throw new Error('nav: unknown start destination ' + startDestination);
  commit([makeEntry(start.dest, startDestination, start.args)]);

  function popInternal(stack, route, inclusive, saveState) {
    // route === 0 → graph root: everything goes.
    let idx;
    if (route === 0) idx = inclusive ? 0 : 0;
    else {
      idx = -1;
      for (let i = stack.length - 1; i >= 0; i--) if (stack[i].route === route) { idx = i; break; }
      if (idx < 0) return { stack, popped: [] , found: false };
      if (!inclusive) idx += 1;
    }
    const popped = stack.slice(idx);
    const kept = stack.slice(0, idx);
    if (saveState && popped.length) {
      const id = ++stackSeq;
      savedStacks.set(id, popped);
      for (const e of popped) savedStackOf.set(e.route, id);
    } else popped.forEach(clearEntry);
    return { stack: kept, popped, found: true };
  }

  const nav = {
    backStack: backStack.asStateFlow(),
    currentBackStackEntry: current.asStateFlow(),
    currentRoute: () => current.value?.route ?? null,
    graph: dests,

    navigate(path, opts = {}) {
      const found = findDest(path);
      if (!found) { console.error('nav: no destination for', path); return; }
      let stack = backStack.value;
      if (opts.popUpTo !== undefined && opts.popUpTo !== null) {
        stack = popInternal(stack, opts.popUpTo, !!opts.inclusive, !!opts.saveState).stack;
      }
      if (opts.restoreState && savedStackOf.has(found.dest.route)) {
        const id = savedStackOf.get(found.dest.route);
        const restored = savedStacks.get(id) || [];
        savedStacks.delete(id);
        for (const [r, sid] of [...savedStackOf]) if (sid === id) savedStackOf.delete(r);
        if (restored.length) {
          if (opts.launchSingleTop && stack.length && stack[stack.length - 1].route === restored[0].route) {
            clearEntry(stack[stack.length - 1]); stack = stack.slice(0, -1);
          }
          commit([...stack, ...restored]);
          return;
        }
      }
      const top = stack[stack.length - 1];
      if (opts.launchSingleTop && top && top.route === found.dest.route) {
        top.path = path; top.arguments = found.args;
        commit([...stack]);
        if (current.value === top) current.value = top; // same entry: no re-composition
        return;
      }
      commit([...stack, makeEntry(found.dest, path, found.args)]);
    },

    popBackStack(route, inclusive = false) {
      const stack = backStack.value;
      if (route === undefined) {
        if (stack.length <= 1) return false;
        clearEntry(stack[stack.length - 1]);
        commit(stack.slice(0, -1));
        return true;
      }
      const r = popInternal(stack, route, inclusive, false);
      if (!r.found || !r.popped.length) return false;
      commit(r.stack);
      return true;
    },

    /** Debug: current stack as paths. */
    dump() { return backStack.value.map((e) => e.path); },
  };
  return nav;
}

// ---------------------------------------------------------------------------------------------
// Composition scopes
let _scope = null;          // active scope while building
let _nav = null;
class Scope {
  constructor(entry, parent = null) {
    this.entry = entry; this.parent = parent; this.disposers = []; this.memo = parent ? parent.memo : new Map();
    this.host = null; // observe() host record of this render pass ({ id, invalidate, drawing }), null = builder
    this.effects = parent ? parent.effects : new Map(); this.overlayIndex = 0; this.disposed = false;
    this.overlaySlots = parent ? null : new Map(); // observe() passes share their host's map
  }
  add(fn) { if (this.disposed) { try { fn(); } catch (e) { console.error(e); } } else this.disposers.push(fn); }
  dispose() {
    if (this.disposed) return; this.disposed = true;
    for (const fn of this.disposers.splice(0).reverse()) { try { fn(); } catch (e) { console.error(e); } }
    if (!this.parent && !this.keepEffects) for (const rec of this.effects.values()) { try { rec.cleanup?.(); } catch (e) { console.error(e); } }
  }
}
function withScope(scope, fn) {
  const prev = _scope; _scope = scope;
  try { return fn(); } finally { _scope = prev; }
}

export function onDispose(fn) {
  if (_scope) _scope.add(fn);
  else console.warn('onDispose outside a composition');
}
export function currentEntry() { return _scope?.entry ?? null; }
export function currentNavController() { return _nav; }
/** Internal for components.js: render-pass scope (overlays use its index to avoid replaying enter animations). */
export function currentScope() { return _scope; }

export function remember(key, init) {
  const memo = _scope?.memo;
  if (!memo) return new MutableStateFlow(typeof init === 'function' ? init() : init);
  if (!memo.has(key)) memo.set(key, new MutableStateFlow(typeof init === 'function' ? init() : init));
  return memo.get(key);
}
export function rememberSaveable(key, init) {
  const st = _scope?.entry?.savedState;
  if (!st) return remember(key, init);
  const k = 'saveable:' + key;
  if (!st.has(k)) st.set(k, new MutableStateFlow(typeof init === 'function' ? init() : init));
  return st.get(k);
}
/**
 * rememberScoped(key, init, deps?) — composable-scoped `remember { mutableStateOf() }` (see header).
 * Bookkeeping: entries live in the destination memo. Every observe() host has a positional path (parent host path +
 * index of the observe() call in the parent pass) and each draw gets a global sequence number. An entry records the
 * host path that last read it and the sequence of that read. When a host finishes a pass, entries owned by its path
 * or a nested path that were not read since the pass started are dropped: their composable left the composition.
 */
let _passSeq = 0;
const SCOPED = Symbol('rememberScoped');
export function rememberScoped(key, init, deps) {
  const memo = _scope?.memo;
  const make = () => new MutableStateFlow(typeof init === 'function' ? init() : init);
  if (!memo) return make();
  if (!memo.has(SCOPED)) memo.set(SCOPED, new Map());
  const table = memo.get(SCOPED);
  const host = currentHost();
  let e = table.get(key);
  if (e && deps && !(e.deps && e.deps.length === deps.length && e.deps.every((d, i) => Object.is(d, deps[i])))) {
    e.unsub(); table.delete(key); e = null;
  }
  if (!e) {
    const flow = make();
    const rec = { flow, deps: deps ? [...deps] : null, host, seq: 0, unsub: null };
    let first = true;
    rec.unsub = flow.subscribe(() => {
      if (first) { first = false; return; }
      const h = rec.host;
      if (h && !h.disposed && !h.drawing) h.invalidate();
    });
    table.set(key, rec);
    e = rec;
  }
  e.host = host;
  e.seq = ++_passSeq;
  return e.flow;
}
function currentHost() {
  for (let s = _scope; s; s = s.parent) if (s.host) return s.host;
  return null;
}
/** After `host` finished the pass that started at sequence `since`: forget state that was not composed in it. */
function sweepScoped(memo, host, since) {
  const table = memo?.get(SCOPED);
  if (!table) return;
  for (const [k, e] of table) {
    if (!e.host || e.seq > since) continue;
    const p = e.host.path;
    if (p === host.path || p.startsWith(host.path + '/')) { e.unsub(); table.delete(k); }
  }
}

export function viewModel(key, factory) {
  const store = _scope?.entry?.viewModelStore;
  if (!store) return factory();
  if (!store.has(key)) store.set(key, factory());
  return store.get(key);
}

/** LaunchedEffect: runs once per key-set for the lifetime of the destination composition. */
export function LaunchedEffect(keys, fn) {
  const effects = _scope?.effects;
  const id = 'effect:' + (_scope?.effectPrefix || '') + (_scope ? _scope.effectSeq = (_scope.effectSeq || 0) + 1 : 0);
  const sig = JSON.stringify(keys ?? []);
  if (!effects) { queueMicrotask(fn); return; }
  const prev = effects.get(id);
  if (prev && prev.sig === sig) return;
  prev?.cleanup?.();
  const rec = { sig, cleanup: null };
  effects.set(id, rec);
  queueMicrotask(() => { const c = fn(); if (typeof c === 'function') rec.cleanup = c; });
}

// ---------------------------------------------------------------------------------------------
// Back handling
const backHandlers = [];
export function BackHandler(enabled, onBack) {
  const h = { enabled: enabled !== false, onBack };
  backHandlers.push(h);
  const remove = () => { const i = backHandlers.indexOf(h); if (i >= 0) backHandlers.splice(i, 1); };
  if (_scope) _scope.add(remove);
  return { setEnabled(b) { h.enabled = !!b; }, remove, set onBack(f) { h.onBack = f; } };
}
export function systemBack() {
  for (let i = backHandlers.length - 1; i >= 0; i--) {
    const h = backHandlers[i];
    if (h.enabled) { h.onBack(); return true; }
  }
  return _nav ? _nav.popBackStack() : false;
}
let _installed = false;
export function installSystemBack(nav) {
  _nav = nav;
  if (_installed || typeof window === 'undefined') return;
  _installed = true;
  // One guard history entry: every browser Back is turned into a system back and the guard re-armed.
  history.replaceState({ sl: 'base' }, '');
  history.pushState({ sl: 'guard' }, '');
  window.addEventListener('popstate', () => {
    systemBack();
    history.pushState({ sl: 'guard' }, '');
  });
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !e.defaultPrevented) { e.preventDefault(); systemBack(); }
  });
}

// ---------------------------------------------------------------------------------------------
// Scroll / focus preservation
function pathFrom(root, el) {
  const path = [];
  for (let n = el; n && n !== root; n = n.parentElement) {
    const p = n.parentElement; if (!p) return null;
    path.unshift(Array.prototype.indexOf.call(p.children, n));
  }
  return path;
}
function resolvePath(root, path) {
  let n = root;
  for (const i of path) { n = n?.children?.[i]; if (!n) return null; }
  return n;
}
function keyOf(el) { return el.getAttribute?.('data-key') || el.getAttribute?.('data-scroll-key') || null; }
export function snapshot(root) {
  const scrolls = [];
  const walk = (el) => {
    for (const c of el.children) {
      if (c.scrollTop || c.scrollLeft) scrolls.push({ key: keyOf(c), path: pathFrom(root, c), top: c.scrollTop, left: c.scrollLeft });
      walk(c);
    }
  };
  walk(root);
  let focus = null;
  const a = document.activeElement;
  if (a && a !== document.body && root.contains(a)) {
    focus = { key: keyOf(a), path: pathFrom(root, a), start: a.selectionStart, end: a.selectionEnd };
  }
  return { scrolls, focus };
}
export function restore(root, snap) {
  if (!snap) return;
  const find = (rec) => (rec.key ? root.querySelector(`[data-key="${CSS.escape(rec.key)}"],[data-scroll-key="${CSS.escape(rec.key)}"]`) : null) || (rec.path ? resolvePath(root, rec.path) : null);
  for (const s of snap.scrolls) {
    const el = find(s);
    if (el) { el.scrollTop = s.top; el.scrollLeft = s.left; }
  }
  if (snap.focus) {
    const el = find(snap.focus);
    if (el && typeof el.focus === 'function') {
      el.focus({ preventScroll: true });
      if (snap.focus.start != null && typeof el.setSelectionRange === 'function') {
        try { el.setSelectionRange(snap.focus.start, snap.focus.end); } catch {}
      }
    }
  }
}

let _observeSeq = 0;
/**
 * Re-render render(value) into a host whenever flow emits (or a rememberScoped state it read changes). Children are
 * rendered in a child scope per pass; the previous pass is disposed after the new one is built. Emissions are
 * coalesced into one microtask; a render still queued when the host is torn down (its owner pass or destination was
 * disposed) is cancelled, so nothing is drawn — and no overlay mounted — after teardown.
 */
export function observe(flow, render, opts = {}) {
  const host = document.createElement(opts.tag || 'div');
  if (opts.class) host.className = opts.class;
  if (opts.style) for (const k in opts.style) host.style[k] = opts.style[k];
  host.setAttribute('data-render-root', '');
  const owner = _scope;
  const slots = new Map();
  const effectPrefix = 'o' + (++_observeSeq) + ':';
  // Positional identity for rememberScoped: parent host path + index of this observe() call in the owner pass.
  const parentHost = currentHost();
  const index = owner ? (owner.observeIndex = (owner.observeIndex || 0) + 1) - 1 : 0;
  const rec = { path: (parentHost ? parentHost.path : 'root') + '/' + index, disposed: false, drawing: false, invalidate: null };
  let pass = null;
  let first = true;
  const draw = (value) => {
    if (rec.disposed) return;
    const snap = first ? null : snapshot(host);
    const prevPass = pass;
    pass = new Scope(owner?.entry ?? null, owner);
    pass.overlaySlots = slots;
    pass.effectPrefix = effectPrefix;
    pass.host = rec;
    const since = _passSeq;
    let node;
    rec.drawing = true;
    try { withScope(pass, () => { node = render(value); }); } finally { rec.drawing = false; }
    if (prevPass) prevPass.dispose();
    sweepScoped(pass.memo, rec, since);
    host.replaceChildren(...(node == null ? [] : Array.isArray(node) ? node.filter(Boolean) : [node]));
    if (!first) restore(host, snap);
    first = false;
  };
  let scheduled = false, latest;
  const schedule = () => {
    // Coalesce bursts of emissions into one frame, like recomposition.
    if (scheduled || rec.disposed) return;
    scheduled = true;
    queueMicrotask(() => { scheduled = false; if (!rec.disposed) draw(latest); });
  };
  rec.invalidate = schedule;
  const unsub = flow.subscribe((v) => {
    latest = v;
    if (first) { draw(v); return; }
    schedule();
  });
  const teardown = () => { rec.disposed = true; scheduled = false; unsub(); pass?.dispose(); };
  if (owner) owner.add(teardown);
  return host;
}

// ---------------------------------------------------------------------------------------------
// NavHost
export function NavHost({ navController, destinations, class: cls, style }) {
  const host = document.createElement('div');
  host.className = 'nav-host' + (cls ? ' ' + cls : '');
  if (style) Object.assign(host.style, style);
  _nav = navController;
  let shown = null;   // { entry, scope, node }
  const scrollMemo = new WeakMap(); // entry -> snapshot

  const show = (entry) => {
    if (shown && shown.entry === entry) return;
    if (shown) {
      scrollMemo.set(shown.entry, snapshot(host));
      shown.scope.dispose();
      host.replaceChildren();
    }
    if (!entry) { shown = null; return; }
    const builder = destinations[entry.route];
    const scope = new Scope(entry);
    shown = { entry, scope };
    const place = (node) => {
      if (shown?.scope !== scope || scope.disposed) return;
      host.replaceChildren(...(node ? [node] : []));
      const snap = scrollMemo.get(entry);
      if (snap) { restore(host, { scrolls: snap.scrolls, focus: null }); requestAnimationFrame(() => restore(host, { scrolls: snap.scrolls, focus: null })); }
      else host.scrollTop = 0;
    };
    let result;
    try {
      result = builder ? withScope(scope, () => builder(entry)) : null;
    } catch (e) {
      console.error(e);
      result = errorNode(entry.route, e);
    }
    if (result && typeof result.then === 'function') {
      result.then((node) => place(node), (e) => { console.error(e); place(errorNode(entry.route, e)); });
    } else place(result);
  };

  /** Re-run the current destination's builder (its props changed, e.g. theme) keeping remember/effects/overlays. */
  host.recompose = () => {
    if (!shown) return;
    const { entry, scope: old } = shown;
    const snap = snapshot(host);
    const scope = new Scope(entry);
    scope.memo = old.memo; scope.effects = old.effects; scope.overlaySlots = old.overlaySlots;
    shown = { entry, scope };
    let node;
    try { node = withScope(scope, () => destinations[entry.route]?.(entry)); } catch (e) { console.error(e); node = errorNode(entry.route, e); }
    const finish = (n) => {
      old.keepEffects = true; old.dispose();
      if (shown?.scope !== scope) return;
      host.replaceChildren(...(n ? [n] : []));
      restore(host, snap);
    };
    if (node && typeof node.then === 'function') node.then(finish, (e) => finish(errorNode(entry.route, e)));
    else finish(node);
  };
  const unsub = navController.currentBackStackEntry.subscribe(show);
  // Popped entries forget their scroll memo with the entry object (WeakMap).
  if (_scope) _scope.add(() => { unsub(); shown?.scope.dispose(); });
  return host;
}

function errorNode(route, e) {
  const d = document.createElement('pre');
  d.style.cssText = 'padding:16px;white-space:pre-wrap;color:var(--c-error);font:12px/16px monospace';
  d.textContent = `Route ${route} failed:\n${e?.stack || e}`;
  return d;
}

/** Capture the active scope so async code (e.g. after a dynamic import) can keep composing into it. */
export function captureScope() {
  const s = _scope;
  return (fn) => withScope(s, fn);
}
