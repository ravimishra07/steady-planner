/* theme.js — ES-module wrapper over the pre-paint global in sam-theme.js.
   Views import this; the global stays because the applier must run
   synchronously in <head> before first paint. */

const api = () => window.SamTheme;

export const THEME_OPTIONS = [
  { id: 'system', label: 'System' },
  { id: 'light', label: 'Light' },
  { id: 'dark', label: 'Dark' },
];

export const getTheme = () => (api() ? api().get() : 'system');
export const resolvedTheme = () => (api() ? api().resolved() : 'dark');
export const setTheme = (pref) => (api() ? api().set(pref) : undefined);
export const clearTheme = () => (api() ? api().clear() : undefined);
