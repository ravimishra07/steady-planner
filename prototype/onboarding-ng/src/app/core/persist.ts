import { WritableSignal, effect, signal, untracked } from '@angular/core';

/**
 * A signal backed by localStorage. Prototype-grade persistence: enough that
 * logged hours, test scores and revision rounds survive a reload, which is the
 * only way a day plan can be judged over more than one sitting.
 */
export function persisted<T>(
  key: string,
  fallback: T,
  encode: (value: T) => unknown = (v) => v,
  decode: (raw: any) => T = (v) => v as T,
): WritableSignal<T> {
  const store = 'steadyline.' + key;
  let initial = fallback;
  try {
    const raw = localStorage.getItem(store);
    if (raw !== null) initial = decode(JSON.parse(raw));
  } catch {
    // A corrupt or unavailable store is not worth failing the app over.
  }

  const s = signal<T>(initial);
  effect(() => {
    const value = s();
    untracked(() => {
      try {
        localStorage.setItem(store, JSON.stringify(encode(value)));
      } catch {}
    });
  });
  return s;
}

/** Sets are the shape most of this app's state takes; JSON has no Set. */
export function persistedSet(key: string, fallback: ReadonlySet<string> = new Set()) {
  return persisted<ReadonlySet<string>>(
    key,
    fallback,
    (v) => [...v],
    (raw) => new Set<string>(Array.isArray(raw) ? raw : []),
  );
}

/** Maps likewise: stored as entry pairs. */
export function persistedMap<V>(key: string, fallback: ReadonlyMap<string, V> = new Map()) {
  return persisted<ReadonlyMap<string, V>>(
    key,
    fallback,
    (v) => [...v],
    (raw) => new Map<string, V>(Array.isArray(raw) ? raw : []),
  );
}
