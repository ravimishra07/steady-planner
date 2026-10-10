// Kotlin-semantics helpers shared by domain/ and data/. Not a port of a Kotlin file.
//
// Conventions used by every ported file:
//  - data class  -> dataClass(defaults, proto) factory: Foo({...}) returns a frozen object with
//                   .copy(changes) and .equals(other). Fields are own enumerable props (JSON-safe).
//  - enum class  -> enumClass([...names], props?) : frozen object, entries are singletons
//                   {name, ordinal, ...props}; Foo.entries / Foo.values() keep declaration order;
//                   Foo.valueOf(name) throws like Kotlin; entry.toString()/toJSON() = name.
//  - sealed      -> tagged plain objects with `type`.
//  - Map<K,V>    -> JS Map (insertion order == LinkedHashMap). Set<T> -> JS Set. Pair -> [a, b].
//  - LocalDate   -> 'YYYY-MM-DD' string (see time.js). Long/Int -> number (Int division via idiv).

const LAZY = Symbol('lazyDefault');
/** A data-class default evaluated at construction time (for defaults that reference other modules' enums). */
export const lazyDefault = (f) => ({ [LAZY]: f });

export function dataClass(defaults = {}, proto = {}) {
  const p = Object.create(null);
  Object.defineProperties(p, Object.getOwnPropertyDescriptors(proto));
  const make = (props = {}) => {
    const o = Object.create(p);
    for (const k of Object.keys(defaults)) { const d = defaults[k]; o[k] = d && d[LAZY] ? d[LAZY]() : d; }
    for (const k of Object.keys(props)) if (props[k] !== undefined || !(k in defaults)) o[k] = props[k];
    return Object.freeze(o);
  };
  Object.defineProperty(p, 'copy', { value(changes = {}) { return make({ ...this, ...changes }); } });
  Object.defineProperty(p, 'equals', { value(other) { return deepEquals(this, other); } });
  Object.defineProperty(p, 'toJSON', { value() { return { ...this }; } });
  make.prototype = p;
  return make;
}

export function enumClass(names, propsFn = () => ({}), methods = {}) {
  const e = {};
  const entries = names.map((name, ordinal) => {
    const entry = { name, ordinal, ...propsFn(name, ordinal) };
    for (const [k, d] of Object.entries(Object.getOwnPropertyDescriptors(methods))) Object.defineProperty(entry, k, d);
    Object.defineProperty(entry, 'toString', { value: () => name });
    Object.defineProperty(entry, 'toJSON', { value: () => name });
    Object.defineProperty(entry, 'compareTo', { value: (o) => ordinal - o.ordinal });
    return Object.freeze(entry);
  });
  for (const en of entries) e[en.name] = en;
  Object.defineProperty(e, 'entries', { value: Object.freeze(entries) });
  Object.defineProperty(e, 'values', { value: () => entries.slice() });
  Object.defineProperty(e, 'valueOf', {
    value: (n) => {
      const v = e[typeof n === 'object' && n ? n.name : n];
      if (!v || !entries.includes(v)) throw new Error(`No enum constant ${n}`);
      return v;
    },
  });
  Object.defineProperty(e, 'valueOfOrNull', { value: (n) => (n == null ? null : entries.find((x) => x.name === (n.name ?? n)) ?? null) });
  return Object.freeze(e);
}

export function deepEquals(a, b) {
  if (a === b) return true;
  if (a == null || b == null || typeof a !== 'object' || typeof b !== 'object') return Number.isNaN(a) && Number.isNaN(b);
  if (a instanceof Map) {
    if (!(b instanceof Map) || a.size !== b.size) return false;
    for (const [k, v] of a) if (!b.has(k) || !deepEquals(v, b.get(k))) return false;
    return true;
  }
  if (a instanceof Set) {
    if (!(b instanceof Set) || a.size !== b.size) return false;
    for (const v of a) if (!b.has(v)) return false;
    return true;
  }
  if (Array.isArray(a)) {
    if (!Array.isArray(b) || a.length !== b.length) return false;
    return a.every((x, i) => deepEquals(x, b[i]));
  }
  const ka = Object.keys(a), kb = Object.keys(b);
  if (ka.length !== kb.length) return false;
  return ka.every((k) => deepEquals(a[k], b[k]));
}

// ---- numbers (Kotlin Int/Long/Double semantics) ----
/** Kotlin Int/Long `/`: truncates toward zero. */
export const idiv = (a, b) => {
  if (b === 0) throw new Error('ArithmeticException: / by zero');
  return Math.trunc(a / b);
};
/** Kotlin `%` on ints: sign follows dividend (same as JS). */
export const irem = (a, b) => a % b;
/** Kotlin Double.roundToInt(): Math.round (ties toward +inf). */
export const roundToInt = (x) => {
  if (Number.isNaN(x)) throw new Error('Cannot round NaN value.');
  return Math.round(x);
};
/** Double/Float.toInt(): truncation. */
export const toInt = (x) => (Number.isNaN(x) ? 0 : Math.trunc(x));
export const coerceIn = (v, lo, hi) => {
  if (lo > hi) throw new Error(`Cannot coerce value to an empty range: maximum ${hi} is less than minimum ${lo}.`);
  return v < lo ? lo : v > hi ? hi : v;
};
export const coerceAtLeast = (v, lo) => (v < lo ? lo : v);
export const coerceAtMost = (v, hi) => (v > hi ? hi : v);

