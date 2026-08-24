'use client';

import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react';
import { DEFAULT_STATE, State } from './data';

const KEY = 'plan';

type Ctx = {
  state: State;
  /** true once localStorage has been read — screens gate rendering on this */
  ready: boolean;
  patch: (p: Partial<State>) => void;
  reset: () => void;
};

const PlanContext = createContext<Ctx | null>(null);

function read(): State {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? { ...DEFAULT_STATE, ...JSON.parse(raw) } : { ...DEFAULT_STATE };
  } catch {
    return { ...DEFAULT_STATE };
  }
}

export function PlanProvider({ children }: { children: React.ReactNode }) {
  // Start from defaults so server and first client render match, then hydrate.
  const [state, setState] = useState<State>(DEFAULT_STATE);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setState(read());
    setReady(true);
  }, []);

  const patch = useCallback((p: Partial<State>) => {
    setState(prev => {
      const next = { ...prev, ...p };
      try { localStorage.setItem(KEY, JSON.stringify(next)); } catch {}
      return next;
    });
  }, []);

  const reset = useCallback(() => {
    try { localStorage.removeItem(KEY); } catch {}
    setState({ ...DEFAULT_STATE });
  }, []);

  const value = useMemo(() => ({ state, ready, patch, reset }), [state, ready, patch, reset]);
  return <PlanContext.Provider value={value}>{children}</PlanContext.Provider>;
}

export function usePlan() {
  const ctx = useContext(PlanContext);
  if (!ctx) throw new Error('usePlan must be used inside <PlanProvider>');
  return ctx;
}
