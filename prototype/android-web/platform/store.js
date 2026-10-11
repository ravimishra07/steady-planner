// store.js — localStorage persistence standing in for DataStore (prefs) and Room (tables).
// Every key lives under the `sl.` prefix. Values are JSON. Survives reload = app relaunch.
//
// prefs(name) → {
//   get(key, default)        value or default
//   set(key, value)          value === undefined → remove
//   remove(key)
//   flow(key, default)       StateFlow of that key (updates on set/remove/clear, and cross-tab)
//   all()                    {key: value} snapshot
//   data                     StateFlow of all() (like DataStore.data)
//   edit(fn)                 fn(mutableObj) then persists diff in one write (like DataStore.edit {})
//   clear()
// }
//   storage key: `sl.prefs.<name>` → JSON object
//
// table(name) → {
//   all()                    array of rows (copies), insertion order
//   get(id)
//   insert(row)  → id        autoincrement when row.id is null/undefined/0; else REPLACE by id (OnConflict.REPLACE)
//   insertAll(rows) → ids
//   update(row)              replace row with same id (no-op if missing)
//   upsert(row) → id
//   delete(idOrRow)
//   deleteWhere(predicate)
//   query(predicate)         filtered array
//   flow()                   StateFlow<rows[]>
//   clear()
// }
//   storage key: `sl.table.<name>` → {seq, rows}
//
// resetAll()  wipes every `sl.` key and resets in-memory flows.
import { MutableStateFlow } from './flow.js';

const PREFIX = 'sl.';
const ls = (() => { try { return window.localStorage; } catch { return null; } })();
const memory = new Map();
function read(k) {
  try { const s = ls ? ls.getItem(k) : memory.get(k); return s == null ? null : JSON.parse(s); }
  catch { return null; }
}
function write(k, v) {
  const s = JSON.stringify(v);
  try { ls ? ls.setItem(k, s) : memory.set(k, s); } catch (e) { console.error('store write failed', e); }
}
function del(k) { try { ls ? ls.removeItem(k) : memory.delete(k); } catch {} }

const clone = (v) => (v == null || typeof v !== 'object') ? v : JSON.parse(JSON.stringify(v));

const prefsCache = new Map();
const tableCache = new Map();

export function prefs(name) {
  if (prefsCache.has(name)) return prefsCache.get(name);
  const key = `${PREFIX}prefs.${name}`;
  let obj = read(key) || {};
  const data = new MutableStateFlow({ ...obj });
  const keyFlows = new Map(); // key -> {flow, def}
  const sync = () => {
    data.value = { ...obj };
    for (const [k, { flow, def }] of keyFlows) flow.value = k in obj ? obj[k] : def;
  };
  const persist = () => { write(key, obj); sync(); };
  const p = {
    get(k, def = null) { return k in obj ? clone(obj[k]) : def; },
    set(k, v) {
      if (v === undefined) return p.remove(k);
      obj = { ...obj, [k]: clone(v) }; persist();
    },
    remove(k) { if (!(k in obj)) return; obj = { ...obj }; delete obj[k]; persist(); },
    flow(k, def = null) {
      let e = keyFlows.get(k);
      if (!e) {
        e = { flow: new MutableStateFlow(k in obj ? obj[k] : def, { equals: jsonEq }), def };
        keyFlows.set(k, e);
      }
      return e.flow.asStateFlow();
    },
    all() { return clone(obj); },
    data: data.asStateFlow(),
    edit(fn) { const m = clone(obj); fn(m); obj = m; persist(); },
    clear() { obj = {}; del(key); sync(); },
    _reload() { obj = read(key) || {}; sync(); },
  };
  prefsCache.set(name, p);
  return p;
}

export function table(name) {
  if (tableCache.has(name)) return tableCache.get(name);
  const key = `${PREFIX}table.${name}`;
  let st = read(key) || { seq: 0, rows: [] };
  const flow = new MutableStateFlow(clone(st.rows));
  const persist = () => { write(key, st); flow.value = clone(st.rows); };
  const idOf = (r) => (typeof r === 'object' && r !== null ? r.id : r);
  const t = {
    all() { return clone(st.rows); },
    get(id) { return clone(st.rows.find((r) => r.id === id) ?? null); },
    insert(row, { persistNow = true } = {}) {
      const r = clone(row);
      if (r.id == null || r.id === 0) { r.id = ++st.seq; st.rows = [...st.rows, r]; }
      else {
        if (typeof r.id === 'number' && r.id > st.seq) st.seq = r.id;
        const i = st.rows.findIndex((x) => x.id === r.id);
        st.rows = i >= 0 ? st.rows.map((x, j) => (j === i ? r : x)) : [...st.rows, r];
      }
      if (persistNow) persist();
      return r.id;
    },
    insertAll(rows) { const ids = rows.map((r) => t.insert(r, { persistNow: false })); persist(); return ids; },
    update(row) {
      const i = st.rows.findIndex((x) => x.id === row.id);
      if (i < 0) return 0;
      st.rows = st.rows.map((x, j) => (j === i ? clone(row) : x)); persist(); return 1;
    },
    upsert(row) { return t.insert(row); },
    delete(idOrRow) {
      const id = idOf(idOrRow); const n = st.rows.length;
      st.rows = st.rows.filter((r) => r.id !== id);
      if (st.rows.length !== n) persist();
      return n - st.rows.length;
    },
    deleteWhere(pred) {
      const n = st.rows.length; st.rows = st.rows.filter((r) => !pred(r));
      if (st.rows.length !== n) persist();
      return n - st.rows.length;
    },
    query(pred) { return clone(st.rows.filter(pred)); },
    flow() { return flow.asStateFlow(); },
    clear() { st = { seq: 0, rows: [] }; del(key); flow.value = []; },
    _reload() { st = read(key) || { seq: 0, rows: [] }; flow.value = clone(st.rows); },
  };
  tableCache.set(name, t);
  return t;
}

export function resetAll() {
  if (ls) {
    const keys = [];
    for (let i = 0; i < ls.length; i++) { const k = ls.key(i); if (k && k.startsWith(PREFIX)) keys.push(k); }
    keys.forEach((k) => ls.removeItem(k));
  } else memory.clear();
  prefsCache.forEach((p) => p._reload());
  tableCache.forEach((t) => t._reload());
}

function jsonEq(a, b) { return Object.is(a, b) || JSON.stringify(a) === JSON.stringify(b); }

// Cross-tab sync (another tab writing = process restart for that key).
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (!e.key || !e.key.startsWith(PREFIX)) return;
    const rest = e.key.slice(PREFIX.length);
    if (rest.startsWith('prefs.')) prefsCache.get(rest.slice(6))?._reload();
    else if (rest.startsWith('table.')) tableCache.get(rest.slice(6))?._reload();
  });
}