// ---- collections ----
export const sumOf = (xs, f = (x) => x) => { let s = 0; for (const x of xs) s += f(x); return s; };
export const maxOfOrNull = (xs, f = (x) => x) => { let m = null; for (const x of xs) { const v = f(x); if (m === null || v > m) m = v; } return m; };
export const minOfOrNull = (xs, f = (x) => x) => { let m = null; for (const x of xs) { const v = f(x); if (m === null || v < m) m = v; } return m; };
export const maxByOrNull = (xs, f) => { let best = null, bv; for (const x of xs) { const v = f(x); if (best === null || cmp(v, bv) > 0) { best = x; bv = v; } } return best; };
export const minByOrNull = (xs, f) => { let best = null, bv; for (const x of xs) { const v = f(x); if (best === null || cmp(v, bv) < 0) { best = x; bv = v; } } return best; };
/** Natural order compare: numbers, strings (UTF-16 like Kotlin), booleans (false<true), enums by ordinal, null first. */
export function cmp(a, b) {
  if (a === b) return 0;
  if (a == null) return -1;
  if (b == null) return 1;
  if (typeof a === 'object' && 'ordinal' in a) return a.ordinal - b.ordinal;
  if (typeof a === 'boolean') return (a ? 1 : 0) - (b ? 1 : 0);
  return a < b ? -1 : a > b ? 1 : 0;
}
/** compareBy(f1, f2, ...) like Kotlin; each selector may return null (nulls first). */
export const compareBy = (...fs) => (x, y) => { for (const f of fs) { const c = cmp(f(x), f(y)); if (c !== 0) return c; } return 0; };
export const compareByDescending = (f) => (x, y) => cmp(f(y), f(x));
export const thenBy = (c1, f) => (x, y) => c1(x, y) || cmp(f(x), f(y));
export const thenByDescending = (c1, f) => (x, y) => c1(x, y) || cmp(f(y), f(x));
/** Stable sortedBy (new array). */
export const sortedBy = (xs, f) => [...xs].sort((a, b) => cmp(f(a), f(b)));
export const sortedByDescending = (xs, f) => [...xs].sort((a, b) => cmp(f(b), f(a)));
export const sortedWith = (xs, c) => [...xs].sort(c);
export const sorted = (xs) => [...xs].sort(cmp);
export const groupBy = (xs, kf, vf = (x) => x) => {
  const m = new Map();
  for (const x of xs) { const k = kf(x); let l = m.get(k); if (!l) m.set(k, (l = [])); l.push(vf(x)); }
  return m;
};
export const associateBy = (xs, kf, vf = (x) => x) => { const m = new Map(); for (const x of xs) m.set(kf(x), vf(x)); return m; };
export const associateWith = (xs, vf) => { const m = new Map(); for (const x of xs) m.set(x, vf(x)); return m; };
export const distinctBy = (xs, f) => { const seen = new Set(); const out = []; for (const x of xs) { const k = f(x); if (!seen.has(k)) { seen.add(k); out.push(x); } } return out; };
export const distinct = (xs) => [...new Set(xs)];
export const mapNotNull = (xs, f) => { const out = []; let i = 0; for (const x of xs) { const v = f(x, i++); if (v != null) out.push(v); } return out; };
export const firstOrNull = (xs, p = () => true) => { for (const x of xs) if (p(x)) return x; return null; };
export const lastOrNull = (xs, p = () => true) => { for (let i = xs.length - 1; i >= 0; i--) if (p(xs[i])) return xs[i]; return null; };
export const count = (xs, p) => { let n = 0; for (const x of xs) if (p(x)) n++; return n; };
export const chunked = (xs, n) => { const out = []; for (let i = 0; i < xs.length; i += n) out.push(xs.slice(i, i + n)); return out; };
export const partition = (xs, p) => { const a = [], b = []; for (const x of xs) (p(x) ? a : b).push(x); return [a, b]; };
export const setOf = (...xs) => new Set(xs);
export const mapGet = (m, k, d = null) => (m && m.has(k) ? m.get(k) : d);
/** Accepts a Map or plain object and returns a Map. */
export const toMap = (m) => (m instanceof Map ? m : new Map(Object.entries(m ?? {})));
export const toSet = (s) => (s instanceof Set ? s : new Set(s ?? []));
export const isBlank = (s) => s == null || s.trim().length === 0;
export const isNullOrBlank = isBlank;
/** Kotlin String.toIntOrNull(). */
export const toIntOrNull = (s) => (s != null && /^[+-]?\d+$/.test(s) && Math.abs(Number(s)) <= 2147483647 ? Number(s) : null);
/** Kotlin String.toDoubleOrNull() (approximate: Java float syntax). */
export const toDoubleOrNull = (s) => (s != null && /^\s*[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?[dDfF]?\s*$/.test(s) ? parseFloat(s) : null);
/** Kotlin "%.Nf".format(x) — HALF_UP rounding like java.util.Formatter. */
export const formatFixed = (x, n) => {
  const f = 10 ** n;
  const r = Math.sign(x) * Math.round(Math.abs(x) * f + 1e-9) / f;
  return r.toFixed(n);
};
/** Kotlin Double.toString(): always has a fractional part ("2.0"). */
export const doubleToString = (x) => (Number.isInteger(x) && Math.abs(x) < 1e7 ? `${x}.0` : String(x));
export const padStart2 = (n) => String(n).padStart(2, '0');
