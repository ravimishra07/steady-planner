// components.js — core/design composables (AppComponents.kt, SessionListItem.kt, ProLock.kt, LengthPicker.kt)
// ported 1:1, plus the androidx Material 3 (1.3.1) primitives the features use, under `M3`.
// Metrics come from the material3-android-1.3.1 token sources (FilledButtonTokens, SwitchTokens, …).
//
// ── Conventions ────────────────────────────────────────────────────────────────────────────────
// • Every composable is `Name(props) → Element`. Kotlin named args → same prop names.
// • Modifier → `class` (string|array|object, see h.js cx) and `css` (style object) on the root element.
//   Common class helpers from tokens.css: weight, fill-w, fill-h, col, row, center-v, scroll-y, scroll-x.
//   `modifier: {class, css}` is accepted too.
// • Colours are CSS colour strings. Use C.<appColorsField> ('var(--c-…)') and alpha(C.x, 0.12)
//   (= Color.copy(alpha)). Omitted colours fall back to the M3 role defaults.
// • Slot lambdas (`content`, `label`, `icon`, `title`, `text`, `confirmButton`, …) take a Node, a string,
//   an array of Nodes, or a function returning one of those. `children` = trailing content lambda.
// • Text `style` is a typography name ('titleSmall') or a TextStyle object {fontSize, lineHeight,
//   fontWeight, letterSpacing}; CSS goes in `css`.
// • Overlays (AlertDialog, Dialog, DatePickerDialog, ModalBottomSheet, DropdownMenu) must be created while a
//   destination / observe() render is being built (platform/nav.js): they mount into the app overlay layer,
//   register a BackHandler (system back = onDismissRequest) and are removed when that render pass is disposed.
//   Re-rendering the same dialog in the next pass reuses it without replaying the enter animation.
//
// ── Exports ────────────────────────────────────────────────────────────────────────────────────
// Helpers   C, alpha(color,a), contentColorFor(color), attachRipple(el,opts), clickable(el,opts), slot(x),
//           overlayRoot(), Shapes, RoundedCornerShape(r|{topStart,…}), CircleShape, BorderStroke(w,color)
// Layout    Column, Row, Box, Spacer, LazyColumn, LazyRow, HorizontalPager(+rememberPagerState)
// Text/Icon Text, Icon
// Core      AppCardTone, AppCard, AppListGroup, AppListDivider, AppListRow, AppValueStepper,
//           AppStepperControl, AppShapes, SessionListItem, ProLockCard, ProLockIcon, LengthPickerLabels,
//           LengthPicker
// M3        Surface, Button, FilledTonalButton, OutlinedButton, TextButton, ElevatedButton, IconButton,
//           FloatingActionButton, Card, ElevatedCard, OutlinedCard, HorizontalDivider, VerticalDivider,
//           FilterChip, AssistChip, InputChip, Switch, Checkbox, RadioButton, Slider,
//           LinearProgressIndicator, CircularProgressIndicator, AlertDialog, Dialog, ModalBottomSheet,
//           DropdownMenu, DropdownMenuItem, NavigationBar, NavigationBarItem, SecondaryTabRow, PrimaryTabRow,
//           TabRow, Tab, SingleChoiceSegmentedButtonRow, SegmentedButton, SegmentedButtonDefaults,
//           OutlinedTextField, TopAppBar, CenterAlignedTopAppBar, SnackbarHost, SnackbarHostState,
//           Scaffold, DatePicker, DatePickerDialog, rememberDatePickerState, TimePicker,
//           rememberTimePickerState, ButtonDefaults, MaterialTheme
import { h, cx, styleOf } from '../platform/h.js';
import { icon as svgIcon } from '../platform/icons.js';
import {
  BackHandler, currentScope, onDispose, remember, rememberScoped, snapshot, restore, observe,
} from '../platform/nav.js';

// =============================================================================================
// Helpers
// =============================================================================================
export const C = new Proxy({}, { get: (_, k) => `var(--c-${String(k)})` });
export const alpha = (color, a) => `color-mix(in srgb, ${color} ${+(a * 100).toFixed(3)}%, transparent)`;
export const Transparent = 'transparent';

const ON = {
  primary: 'onPrimary', secondary: 'onSecondary', tertiary: 'onTertiary', error: 'onError',
  primaryContainer: 'onPrimaryContainer', secondaryContainer: 'onSecondaryContainer',
  tertiaryContainer: 'onTertiaryContainer', errorContainer: 'onErrorContainer',
  inverseSurface: 'inverseOnSurface', background: 'onBackground', surface: 'onSurface',
  surfaceVariant: 'onSurfaceVariant', surfaceBright: 'onSurface', surfaceDim: 'onSurface',
  surfaceContainer: 'onSurface', surfaceContainerHigh: 'onSurface', surfaceContainerHighest: 'onSurface',
  surfaceContainerLow: 'onSurface', surfaceContainerLowest: 'onSurface',
  // AppColors aliases that resolve to roles
  bg: 'onSurface', bgDeep: 'onSurface', surfaceCard: 'onSurface', surfaceControl: 'onSurface',
  surfaceInk: 'onSurface', elevated: 'onSurface', surface3: 'onSurface', tabBg: 'onSurface',
  brand: 'onPrimary', brandDeep: 'onPrimary', surfaceTinted: 'onPrimaryContainer', brandContainer: 'onPrimaryContainer',
  selectionContainer: 'onSecondaryContainer', successContainer: 'onTertiaryContainer', dangerContainer: 'onErrorContainer',
  danger: 'onError', success: 'onTertiary',
};
/** contentColorFor(backgroundColor): M3 pairing; null (= keep LocalContentColor) when unknown. */
export function contentColorFor(color) {
  const m = /^var\(--c-(\w+)\)$/.exec(color || '');
  if (m && ON[m[1]]) return `var(--c-${ON[m[1]]})`;
  return null;
}

/** Slot lambda → array of nodes. */
export function slot(x) {
  if (x == null || x === false) return [];
  if (typeof x === 'function') return slot(x());
  if (Array.isArray(x)) return x.flatMap(slot);
  if (x instanceof Node) return [x];
  return [Text({ text: String(x) })];
}

function mod(el, p = {}) {
  const m = p.modifier || {};
  const cls = cx(m.class, p.class);
  if (cls) el.classList.add(...cls.split(/\s+/).filter(Boolean));
  styleOf(el, m.css || m.style);
  styleOf(el, p.css);
  consumeInsets(el, [m.css || m.style, p.css], m.consumeWindowInsets ?? p.consumeWindowInsets);
  if (p.key != null) el.setAttribute('data-key', String(p.key));
  if (p.testTag) el.setAttribute('data-testid', p.testTag);
  return el;
}

// Window insets ------------------------------------------------------------------------------
/**
 * WindowInsets: the fake system bars (index.html --status-bar-height / --gesture-bar-height) as the *unconsumed*
 * insets at a point of the tree. `--inset-top` / `--inset-bottom` are 0 below an element that consumed them;
 * descendants (Scaffold, NavigationBar) read WindowInsets.* and so apply each inset once, like Compose.
 * An element consumes insets when its modifier css is an inset padding — `Modifier.padding(innerPadding)
 * .consumeWindowInsets(innerPadding)` / windowInsetsPadding: some value uses an inset variable; then every edge the
 * css pads (paddingTop / paddingBottom / padding / paddingBlock, even '0px' = a bar outside already covers it) is
 * consumed — or when the modifier says so: `modifier: { consumeWindowInsets: true | { top?, bottom? } }`.
 */
export const WindowInsets = Object.freeze({
  statusBars: 'var(--inset-top, var(--status-bar-height, 24px))',
  navigationBars: 'var(--inset-bottom, var(--gesture-bar-height, 24px))',
});
const INSET_VAR = /--(status-bar-height|gesture-bar-height|inset-top|inset-bottom)\b/;
function consumeInsets(el, cssList, explicit) {
  let top = false, bottom = false;
  for (const css of cssList) {
    if (!css || typeof css !== 'object') continue;
    if (!Object.values(css).some((v) => INSET_VAR.test(String(v ?? '')))) continue;
    const keys = Object.keys(css);
    if (keys.some((k) => k === 'padding' || k === 'paddingBlock')) top = bottom = true;
    if (keys.includes('paddingTop') || keys.includes('paddingBlockStart')) top = true;
    if (keys.includes('paddingBottom') || keys.includes('paddingBlockEnd')) bottom = true;
  }
  if (explicit === true) top = bottom = true;
  else if (explicit && typeof explicit === 'object') { top = top || !!explicit.top; bottom = bottom || !!explicit.bottom; }
  // Consumed for the descendants only (tokens.css `.consumes-inset-* > *`): the element's own padding still reads
  // the inherited inset, so `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)` pads once, not zero.
  if (top) el.classList.add('consumes-inset-top');
  if (bottom) el.classList.add('consumes-inset-bottom');
}
const has = (p, ...props) => {
  const css = { ...(p.modifier?.css || {}), ...(p.css || {}) };
  return props.some((k) => css[k] != null);
};
const hasClass = (p, ...names) => {
  const c = ' ' + cx(p.modifier?.class, p.class) + ' ';
  return names.some((n) => c.includes(' ' + n + ' '));
};

// Shapes ------------------------------------------------------------------------------------
export function RoundedCornerShape(r) {
  if (r == null) return '0';
  if (typeof r === 'object') {
    const v = (x) => (x == null ? '0' : typeof x === 'number' ? x + 'px' : x);
    return `${v(r.topStart)} ${v(r.topEnd)} ${v(r.bottomEnd)} ${v(r.bottomStart)}`;
  }
  return typeof r === 'number' ? r + 'px' : r;
}
export const CircleShape = '9999px';
export const RectangleShape = '0';
export const Shapes = {
  extraSmall: 'var(--radius-extraSmall)', small: 'var(--radius-small)', medium: 'var(--radius-medium)',
  large: 'var(--radius-large)', extraLarge: 'var(--radius-extraLarge)', full: 'var(--radius-full)',
};
export const BorderStroke = (width, color) => ({ width: typeof width === 'number' ? width + 'px' : width, color });
/**
 * Modifier.border / Surface(border): Compose draws the stroke inside the bounds, on top of the content (border's
 * drawContent() runs first), without moving it. A CSS border would push the content in by its width and an outline
 * paints under positioned children, so the stroke is an absolutely positioned ::after (tokens.css `.border-stroke`)
 * filling the element, following its border-radius and stacked above the children; the element becomes its
 * containing block and an isolated stacking context so the stroke stays local.
 */
function applyBorder(el, b) {
  if (!b) return el;
  el.classList.add('border-stroke');
  el.style.setProperty('--border-stroke-width', b.width);
  el.style.setProperty('--border-stroke-color', b.color);
  if (!el.style.position) el.style.position = 'relative';
  el.style.isolation = 'isolate';
  return el;
}

// Ripple / clickable -----------------------------------------------------------------------
const CircularEasing = 'cubic-bezier(0.4, 0, 0.2, 1)';
/**
 * M3 ripple: state layer (hover 8%) via .state-layer::before, press ripple in currentColor at 10%.
 * opts: { bounded=true, centered=false, radius?: px, color?: CSS colour }
 */
export function attachRipple(el, opts = {}) {
  el.classList.add('state-layer');
  if (opts.color) el.style.setProperty('--ripple-color', opts.color);
  el.addEventListener('pointerdown', (e) => {
    if (e.button !== 0 || el.getAttribute('aria-disabled') === 'true') return;
    const r = el.getBoundingClientRect();
    const w = r.width, hgt = r.height;
    const bounded = opts.bounded !== false;
    const endRadius = opts.radius ?? (bounded ? Math.hypot(w, hgt) / 2 + 10 : Math.max(w, hgt) / 2);
    const startRadius = Math.max(w, hgt) * 0.3;
    const sx = opts.centered || !bounded ? w / 2 : e.clientX - r.left;
    const sy = opts.centered || !bounded ? hgt / 2 : e.clientY - r.top;
    const dot = document.createElement('span');
    dot.className = 'ripple';
    if (opts.color) dot.style.background = opts.color;
    const d = endRadius * 2;
    Object.assign(dot.style, { width: d + 'px', height: d + 'px', left: w / 2 - endRadius + 'px', top: hgt / 2 - endRadius + 'px', transition: 'none', opacity: '0' });
    el.appendChild(dot);
    const from = `translate(${sx - w / 2}px, ${sy - hgt / 2}px) scale(${startRadius / endRadius})`;
    const anim = dot.animate(
      [{ transform: from, opacity: 0.1 }, { transform: 'translate(0,0) scale(1)', opacity: 0.1 }],
      { duration: 225, easing: CircularEasing, fill: 'forwards' },
    );
    dot.style.opacity = '0.1'; // StateTokens.PressedStateLayerOpacity
    let released = false;
    const release = () => {
      if (released) return; released = true;
      const fade = () => {
        const f = dot.animate([{ opacity: 0.1 }, { opacity: 0 }], { duration: 150, easing: 'linear', fill: 'forwards' });
        f.onfinish = () => dot.remove();
      };
      if (anim.playState === 'finished') fade(); else anim.onfinish = fade;
      window.removeEventListener('pointerup', release, true);
      window.removeEventListener('pointercancel', release, true);
    };
    window.addEventListener('pointerup', release, true);
    window.addEventListener('pointercancel', release, true);
  });
  return el;
}

/**
 * Modifier.clickable / selectable / toggleable. opts: { onClick, enabled=true, indication=true (false = no ripple),
 * role: 'button'|'checkbox'|'radio'|'switch'|'tab', selected?, checked?, bounded?, centered?, radius? }
 */
export function clickable(el, opts = {}) {
  const enabled = opts.enabled !== false;
  if (opts.indication !== false) attachRipple(el, opts);
  else el.classList.add('state-layer', 'no-indication');
  el.setAttribute('role', opts.role || 'button');
  if (opts.selected != null) el.setAttribute('aria-selected', String(!!opts.selected));
  if (opts.checked != null) el.setAttribute('aria-checked', String(!!opts.checked));
  if (!enabled) el.setAttribute('aria-disabled', 'true');
  el.tabIndex = enabled ? 0 : -1;
  if (enabled && opts.onClick) {
    el.addEventListener('click', (e) => { e.stopPropagation(); opts.onClick(e); });
    el.addEventListener('keydown', (e) => {
      if ((e.key === 'Enter' || e.key === ' ') && e.target === el) { e.preventDefault(); opts.onClick(e); }
    });
  }
  return el;
}

/**
 * Modifier.minimumInteractiveComponentSize(): wraps `inner` in a ≥48dp layout box; caller modifier goes on the wrapper.
 * An explicit width/height in the caller's css is a fixed constraint in Compose: the layout (and the visual, and the
 * component's own defaultMinSize such as ButtonDefaults.MinHeight) follow it, even below 48dp. The touch area stays
 * ≥48dp through the wrapper's invisible ::after hit box (tokens.css); taps on it are forwarded to `inner`.
 */
function touchTarget(inner, p) {
  const outer = h('div', { class: 'touch-target' });
  outer.appendChild(inner);
  // Compose IconButton = size(40) under the caller's constraint: a fixed width alone leaves the height at 40.
  const fixedW = has(p, 'width');
  const fixedH = has(p, 'height');
  if (fixedW) { outer.style.minWidth = '0px'; inner.style.minWidth = '0px'; }
  if (fixedH) { outer.style.minHeight = '0px'; inner.style.minHeight = '0px'; }
  mod(outer, p);
  if (fixedW || has(p, 'flex', 'flexBasis', 'minWidth') || hasClass(p, 'weight', 'fill-w', 'fill')) inner.style.width = '100%';
  if (fixedH || has(p, 'minHeight') || hasClass(p, 'fill-h', 'fill')) inner.style.height = '100%';
  // Expanded hit area → the control (click + ripple), like Compose's touch-bounds expansion.
  outer.addEventListener('pointerdown', (e) => {
    if (e.target !== outer) return;
    inner.dispatchEvent(new PointerEvent('pointerdown', e));
  });
  outer.addEventListener('click', (e) => {
    if (e.target !== outer) return;
    e.stopPropagation();
    inner.click();
  });
  return outer;
}

let _overlayRoot = null;
export function overlayRoot() {
  if (_overlayRoot && _overlayRoot.isConnected) return _overlayRoot;
  _overlayRoot = document.querySelector('[data-overlay-root]') || document.body;
  return _overlayRoot;
}

const FW = { Thin: 100, ExtraLight: 200, Light: 300, Normal: 400, Medium: 500, SemiBold: 600, Bold: 700, ExtraBold: 800, Black: 900 };
const fw = (w) => (w == null ? null : typeof w === 'number' ? w : FW[w] ?? w);

// =============================================================================================
// Layout
// =============================================================================================
const ARR = { Top: 'flex-start', Start: 'flex-start', Center: 'center', Bottom: 'flex-end', End: 'flex-end', SpaceBetween: 'space-between', SpaceAround: 'space-around', SpaceEvenly: 'space-evenly' };
const ALIGN = { Top: 'flex-start', Start: 'flex-start', CenterVertically: 'center', CenterHorizontally: 'center', Center: 'center', Bottom: 'flex-end', End: 'flex-end' };
function arrangement(el, a) {
  if (a == null) return;
  if (typeof a === 'string' && ARR[a]) { el.style.justifyContent = ARR[a]; return; }
  // { spacedBy: '8px' | 8, alignment?: 'Center'|'End'|'Bottom' }
  if (typeof a === 'object' && a.spacedBy != null) {
    el.style.gap = typeof a.spacedBy === 'number' ? a.spacedBy + 'px' : a.spacedBy;
    if (a.alignment) el.style.justifyContent = ARR[a.alignment];
    return;
  }
  el.style.gap = typeof a === 'number' ? a + 'px' : a;
}
/**
 * Column({ verticalArrangement?: 'Top'|'Center'|'Bottom'|'SpaceBetween'|…|{spacedBy}|'8px', horizontalAlignment?: 'Start'|'CenterHorizontally'|'End', children })
 * Children stretch to full width only with their own fill-w (Compose default is Start = wrap).
 */
export function Column(p = {}, ...kids) {
  const el = h('div', { class: 'col' });
  arrangement(el, p.verticalArrangement);
  el.style.alignItems = ALIGN[p.horizontalAlignment] || 'flex-start';
  mod(el, p);
  el.append(...slot(p.children ?? kids));
  return el;
}
/** Row({ horizontalArrangement?, verticalAlignment?: 'Top'|'CenterVertically'|'Bottom', children }) */
export function Row(p = {}, ...kids) {
  const el = h('div', { class: 'row' });
  arrangement(el, p.horizontalArrangement);
  el.style.alignItems = ALIGN[p.verticalAlignment] || 'flex-start';
  mod(el, p);
  el.append(...slot(p.children ?? kids));
  return el;
}
/** Box({ contentAlignment?: 'TopStart'|'Center'|'BottomEnd'|…, children }) — children stacked in one grid cell. */
const BOX_ALIGN = {
  TopStart: ['start', 'start'], TopCenter: ['start', 'center'], TopEnd: ['start', 'end'],
  CenterStart: ['center', 'start'], Center: ['center', 'center'], CenterEnd: ['center', 'end'],
  BottomStart: ['end', 'start'], BottomCenter: ['end', 'center'], BottomEnd: ['end', 'end'],
};
export function Box(p = {}, ...kids) {
  const el = h('div', { class: 'box' });
  const [a, j] = BOX_ALIGN[p.contentAlignment || 'TopStart'];
  el.style.alignItems = a; el.style.justifyItems = j;
  mod(el, p);
  el.append(...slot(p.children ?? kids));
  return el;
}
/** Spacer({ width?, height?, weight? }) */
export function Spacer(p = {}) {
  const el = h('div', { class: p.weight ? 'weight' : null });
  el.style.flex = p.weight ? `${p.weight} 1 0` : 'none';
  if (p.width != null) el.style.width = typeof p.width === 'number' ? p.width + 'px' : p.width;
  if (p.height != null) el.style.height = typeof p.height === 'number' ? p.height + 'px' : p.height;
  return mod(el, p);
}
/** LazyColumn({ contentPadding?: css padding, verticalArrangement?, horizontalAlignment?, scrollKey?, children }) */
export function LazyColumn(p = {}, ...kids) {
  const el = Column({ ...p, class: cx('scroll-y', p.class) }, ...kids);
  if (p.contentPadding) el.style.padding = p.contentPadding;
  el.setAttribute('data-scroll-key', p.scrollKey || 'lazy-column');
  return el;
}
/** LazyRow({ contentPadding?, horizontalArrangement?, verticalAlignment?, scrollKey?, children }) */
export function LazyRow(p = {}, ...kids) {
  const el = Row({ ...p, class: cx('scroll-x', p.class) }, ...kids);
  if (p.contentPadding) el.style.padding = p.contentPadding;
  el.setAttribute('data-scroll-key', p.scrollKey || 'lazy-row');
  return el;
}

/** rememberPagerState({ initialPage, pageCount }) — stored in the current composition (nav.remember). */
export function rememberPagerState({ initialPage = 0, pageCount = 0, key = 'pager' } = {}) {
  const s = remember(key, () => ({
    currentPage: initialPage, pageCount, _el: null, _listeners: new Set(),
    scrollToPage(i) { this.currentPage = i; this._scroll(i, false); },
    animateScrollToPage(i) { this.currentPage = i; this._scroll(i, true); },
    _scroll(i, smooth) {
      const el = this._el; if (!el) return;
      const page = el.children[i]; if (!page) return;
      const left = page.offsetLeft - (el.clientWidth - page.offsetWidth) / 2;
      el.scrollTo({ left, behavior: smooth ? 'smooth' : 'auto' });
    },
  }));
  s.value.pageCount = pageCount;
  return s.value;
}
/**
 * HorizontalPager({ state, pageSize?: css width (PageSize.Fixed) , pageSpacing?, contentPadding?: css horizontal
 *   padding, onPageChange?(i), pageContent(i) → Node })  — scroll-snap centred pages.
 */
export function HorizontalPager(p) {
  const el = h('div', { class: 'scroll-x row', style: { scrollSnapType: 'x mandatory', gap: p.pageSpacing || 0, paddingInline: p.contentPadding || 0, alignItems: 'stretch' } });
  mod(el, p);
  const st = p.state;
  for (let i = 0; i < (st?.pageCount ?? p.pageCount ?? 0); i++) {
    const page = h('div', { style: { flex: 'none', width: p.pageSize || '100%', scrollSnapAlign: 'center' } }, p.pageContent(i));
    el.appendChild(page);
  }
  if (st) {
    st._el = el;
    queueMicrotask(() => st._scroll(st.currentPage, false));
    let t = null;
    el.addEventListener('scroll', () => {
      clearTimeout(t);
      t = setTimeout(() => {
        const mid = el.scrollLeft + el.clientWidth / 2;
        let best = 0, bd = Infinity;
        [...el.children].forEach((c, i) => { const d = Math.abs(c.offsetLeft + c.offsetWidth / 2 - mid); if (d < bd) { bd = d; best = i; } });
        if (best !== st.currentPage) { st.currentPage = best; p.onPageChange?.(best); }
      }, 60);
    });
  }
  el.setAttribute('data-scroll-key', p.scrollKey || 'pager');
  return el;
}

// =============================================================================================
// Text & Icon
// =============================================================================================
/**
 * Text({ text, style?: 'bodyLarge'|…|TextStyle, color?, fontWeight?: 'SemiBold'|600, fontSize?, lineHeight?,
 *   letterSpacing?, textAlign?: 'Start'|'Center'|'End', maxLines?, overflow?: 'Clip'|'Ellipsis', softWrap?,
 *   textDecoration?, fontStyle?, class?, css? })  — `Text('hello')` also works. No style = inherit
 *   LocalTextStyle (bodyLarge at the root, labelLarge inside buttons…).
 */
export function Text(p) {
  if (typeof p === 'string' || typeof p === 'number') p = { text: String(p) };
  const el = h('div', { class: 'text' });
  if (typeof p.style === 'string') el.classList.add('t-' + p.style);
  else if (p.style && typeof p.style === 'object') {
    const s = p.style;
    if (s.typography) el.classList.add('t-' + s.typography);
    if (s.fontSize != null) el.style.fontSize = px(s.fontSize);
    if (s.lineHeight != null) el.style.lineHeight = px(s.lineHeight);
    if (s.fontWeight != null) el.style.fontWeight = fw(s.fontWeight);
    if (s.letterSpacing != null) el.style.letterSpacing = px(s.letterSpacing);
    if (s.color) el.style.color = s.color;
  }
  if (p.color) el.style.color = p.color;
  if (p.fontWeight != null) el.style.fontWeight = fw(p.fontWeight);
  if (p.fontSize != null) el.style.fontSize = px(p.fontSize);
  if (p.lineHeight != null) el.style.lineHeight = px(p.lineHeight);
  if (p.letterSpacing != null) el.style.letterSpacing = px(p.letterSpacing);
  if (p.fontStyle === 'Italic') el.style.fontStyle = 'italic';
  if (p.textDecoration === 'LineThrough') el.style.textDecoration = 'line-through';
  if (p.textDecoration === 'Underline') el.style.textDecoration = 'underline';
  if (p.textAlign) el.style.textAlign = { Start: 'start', Center: 'center', End: 'end', Left: 'left', Right: 'right', Justify: 'justify' }[p.textAlign] || p.textAlign;
  el.style.whiteSpace = p.softWrap === false ? 'nowrap' : 'pre-wrap';
  el.style.overflowWrap = 'anywhere';
  if (p.maxLines === 1) {
    el.style.whiteSpace = 'nowrap'; el.style.overflow = 'hidden'; el.style.minWidth = '0';
    if (p.overflow === 'Ellipsis') el.style.textOverflow = 'ellipsis';
  } else if (p.maxLines > 1) {
    Object.assign(el.style, { display: '-webkit-box', webkitLineClamp: String(p.maxLines), webkitBoxOrient: 'vertical', overflow: 'hidden', minWidth: '0' });
    el.style.setProperty('-webkit-line-clamp', String(p.maxLines));
  }
  el.textContent = p.text ?? '';
  return mod(el, p);
}
const px = (v) => (typeof v === 'number' ? v + 'px' : v);

/** Icon({ imageVector: 'Filled.Add', contentDescription?, tint?, size?: css (default 24dp), class?, css? }) */
export function Icon(p) {
  if (typeof p === 'string') p = { imageVector: p };
  const size = p.size || (p.css && (p.css.width || p.css.size)) || 'var(--size-standardIcon)';
  const el = svgIcon(p.imageVector, size, { tint: p.tint, contentDescription: p.contentDescription });
  return mod(el, { ...p, css: p.css ? { ...p.css, size: undefined } : undefined });
}

// =============================================================================================
// Surface
// =============================================================================================
const SHADOW = (dp) => {
  const n = parseFloat(dp) || 0;
  if (n <= 0) return 'none';
  if (n <= 1) return 'var(--shadow-1)';
  if (n <= 3) return 'var(--shadow-3)';
  if (n <= 6) return 'var(--shadow-6)';
  if (n <= 8) return 'var(--shadow-8)';
  return 'var(--shadow-12)';
};
/** Tonal elevation only tints when the colour is exactly colorScheme.surface (surfaceColorAtElevation). */
function tonal(color, elevation) {
  const e = parseFloat(elevation) || 0;
  if (!e || color !== 'var(--c-surface)') return color;
  const a = ((4.5 * Math.log(e + 1)) + 2) / 100;
  return `color-mix(in srgb, var(--c-primary) ${(a * 100).toFixed(3)}%, var(--c-surface))`;
}
/**
 * Surface({ color=surface, contentColor=contentColorFor(color), shape?: radius css, tonalElevation?, shadowElevation?,
 *   border?: BorderStroke, onClick?, enabled?, selected?, checked?, onCheckedChange?, children, class?, css?, tag? })
 * Clickable surfaces get the 48dp minimum touch target and a bounded ripple.
 */
export function Surface(p = {}, ...kids) {
  const color = p.color ?? 'var(--c-surface)';
  const content = p.contentColor ?? contentColorFor(color);
  const inner = h(p.tag || 'div', { class: 'surface' });
  inner.style.background = tonal(color, p.tonalElevation);
  if (content) inner.style.color = content;
  if (p.shape) inner.style.borderRadius = p.shape;
  applyBorder(inner, p.border);
  const sh = SHADOW(p.shadowElevation);
  if (sh !== 'none') inner.style.boxShadow = sh;
  inner.style.position = 'relative';
  inner.append(...slot(p.children ?? kids));
  const onClick = p.onClick || (p.onCheckedChange ? () => p.onCheckedChange(!p.checked) : null);
  if (onClick) {
    clickable(inner, { onClick, enabled: p.enabled, selected: p.selected, checked: p.checked, role: p.role || (p.onCheckedChange ? 'checkbox' : p.selected != null ? 'tab' : 'button') });
    inner.style.overflow = 'hidden';
    inner.style.isolation = 'isolate';
    return touchTarget(inner, p);
  }
  if (p.shape) inner.style.overflow = p.clip === false ? '' : 'hidden';
  return mod(inner, p);
}

// =============================================================================================
// Buttons
// =============================================================================================
const BTN = {
  filled: { container: 'var(--c-primary)', content: 'var(--c-onPrimary)', pad: '8px 24px', padIcon: '8px 24px 8px 16px', elev: 0 },
  tonal: { container: 'var(--c-secondaryContainer)', content: 'var(--c-onSecondaryContainer)', pad: '8px 24px', padIcon: '8px 24px 8px 16px', elev: 0 },
  elevated: { container: 'var(--c-surfaceContainerLow)', content: 'var(--c-primary)', pad: '8px 24px', padIcon: '8px 24px 8px 16px', elev: 1 },
  outlined: { container: 'transparent', content: 'var(--c-primary)', pad: '8px 24px', padIcon: '8px 24px 8px 16px', elev: 0, border: true },
  text: { container: 'transparent', content: 'var(--c-primary)', pad: '8px 12px', padIcon: '8px 16px 8px 12px', elev: 0 },
};
/** ButtonDefaults: colour factories mirror Kotlin; values are CSS colours. */
export const ButtonDefaults = {
  MinWidth: '58px', MinHeight: '40px', IconSize: '18px', IconSpacing: '8px',
  ContentPadding: '8px 24px', ButtonWithIconContentPadding: '8px 24px 8px 16px', TextButtonContentPadding: '8px 12px',
  buttonColors: (c = {}) => ({ kind: 'filled', ...c }),
  filledTonalButtonColors: (c = {}) => ({ kind: 'tonal', ...c }),
  elevatedButtonColors: (c = {}) => ({ kind: 'elevated', ...c }),
  outlinedButtonColors: (c = {}) => ({ kind: 'outlined', ...c }),
  textButtonColors: (c = {}) => ({ kind: 'text', ...c }),
  outlinedButtonBorder: (enabled = true) => BorderStroke(1, enabled ? 'var(--c-outline)' : alpha('var(--c-onSurface)', 0.12)),
};
function buttonImpl(kind, p, kids) {
  const spec = BTN[kind];
  const enabled = p.enabled !== false;
  const col = p.colors || {};
  const container = enabled ? (col.containerColor ?? spec.container) : (col.disabledContainerColor ?? (spec.container === 'transparent' ? 'transparent' : alpha('var(--c-onSurface)', 0.12)));
  const content = enabled ? (col.contentColor ?? spec.content) : (col.disabledContentColor ?? alpha('var(--c-onSurface)', 0.38));
  const inner = h('div', { class: 'm3-button t-labelLarge' });
  Object.assign(inner.style, {
    display: 'flex', alignItems: 'center', justifyContent: 'center', boxSizing: 'border-box',
    minWidth: 'var(--btn-min-width, 58px)', minHeight: '40px', padding: p.contentPadding || spec.pad,
    background: container, color: content, borderRadius: p.shape || 'var(--radius-full)',
    position: 'relative', overflow: 'hidden', isolation: 'isolate', whiteSpace: 'nowrap',
  });
  const border = p.border !== undefined ? p.border : (spec.border ? ButtonDefaults.outlinedButtonBorder(enabled) : null);
  applyBorder(inner, border);
  const elev = p.elevation ?? spec.elev;
  if (elev && enabled) inner.style.boxShadow = SHADOW(elev);
  const children = slot(p.children ?? p.content ?? kids);
  inner.append(...children);
  clickable(inner, { onClick: p.onClick, enabled });
  return touchTarget(inner, p);
}
/** Button({ onClick, enabled?, shape?, colors?: ButtonDefaults.buttonColors({containerColor, contentColor, disabledContainerColor, disabledContentColor}), elevation?: dp, border?, contentPadding?: css, children }) */
export const Button = (p, ...k) => buttonImpl('filled', p, k);
export const FilledTonalButton = (p, ...k) => buttonImpl('tonal', p, k);
export const ElevatedButton = (p, ...k) => buttonImpl('elevated', p, k);
export const OutlinedButton = (p, ...k) => buttonImpl('outlined', p, k);
export const TextButton = (p, ...k) => buttonImpl('text', p, k);

/**
 * IconButton({ onClick, enabled?, colors?: {containerColor, contentColor, disabledContentColor}, children })
 * 40dp state layer inside a 48dp touch target. Content colour = LocalContentColor (inherited) by default.
 */
export function IconButton(p, ...kids) {
  const enabled = p.enabled !== false;
  const inner = h('div', { class: 'm3-icon-button' });
  Object.assign(inner.style, {
    width: '40px', height: '40px', display: 'flex', alignItems: 'center', justifyContent: 'center',
    borderRadius: '50%', position: 'relative', overflow: 'hidden', isolation: 'isolate',
    background: p.colors?.containerColor || 'transparent',
  });
  if (p.colors?.contentColor) inner.style.color = p.colors.contentColor;
  if (!enabled) inner.style.color = p.colors?.disabledContentColor || alpha(p.colors?.contentColor || 'currentColor', 0.38);
  inner.append(...slot(p.children ?? p.content ?? kids));
  clickable(inner, { onClick: p.onClick, enabled, centered: true });
  return touchTarget(inner, p);
}

/** FloatingActionButton({ onClick, containerColor=primaryContainer, contentColor, shape=large(16), elevation?, children }) 56dp. */
export function FloatingActionButton(p, ...kids) {
  const container = p.containerColor || 'var(--c-primaryContainer)';
  const inner = h('div', { class: 'm3-fab' });
  Object.assign(inner.style, {
    width: '56px', height: '56px', display: 'flex', alignItems: 'center', justifyContent: 'center',
    borderRadius: p.shape || 'var(--radius-large)', background: container,
    color: p.contentColor || contentColorFor(container) || 'var(--c-onPrimaryContainer)',
    boxShadow: 'var(--shadow-6)', position: 'relative', overflow: 'hidden', isolation: 'isolate',
  });
  inner.append(...slot(p.children ?? kids));
  clickable(inner, { onClick: p.onClick });
  return mod(inner, p);
}

// Cards --------------------------------------------------------------------------------------
/** Card({ onClick?, shape=medium(12), colors?: {containerColor, contentColor}, elevation?, border?, children }) — Column content. */
export function Card(p = {}, ...kids) {
  return Surface({
    ...p, color: p.colors?.containerColor ?? 'var(--c-surfaceContainerHighest)', contentColor: p.colors?.contentColor,
    shape: p.shape || 'var(--radius-medium)', shadowElevation: p.elevation ?? 0,
    children: Column({ class: 'fill-w' }, ...slot(p.children ?? kids)),
  });
}
export const ElevatedCard = (p = {}, ...k) => Card({ ...p, colors: { containerColor: 'var(--c-surfaceContainerLow)', ...(p.colors || {}) }, elevation: p.elevation ?? 1 }, ...k);
export const OutlinedCard = (p = {}, ...k) => Card({ ...p, colors: { containerColor: 'var(--c-surface)', ...(p.colors || {}) }, border: p.border ?? BorderStroke(1, 'var(--c-outlineVariant)') }, ...k);

// Dividers -----------------------------------------------------------------------------------
/** HorizontalDivider({ thickness=1dp, color=outlineVariant }) — fills width. */
export function HorizontalDivider(p = {}) {
  const el = h('div', { role: 'separator' });
  Object.assign(el.style, { height: px(p.thickness ?? 1), background: p.color || 'var(--c-outlineVariant)', width: '100%', flex: 'none', alignSelf: 'stretch' });
  return mod(el, p);
}
export function VerticalDivider(p = {}) {
  const el = h('div', { role: 'separator' });
  Object.assign(el.style, { width: px(p.thickness ?? 1), background: p.color || 'var(--c-outlineVariant)', alignSelf: 'stretch', flex: 'none' });
  return mod(el, p);
}

// Chips -------------------------------------------------------------------------------------
/**
 * FilterChip({ selected, onClick, label, enabled?, leadingIcon?, trailingIcon?, shape=small(8),
 *   colors?: {containerColor, selectedContainerColor, labelColor, selectedLabelColor, iconColor, selectedLeadingIconColor},
 *   border?: BorderStroke|null })  32dp high, 48dp touch target.
 */
export function FilterChip(p) {
  const enabled = p.enabled !== false;
  const sel = !!p.selected;
  const c = p.colors || {};
  const inner = h('div', { class: 'm3-chip t-labelLarge' });
  const container = sel
    ? (enabled ? (c.selectedContainerColor ?? 'var(--c-secondaryContainer)') : alpha('var(--c-onSurface)', 0.12))
    : (c.containerColor ?? 'transparent');
  const labelColor = !enabled ? alpha('var(--c-onSurface)', 0.38) : sel ? (c.selectedLabelColor ?? 'var(--c-onSecondaryContainer)') : (c.labelColor ?? 'var(--c-onSurfaceVariant)');
  Object.assign(inner.style, {
    height: '32px', display: 'flex', alignItems: 'center', padding: '0 8px', borderRadius: p.shape || 'var(--radius-small)',
    background: container, color: labelColor, position: 'relative', overflow: 'hidden', isolation: 'isolate', whiteSpace: 'nowrap',
  });
  const border = p.border !== undefined ? p.border : (sel ? null : BorderStroke(1, enabled ? 'var(--c-outline)' : alpha('var(--c-onSurface)', 0.12)));
  inner.style.boxShadow = border ? `inset 0 0 0 ${border.width} ${border.color}` : 'none';
  const lead = slot(p.leadingIcon);
  if (lead.length) {
    const li = h('div', { style: { display: 'flex', width: '18px', height: '18px', alignItems: 'center', justifyContent: 'center', color: !enabled ? alpha('var(--c-onSurface)', 0.38) : sel ? (c.selectedLeadingIconColor ?? 'var(--c-onSecondaryContainer)') : (c.iconColor ?? 'var(--c-primary)') } }, ...lead);
    li.querySelectorAll('svg.icon').forEach((s) => { s.style.width = '18px'; s.style.height = '18px'; });
    inner.append(li);
  }
  inner.append(h('div', { style: { padding: '0 8px' } }, ...slot(p.label)));
  const trail = slot(p.trailingIcon);
  if (trail.length) {
    const ti = h('div', { style: { display: 'flex', width: '18px', height: '18px', alignItems: 'center', justifyContent: 'center' } }, ...trail);
    ti.querySelectorAll('svg.icon').forEach((s) => { s.style.width = '18px'; s.style.height = '18px'; });
    inner.append(ti);
  }
  clickable(inner, { onClick: p.onClick, enabled, role: 'checkbox', checked: sel });
  return touchTarget(inner, p);
}
/** AssistChip({ onClick, label, leadingIcon?, trailingIcon?, enabled?, colors?, border? }) */
export function AssistChip(p) {
  return FilterChip({ ...p, selected: false, colors: { labelColor: 'var(--c-onSurface)', iconColor: 'var(--c-primary)', ...(p.colors || {}) }, border: p.border !== undefined ? p.border : BorderStroke(1, 'var(--c-outlineVariant)') });
}
/** InputChip({ selected, onClick, label, leadingIcon?, trailingIcon?, enabled? }) */
export function InputChip(p) {
  return FilterChip({ ...p, border: p.border !== undefined ? p.border : (p.selected ? null : BorderStroke(1, 'var(--c-outlineVariant)')) });
}

// Selection controls --------------------------------------------------------------------------
/**
 * Switch({ checked, onCheckedChange?: (bool)=>void | null, enabled?, colors?: {checkedThumbColor, checkedTrackColor,
 *   checkedBorderColor, uncheckedThumbColor, uncheckedTrackColor, uncheckedBorderColor}, thumbContent? })
 * 52×32 track; null onCheckedChange → not focusable/clickable (the row handles it) and no touch-target padding.
 */
export function Switch(p) {
  const on = !!p.checked, enabled = p.enabled !== false;
  const c = p.colors || {};
  const track = h('div', { class: 'm3-switch' });
  const trackColor = enabled
    ? (on ? (c.checkedTrackColor ?? 'var(--c-primary)') : (c.uncheckedTrackColor ?? 'var(--c-surfaceContainerHighest)'))
    : (on ? alpha('var(--c-onSurface)', 0.12) : alpha('var(--c-surfaceContainerHighest)', 0.12));
  const borderColor = on ? (c.checkedBorderColor ?? 'transparent') : enabled ? (c.uncheckedBorderColor ?? 'var(--c-outline)') : alpha('var(--c-onSurface)', 0.12);
  Object.assign(track.style, {
    width: '52px', height: '32px', borderRadius: '16px', background: trackColor, position: 'relative', flex: 'none',
    boxShadow: `inset 0 0 0 2px ${borderColor}`, transition: 'background-color 100ms linear',
  });
  const hasIcon = !!p.thumbContent;
  const d = on || hasIcon ? 24 : 16;
  const thumbColor = enabled
    ? (on ? (c.checkedThumbColor ?? 'var(--c-onPrimary)') : (c.uncheckedThumbColor ?? 'var(--c-outline)'))
    : (on ? 'var(--c-surface)' : alpha('var(--c-onSurface)', 0.38));
  const thumb = h('div');
  const x = on ? 52 - 4 - 24 : (32 - d) / 2;
  Object.assign(thumb.style, {
    position: 'absolute', top: (32 - d) / 2 + 'px', left: x + 'px', width: d + 'px', height: d + 'px', borderRadius: '50%',
    background: thumbColor, transition: 'all 150ms cubic-bezier(0.2,0,0,1)', display: 'flex', alignItems: 'center', justifyContent: 'center',
  });
  thumb.append(...slot(p.thumbContent));
  // State layer: 40dp circle around the thumb.
  const layer = h('div');
  Object.assign(layer.style, { position: 'absolute', width: '40px', height: '40px', borderRadius: '50%', left: x + d / 2 - 20 + 'px', top: '-4px', color: on ? 'var(--c-primary)' : 'var(--c-onSurface)' });
  track.append(layer, thumb);
  if (!p.onCheckedChange) { track.setAttribute('role', 'switch'); track.setAttribute('aria-checked', String(on)); return mod(track, p); }
  clickable(layer, { onClick: () => p.onCheckedChange(!on), enabled, role: 'switch', checked: on, centered: true });
  track.addEventListener('click', () => enabled && p.onCheckedChange(!on));
  const outer = touchTarget(track, p);
  return outer;
}

/** Checkbox({ checked, onCheckedChange?: (bool)=>void|null, enabled?, colors?: {checkedColor, uncheckedColor, checkmarkColor} }) */
export function Checkbox(p) {
  const on = !!p.checked, enabled = p.enabled !== false;
  const c = p.colors || {};
  const box = h('div', { class: 'm3-checkbox' });
  const fill = enabled ? (c.checkedColor ?? 'var(--c-primary)') : alpha('var(--c-onSurface)', 0.38);
  const outline = enabled ? (c.uncheckedColor ?? 'var(--c-onSurfaceVariant)') : alpha('var(--c-onSurface)', 0.38);
  // Checkbox.kt: 20dp canvas (CheckboxSize), 2dp stroke, 2dp corner; check through (0.2,0.5)(0.4,0.7)(0.8,0.3).
  const svg = h('svg', { attrs: { viewBox: '0 0 20 20', width: 20, height: 20 } });
  svg.style.cssText = 'display:block;overflow:visible';
  if (on) {
    svg.append(h('svg:rect', { x: 0, y: 0, width: 20, height: 20, rx: 2, style: { fill } }));
    svg.append(h('svg:path', { d: 'M4 10 L8 14 L16 6', fill: 'none', style: { stroke: enabled ? (c.checkmarkColor ?? 'var(--c-onPrimary)') : 'var(--c-surface)' }, 'stroke-width': 2, 'stroke-linecap': 'square' }));
  } else {
    svg.append(h('svg:rect', { x: 1, y: 1, width: 18, height: 18, rx: 1, fill: 'none', style: { stroke: outline }, 'stroke-width': 2 }));
  }
  Object.assign(box.style, { width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', overflow: 'hidden', isolation: 'isolate', color: on ? 'var(--c-primary)' : 'var(--c-onSurface)' });
  box.append(svg);
  if (!p.onCheckedChange) {
    const plain = h('div', { style: { padding: '2px' }, role: 'checkbox', 'aria-checked': String(on) }, svg);
    return mod(plain, p);
  }
  clickable(box, { onClick: () => p.onCheckedChange(!on), enabled, role: 'checkbox', checked: on, centered: true });
  return touchTarget(box, p);
}

/** RadioButton({ selected, onClick?: fn|null, enabled?, colors?: {selectedColor, unselectedColor} }) */
export function RadioButton(p) {
  const sel = !!p.selected, enabled = p.enabled !== false;
  const c = p.colors || {};
  const color = !enabled ? alpha('var(--c-onSurface)', 0.38) : sel ? (c.selectedColor ?? 'var(--c-primary)') : (c.unselectedColor ?? 'var(--c-onSurfaceVariant)');
  const svg = h('svg', { attrs: { viewBox: '0 0 20 20', width: 20, height: 20 } });
  svg.append(h('svg:circle', { cx: 10, cy: 10, r: 9, fill: 'none', style: { stroke: color }, 'stroke-width': 2 }));
  if (sel) svg.append(h('svg:circle', { cx: 10, cy: 10, r: 5, style: { fill: color } }));
  const box = h('div', { class: 'm3-radio' });
  Object.assign(box.style, { width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', overflow: 'hidden', isolation: 'isolate', color: sel ? (c.selectedColor ?? 'var(--c-primary)') : 'var(--c-onSurface)' });
  box.append(svg);
  if (!p.onClick) { const plain = h('div', { style: { padding: '2px' }, role: 'radio', 'aria-checked': String(sel) }, svg); return mod(plain, p); }
  clickable(box, { onClick: p.onClick, enabled, role: 'radio', checked: sel, centered: true });
  return touchTarget(box, p);
}

// Slider ------------------------------------------------------------------------------------
/**
 * Slider({ value, onValueChange(v), valueRange=[0,1], steps=0, enabled?, onValueChangeFinished?,
 *   colors?: {thumbColor, activeTrackColor, inactiveTrackColor, activeTickColor, inactiveTickColor} })
 * M3 1.3 visuals: 16dp track, 4×44dp handle (2dp while dragged), 6dp thumb gap, 4dp stop/tick dots. Fills width.
 */
export function Slider(p) {
  const [min, max] = p.valueRange || [0, 1];
  const steps = p.steps || 0;
  const enabled = p.enabled !== false;
  const c = p.colors || {};
  const thumbColor = enabled ? (c.thumbColor ?? 'var(--c-primary)') : alpha('var(--c-onSurface)', 0.38);
  const activeTrack = enabled ? (c.activeTrackColor ?? 'var(--c-primary)') : alpha('var(--c-onSurface)', 0.38);
  const inactiveTrack = enabled ? (c.inactiveTrackColor ?? 'var(--c-secondaryContainer)') : alpha('var(--c-onSurface)', 0.12);
  const activeTick = c.activeTickColor ?? (enabled ? 'var(--c-secondaryContainer)' : alpha('var(--c-onSurface)', 0.38));
  const inactiveTick = c.inactiveTickColor ?? (enabled ? 'var(--c-primary)' : alpha('var(--c-onSurface)', 0.38));
  const root = h('div', { class: 'm3-slider', role: 'slider', tabIndex: enabled ? 0 : -1 });
  Object.assign(root.style, { position: 'relative', height: '48px', width: '100%', touchAction: 'none', cursor: enabled ? 'pointer' : 'default' });
  root.setAttribute('aria-valuemin', min); root.setAttribute('aria-valuemax', max); root.setAttribute('aria-valuenow', p.value);
  const svg = h('svg', { attrs: { height: 48 } });
  svg.style.cssText = 'position:absolute;inset:0;width:100%;height:48px;overflow:visible';
  root.append(svg);
  const snap = (v) => {
    v = Math.min(max, Math.max(min, v));
    if (steps > 0) { const n = steps + 1; const f = Math.round(((v - min) / (max - min)) * n) / n; v = min + f * (max - min); }
    return v;
  };
  let pressed = false;
  const draw = (value) => {
    const W = root.clientWidth || root.getBoundingClientRect().width;
    if (!W) return;
    const thumbW = pressed ? 2 : 4;
    const trackStart = 2, trackEnd = W - 2, tw = trackEnd - trackStart; // track inset by ThumbWidth/2
    const frac = max > min ? (value - min) / (max - min) : 0;
    const tx = trackStart + tw * frac;
    const H = 16, y0 = 24 - H / 2, outer = H / 2, inside = 2, gap = thumbW / 2 + 6;
    const rr = (x0, x1, rs, re, fillc) => {
      if (x1 - x0 <= 0) return '';
      const w = x1 - x0; rs = Math.min(rs, w / 2); re = Math.min(re, w / 2);
      const d = `M${x0 + rs},${y0} H${x1 - re} A${re},${re} 0 0 1 ${x1},${y0 + re} V${y0 + H - re} A${re},${re} 0 0 1 ${x1 - re},${y0 + H} H${x0 + rs} A${rs},${rs} 0 0 1 ${x0},${y0 + H - rs} V${y0 + rs} A${rs},${rs} 0 0 1 ${x0 + rs},${y0} Z`;
      return `<path d="${d}" style="fill:${fillc}"/>`;
    };
    let out = '';
    if (tx < trackEnd - gap - outer) {
      out += rr(trackStart + (tx - trackStart) + gap, trackEnd, inside, outer, inactiveTrack);
      out += `<circle cx="${trackEnd - outer}" cy="24" r="2" style="fill:${activeTrack}"/>`;
    }
    if (tx - gap - trackStart > outer) out += rr(trackStart, tx - gap, outer, inside, activeTrack);
    if (steps > 0) {
      const n = steps + 2;
      for (let i = 0; i < n - 1; i++) {
        const f = i / (n - 1);
        const cxp = (trackStart + outer) + f * ((trackEnd - outer) - (trackStart + outer));
        if (Math.abs(cxp - tx) <= gap) continue;
        out += `<circle cx="${cxp}" cy="24" r="2" style="fill:${f > frac ? inactiveTick : activeTick}"/>`;
      }
    }
    out += `<rect x="${tx - thumbW / 2}" y="2" width="${thumbW}" height="44" rx="${thumbW / 2}" style="fill:${thumbColor}"/>`;
    svg.innerHTML = out;
  };
  let current = p.value;
  const ro = new ResizeObserver(() => draw(current));
  ro.observe(root);
  queueMicrotask(() => draw(current));
  if (enabled) {
    const valueAt = (clientX, rect) => {
      const f = (clientX - rect.left - 2) / (rect.width - 4);
      return snap(min + Math.min(1, Math.max(0, f)) * (max - min));
    };
    root.addEventListener('pointerdown', (e) => {
      if (e.button !== 0) return;
      e.preventDefault();
      const rect = root.getBoundingClientRect();
      pressed = true;
      const onValueChange = p.onValueChange, finished = p.onValueChangeFinished;
      const move = (ev) => { current = valueAt(ev.clientX, rect); draw(current); onValueChange?.(current); };
      const up = () => {
        pressed = false; draw(current);
        window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); window.removeEventListener('pointercancel', up);
        finished?.();
      };
      window.addEventListener('pointermove', move); window.addEventListener('pointerup', up); window.addEventListener('pointercancel', up);
      move(e);
    });
    root.addEventListener('keydown', (e) => {
      const step = steps > 0 ? (max - min) / (steps + 1) : (max - min) / 100;
      if (e.key === 'ArrowRight' || e.key === 'ArrowUp') { current = snap(current + step); p.onValueChange?.(current); draw(current); e.preventDefault(); }
      if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') { current = snap(current - step); p.onValueChange?.(current); draw(current); e.preventDefault(); }
    });
  }
  return mod(root, p);
}

// Progress ----------------------------------------------------------------------------------
/**
 * LinearProgressIndicator({ progress?: number | ()=>number (omit = indeterminate), color=primary,
 *   trackColor=secondaryContainer, strokeCap='Round'|'Butt', gapSize=4 })  default 240×4dp; size via css/class.
 *   Determinate result has `.setProgress(p)` (redraw in place).
 */
export function LinearProgressIndicator(p = {}) {
  const color = p.color || 'var(--c-primary)';
  const track = p.trackColor || 'var(--c-secondaryContainer)';
  const round = (p.strokeCap || 'Round') === 'Round';
  const gap = p.gapSize ?? 4;
  const el = h('div', { class: 'm3-linear-progress', role: 'progressbar' });
  Object.assign(el.style, { width: '240px', height: 'var(--size-progressHeight)', position: 'relative', flex: 'none' });
  mod(el, p);
  const svg = h('svg');
  svg.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;overflow:visible;display:block';
  el.append(svg);
  const line = (W, H, s, e, col) => {
    if (Math.abs(e - s) <= 0) return '';
    let x0 = s * W, x1 = e * W;
    if (!round || H > W) return `<line x1="${x0}" y1="${H / 2}" x2="${x1}" y2="${H / 2}" style="stroke:${col}" stroke-width="${H}"/>`;
    const o = H / 2; x0 = Math.min(Math.max(x0, o), W - o); x1 = Math.min(Math.max(x1, o), W - o);
    return `<line x1="${x0}" y1="${H / 2}" x2="${x1}" y2="${H / 2}" style="stroke:${col}" stroke-width="${H}" stroke-linecap="round"/>`;
  };
  if (p.progress == null) {
    // Indeterminate: two lines with the Compose keyframe timings (1800ms cycle).
    let raf;
    const ease = cubic(0.2, 0, 0.8, 1);
    const seg = (t, delay, dur) => ease(Math.min(1, Math.max(0, (t - delay) / dur)));
    const tick = (ts) => {
      const W = el.clientWidth, H = el.clientHeight; if (!W) { raf = requestAnimationFrame(tick); return; }
      const t = ts % 1800;
      const h1 = seg(t, 0, 750), t1 = seg(t, 333, 850), h2 = seg(t, 1000, 567), t2 = seg(t, 1267, 533);
      let s = line(W, H, 0, 1, track);
      s = `<line x1="0" y1="${H / 2}" x2="${W}" y2="${H / 2}" style="stroke:${track}" stroke-width="${H}" ${round ? 'stroke-linecap="round"' : ''}/>`;
      if (h1 - t1 > 0) s += line(W, H, t1, h1, color);
      if (h2 - t2 > 0) s += line(W, H, t2, h2, color);
      svg.innerHTML = s;
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    if (currentScope()) onDispose(() => cancelAnimationFrame(raf));
    return el;
  }
  let progress = p.progress;
  const draw = () => {
    const W = el.clientWidth, H = el.clientHeight; if (!W || !H) return;
    const prog = Math.min(1, Math.max(0, Number(typeof progress === 'function' ? progress() : progress) || 0));
    const adjGap = !round || H > W ? gap : gap + H;
    const gapF = adjGap / W;
    let s = '';
    const trackStart = prog + Math.min(prog, gapF);
    if (trackStart <= 1) s += line(W, H, trackStart, 1, track);
    s += line(W, H, 0, prog, color);
    // stop indicator
    const stop = Math.min(4, H), off = (H - stop) / 2;
    s += round ? `<circle cx="${W - stop / 2 - off}" cy="${H / 2}" r="${stop / 2}" style="fill:${color}"/>` : `<rect x="${W - stop - off}" y="${(H - stop) / 2}" width="${stop}" height="${stop}" style="fill:${color}"/>`;
    svg.innerHTML = s;
  };
  new ResizeObserver(draw).observe(el);
  queueMicrotask(draw);
  el.setProgress = (v) => { if (v === progress && typeof v !== 'function') return; progress = v; draw(); };
  return el;
}

function cubic(x1, y1, x2, y2) {
  const ax = 3 * x1 - 3 * x2 + 1, bx = 3 * x2 - 6 * x1, cx_ = 3 * x1;
  const ay = 3 * y1 - 3 * y2 + 1, by = 3 * y2 - 6 * y1, cy = 3 * y1;
  const sx = (t) => ((ax * t + bx) * t + cx_) * t, sy = (t) => ((ay * t + by) * t + cy) * t;
  const dx = (t) => (3 * ax * t + 2 * bx) * t + cx_;
  return (x) => {
    if (x <= 0) return 0; if (x >= 1) return 1;
    let t = x;
    for (let i = 0; i < 8; i++) { const e = sx(t) - x; const d = dx(t); if (Math.abs(e) < 1e-5 || !d) break; t -= e / d; }
    return sy(Math.min(1, Math.max(0, t)));
  };
}

/** A stroke width: px number, '4px', or a 'var(--token)' resolved against `el` at draw time. */
function strokePx(el, w) {
  if (typeof w === 'number') return w;
  const m = /^var\((--[\w-]+)\)$/.exec(String(w));
  if (m) return parseFloat(getComputedStyle(el).getPropertyValue(m[1])) || 0;
  return parseFloat(w) || 0;
}
/**
 * CircularProgressIndicator({ progress?: number|()=>number (omit = indeterminate), color=primary,
 *   strokeWidth=4 (px number or 'var(--token)'), trackColor (determinate: secondaryContainer, indeterminate:
 *   transparent), strokeCap='Round'|'Butt', gapSize=4 })
 * Default 40dp; size via css {width,height} or class.
 * Determinate result → Element with `.setProgress(p)` and `.setColor(c)`: redraws in place (Compose reads the
 * progress lambda in the draw phase; use these to animate without rebuilding the node).
 */
export function CircularProgressIndicator(p = {}) {
  let color = p.color || 'var(--c-primary)';
  const round = (p.strokeCap || 'Round') === 'Round';
  const el = h('div', { class: 'm3-circular-progress', role: 'progressbar' });
  Object.assign(el.style, { width: '40px', height: '40px', position: 'relative', flex: 'none' });
  mod(el, p);
  const svg = h('svg');
  svg.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;overflow:visible;display:block';
  el.append(svg);
  let sw = typeof p.strokeWidth === 'number' ? p.strokeWidth : 4;
  const arc = (S, start, sweep, col) => {
    if (sweep <= 0) return '';
    const r = (S - sw) / 2, c = S / 2;
    if (sweep >= 359.999) return `<circle cx="${c}" cy="${c}" r="${r}" fill="none" style="stroke:${col}" stroke-width="${sw}"/>`;
    const a0 = (start * Math.PI) / 180, a1 = ((start + sweep) * Math.PI) / 180;
    const x0 = c + r * Math.cos(a0), y0 = c + r * Math.sin(a0), x1 = c + r * Math.cos(a1), y1 = c + r * Math.sin(a1);
    return `<path d="M${x0},${y0} A${r},${r} 0 ${sweep > 180 ? 1 : 0} 1 ${x1},${y1}" fill="none" style="stroke:${col}" stroke-width="${sw}" ${round ? 'stroke-linecap="round"' : ''}/>`;
  };
  if (p.progress == null) {
    const track = p.trackColor || 'transparent';
    const ease = cubic(0.4, 0, 0.2, 1);
    let raf;
    const tick = (ts) => {
      const S = el.clientWidth; if (!S) { raf = requestAnimationFrame(tick); return; }
      sw = strokePx(el, p.strokeWidth ?? 4);
      const rot = Math.floor(((ts % 6660) / 6660) * 5);
      const base = 286 * ((ts % 1332) / 1332);
      const k = ts % 1332;
      const end = k < 666 ? 290 * ease(k / 666) : 290;
      const start = k < 666 ? 0 : 290 * ease((k - 666) / 666);
      const offset = -90 + ((rot * 216) % 360) + base;
      const capOff = round ? (180 / Math.PI) * (sw / (S / 2)) / 2 : 0;
      let s = track !== 'transparent' ? arc(S, 0, 360, track) : '';
      s += arc(S, start + offset + capOff, Math.max(Math.abs(end - start), 0.1), color);
      svg.innerHTML = s;
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    if (currentScope()) onDispose(() => cancelAnimationFrame(raf));
    el.setColor = (c) => { color = c; };
    return el;
  }
  const track = p.trackColor || 'var(--c-secondaryContainer)';
  let progress = p.progress;
  const draw = () => {
    const S = el.clientWidth; if (!S) return;
    sw = strokePx(el, p.strokeWidth ?? 4);
    const prog = Math.min(1, Math.max(0, Number(typeof progress === 'function' ? progress() : progress) || 0));
    const sweep = prog * 360;
    const adj = round ? (p.gapSize ?? 4) + sw : (p.gapSize ?? 4);
    const gapSweep = (adj / (Math.PI * S)) * 360;
    const g = Math.min(sweep, gapSweep);
    svg.innerHTML = arc(S, 270 + sweep + g, 360 - sweep - g * 2, track) + arc(S, 270, sweep, color);
    el.setAttribute('aria-valuenow', String(prog));
  };
  new ResizeObserver(draw).observe(el);
  queueMicrotask(draw);
  el.setProgress = (v) => { if (v === progress && typeof v !== 'function') return; progress = v; draw(); };
  el.setColor = (c) => { if (c === color) return; color = c; draw(); };
  return el;
}

// =============================================================================================
// Overlays (Dialog, AlertDialog, ModalBottomSheet, DropdownMenu)
// =============================================================================================
/**
 * Internal: mount an overlay node for the current render pass.
 * build(animateIn) → { el, enter(), exit(done) }
 */
const keyedOverlays = new Map();
function mountOverlay(build, onBack, key) {
  const scope = currentScope();
  const slots = scope?.overlaySlots;
  const index = scope ? scope.overlayIndex++ : -1;
  let prev = key != null ? keyedOverlays.get(key) : null;
  if (!prev || !prev.el.isConnected || prev.replaced || prev.closing) prev = slots?.get(index);
  const live = !!(prev && prev.el.isConnected && !prev.replaced && !prev.closing);
  const animateIn = !live;
  const rec = build(animateIn);
  let snap = null;
  if (live) {
    snap = snapshot(prev.el);
    prev.el.replaceWith(rec.el);
    prev.replaced = true;
    prev.back?.remove();
  } else overlayRoot().appendChild(rec.el);
  if (snap) restore(rec.el, snap);
  rec.enter?.(animateIn);
  if (slots) slots.set(index, rec);
  if (key != null) keyedOverlays.set(key, rec);
  if (onBack) rec.back = BackHandler(true, onBack);
  const remove = () => {
    if (rec.replaced || rec.closing) return;
    rec.closing = true;
    rec.back?.remove();
    if (slots && slots.get(index) === rec) slots.delete(index);
    if (key != null && keyedOverlays.get(key) === rec) keyedOverlays.delete(key);
    if (rec.exit) rec.exit(() => rec.el.remove()); else rec.el.remove();
  };
  if (scope) onDispose(remove);
  rec.dismissOverlay = remove;
  return rec;
}
/** Placeholder left in the composition tree where an overlay was declared. */
function anchor(rec) {
  const a = document.createComment('overlay');
  a.dismissOverlay = rec.dismissOverlay;
  return a;
}

const DIALOG_DIM = 'rgba(0,0,0,0.6)'; // platform dialog window dim (Theme.Material backgroundDimAmount)
function windowLayer({ onDismissRequest, dismissOnClickOutside = true, scrim = DIALOG_DIM, align = 'center' }) {
  const layer = h('div', { class: 'overlay-layer' });
  Object.assign(layer.style, { position: 'absolute', inset: '0', display: 'flex', alignItems: align === 'bottom' ? 'flex-end' : 'center', justifyContent: 'center', zIndex: '10', pointerEvents: 'auto' });
  const dim = h('div');
  Object.assign(dim.style, { position: 'absolute', inset: '0', background: scrim });
  layer.append(dim);
  if (dismissOnClickOutside && onDismissRequest) {
    dim.addEventListener('click', () => onDismissRequest());
  }
  return { layer, dim };
}

/**
 * Dialog({ onDismissRequest, properties?: {dismissOnBackPress=true, dismissOnClickOutside=true,
 *   usePlatformDefaultWidth=true}, children })  — compose.ui.window.Dialog: a dimmed window with your content.
 */
export function Dialog(p, ...kids) {
  const props = p.properties || {};
  const rec = mountOverlay((animateIn) => {
    const { layer, dim } = windowLayer({ onDismissRequest: p.onDismissRequest, dismissOnClickOutside: props.dismissOnClickOutside !== false });
    const box = h('div', { class: 'dialog-window', role: 'dialog', 'aria-modal': 'true' });
    Object.assign(box.style, {
      position: 'relative', display: 'flex', justifyContent: 'center', maxHeight: 'calc(100% - 48px)',
      maxWidth: props.usePlatformDefaultWidth === false ? '100%' : 'calc(100% - 48px)',
      width: props.usePlatformDefaultWidth === false ? '100%' : '',
    });
    box.append(...slot(p.children ?? kids));
    layer.append(box);
    return {
      el: layer,
      enter(animate) {
        if (!animate) return;
        dim.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 150, easing: 'linear' });
        box.animate([{ opacity: 0, transform: 'scale(0.9)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 150, easing: 'cubic-bezier(0,0,0.2,1)' });
      },
      exit(done) {
        layer.style.pointerEvents = 'none';
        dim.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 75, easing: 'linear', fill: 'forwards' });
        box.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 75, easing: 'linear', fill: 'forwards' }).onfinish = done;
      },
    };
  }, props.dismissOnBackPress === false ? () => {} : p.onDismissRequest, p.key);
  return anchor(rec);
}

/**
 * AlertDialog({ onDismissRequest, confirmButton, dismissButton?, icon?, title?, text?, shape=extraLarge(28),
 *   containerColor=surfaceContainerHigh, iconContentColor=secondary, titleContentColor=onSurface,
 *   textContentColor=onSurfaceVariant, tonalElevation=0, properties? })
 */
export function AlertDialog(p) {
  const container = p.containerColor || 'var(--c-surfaceContainerHigh)';
  const card = h('div', { class: 'm3-alert-dialog' });
  Object.assign(card.style, {
    minWidth: '280px', maxWidth: '560px', width: '100%', background: tonal(container, p.tonalElevation), borderRadius: p.shape || 'var(--radius-extraLarge)',
    padding: '24px', display: 'flex', flexDirection: 'column', boxShadow: 'var(--shadow-6)', color: 'var(--c-onSurface)', overflow: 'hidden',
  });
  const iconS = slot(p.icon);
  if (iconS.length) card.append(h('div', { style: { color: p.iconContentColor || 'var(--c-secondary)', paddingBottom: '16px', alignSelf: 'center', display: 'flex' } }, ...iconS));
  const titleS = slot(p.title);
  if (titleS.length) card.append(h('div', { class: 't-headlineSmall', style: { color: p.titleContentColor || 'var(--c-onSurface)', paddingBottom: '16px', alignSelf: iconS.length ? 'center' : 'flex-start', textAlign: iconS.length ? 'center' : 'start' } }, ...titleS));
  const textS = slot(p.text);
  if (textS.length) card.append(h('div', { class: 't-bodyMedium scroll-y', style: { color: p.textContentColor || 'var(--c-onSurfaceVariant)', paddingBottom: '24px', flex: '1 1 auto', minHeight: '0', alignSelf: 'stretch' } }, ...textS));
  const buttons = h('div', { style: { display: 'flex', flexWrap: 'wrap', justifyContent: 'flex-end', alignItems: 'center', columnGap: '8px', rowGap: '12px', alignSelf: 'stretch', color: 'var(--c-primary)' } });
  buttons.append(...slot(p.dismissButton), ...slot(p.confirmButton));
  card.append(buttons);
  // Touch-target wrappers around the 40dp buttons would add 8dp: AlertDialog's FlowRow keeps their 48dp layout.
  return Dialog({ onDismissRequest: p.onDismissRequest, properties: p.properties, key: p.key, children: card });
}

/**
 * ModalBottomSheet({ onDismissRequest, sheetState?: {skipPartiallyExpanded}, containerColor=surfaceContainerLow,
 *   contentColor, shape=extraLarge top, scrimColor=scrim@0.32, dragHandle=true|null|Node, tonalElevation=0, children })
 * Always opens fully expanded (the app only uses skipPartiallyExpanded = true). Drag the sheet down to dismiss.
 * Content gets bottom inset padding = gesture bar (BottomSheetDefaults.windowInsets).
 */
export function ModalBottomSheet(p, ...kids) {
  const rec = mountOverlay((animateIn) => {
    const { layer, dim } = windowLayer({ onDismissRequest: p.onDismissRequest, scrim: p.scrimColor || alpha('var(--c-scrim)', 0.32), align: 'bottom' });
    const container = p.containerColor || 'var(--c-surfaceContainerLow)';
    const sheet = h('div', { class: 'm3-bottom-sheet', role: 'dialog', 'aria-modal': 'true' });
    Object.assign(sheet.style, {
      position: 'relative', width: '100%', maxWidth: '640px', maxHeight: 'calc(100% - var(--status-bar-height, 24px))',
      background: tonal(container, p.tonalElevation), color: p.contentColor || contentColorFor(container) || 'var(--c-onSurface)',
      borderRadius: p.shape || 'var(--radius-extraLarge) var(--radius-extraLarge) 0 0', display: 'flex', flexDirection: 'column',
      paddingBottom: 'var(--gesture-bar-height, 24px)', touchAction: 'none',
    });
    if (p.dragHandle !== null) {
      const handle = p.dragHandle && p.dragHandle !== true ? p.dragHandle : h('div', { style: { padding: '22px 0', alignSelf: 'center' } },
        h('div', { style: { width: '32px', height: '4px', borderRadius: '28px', background: 'var(--c-onSurfaceVariant)' } }));
      sheet.append(handle);
    }
    const body = h('div', { class: 'col', style: { minHeight: '0', flex: '1 1 auto', touchAction: 'pan-y' } }, ...slot(p.children ?? kids));
    sheet.append(body);
    layer.append(sheet);
    // Drag to dismiss
    let startY = null, dy = 0;
    sheet.addEventListener('pointerdown', (e) => {
      const scroller = e.target.closest('.scroll-y');
      if (scroller && scroller.scrollTop > 0) return;
      startY = e.clientY; dy = 0;
    });
    const move = (e) => {
      if (startY == null) return;
      dy = Math.max(0, e.clientY - startY);
      if (dy > 4) sheet.style.transform = `translateY(${dy}px)`;
    };
    const up = () => {
      if (startY == null) return;
      const h0 = sheet.offsetHeight; startY = null;
      if (dy > h0 * 0.5 || dy > 120) { p.onDismissRequest?.(); }
      else if (dy > 0) sheet.animate([{ transform: `translateY(${dy}px)` }, { transform: 'translateY(0)' }], { duration: 200, easing: 'cubic-bezier(0.2,0,0,1)' });
      sheet.style.transform = '';
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
    try { onDispose(() => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); }); } catch {}
    return {
      el: layer,
      enter(animate) {
        if (!animate) return;
        dim.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 250, easing: 'linear' });
        sheet.animate([{ transform: 'translateY(100%)' }, { transform: 'translateY(0)' }], { duration: 350, easing: 'cubic-bezier(0.05,0.7,0.1,1)' });
      },
      exit(done) {
        layer.style.pointerEvents = 'none';
        dim.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 200, easing: 'linear', fill: 'forwards' });
        sheet.animate([{ transform: getComputedStyle(sheet).transform === 'none' ? 'translateY(0)' : getComputedStyle(sheet).transform }, { transform: 'translateY(100%)' }], { duration: 200, easing: 'cubic-bezier(0.3,0,0.8,0.15)', fill: 'forwards' }).onfinish = done;
      },
    };
  }, p.onDismissRequest, p.key);
  return anchor(rec);
}
export const rememberModalBottomSheetState = (o = {}) => ({ skipPartiallyExpanded: !!o.skipPartiallyExpanded });

/**
 * DropdownMenu({ expanded, onDismissRequest, offset?: {x, y}, containerColor=surfaceContainer, children })
 * Place it next to its anchor inside the same parent (like Compose: positioned against the parent Box).
 * Returns an invisible marker element; the menu itself goes to the overlay layer.
 */
export function DropdownMenu(p, ...kids) {
  const marker = h('span', { style: { position: 'absolute', width: '0', height: '0', overflow: 'hidden' } });
  if (!p.expanded) return marker;
  const rec = mountOverlay((animateIn) => {
    const layer = h('div', { class: 'overlay-layer' });
    Object.assign(layer.style, { position: 'absolute', inset: '0', zIndex: '11' });
    layer.addEventListener('pointerdown', (e) => { if (e.target === layer) { e.preventDefault(); p.onDismissRequest?.(); } });
    const menu = h('div', { class: 'm3-menu', role: 'menu' });
    Object.assign(menu.style, {
      position: 'absolute', minWidth: '112px', maxWidth: '280px', padding: '8px 0', borderRadius: 'var(--radius-extraSmall)',
      background: p.containerColor || 'var(--c-surfaceContainer)', color: 'var(--c-onSurface)', boxShadow: 'var(--shadow-3)',
      overflowY: 'auto', visibility: 'hidden', transformOrigin: 'top left',
    });
    menu.append(...slot(p.children ?? kids));
    layer.append(menu);
    const place = () => {
      const parent = marker.parentElement; if (!parent) return;
      const root = overlayRoot().getBoundingClientRect();
      const a = parent.getBoundingClientRect();
      const mw = menu.offsetWidth, mh = menu.offsetHeight;
      const margin = 48; // MenuVerticalMargin
      let x = a.left - root.left + (p.offset?.x || 0);
      if (x + mw > root.width) x = a.right - root.left - mw;
      x = Math.max(0, Math.min(x, root.width - mw));
      let y = a.bottom - root.top + (p.offset?.y || 0);
      let origin = 'top';
      if (y + mh > root.height - margin) { y = a.top - root.top - mh; origin = 'bottom'; }
      y = Math.max(margin, Math.min(y, root.height - margin - mh));
      menu.style.left = x + 'px'; menu.style.top = y + 'px'; menu.style.maxHeight = root.height - margin * 2 + 'px';
      menu.style.transformOrigin = `${x === a.left - root.left ? 'left' : 'right'} ${origin}`;
      menu.style.visibility = 'visible';
    };
    return {
      el: layer,
      enter(animate) {
        requestAnimationFrame(() => {
          place();
          if (animate) menu.animate([{ opacity: 0, transform: 'scale(0.8)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 120, easing: 'cubic-bezier(0,0,0.2,1)' });
        });
      },
      exit(done) { menu.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 75, easing: 'linear', fill: 'forwards' }).onfinish = done; },
      place,
    };
  }, p.onDismissRequest, p.key);
  return marker;
}
/** DropdownMenuItem({ text, onClick, leadingIcon?, trailingIcon?, enabled?, colors?: {textColor, leadingIconColor} }) 48dp row. */
export function DropdownMenuItem(p) {
  const enabled = p.enabled !== false;
  const row = h('div', { class: 'm3-menu-item t-labelLarge' });
  Object.assign(row.style, { display: 'flex', alignItems: 'center', minHeight: '48px', padding: '0 12px', gap: '12px', minWidth: '112px', maxWidth: '280px', position: 'relative', overflow: 'hidden', isolation: 'isolate', color: enabled ? (p.colors?.textColor || 'var(--c-onSurface)') : alpha('var(--c-onSurface)', 0.38) });
  const lead = slot(p.leadingIcon);
  if (lead.length) row.append(h('div', { style: { display: 'flex', color: p.colors?.leadingIconColor || 'var(--c-onSurfaceVariant)' } }, ...lead));
  row.append(h('div', { style: { flex: '1 1 auto' } }, ...slot(p.text)));
  const trail = slot(p.trailingIcon);
  if (trail.length) row.append(h('div', { style: { display: 'flex', color: 'var(--c-onSurfaceVariant)' } }, ...trail));
  clickable(row, { onClick: p.onClick, enabled, role: 'menuitem' });
  return mod(row, p);
}

// =============================================================================================
// Navigation bar, tabs, segmented buttons
// =============================================================================================
/** NavigationBar({ containerColor=surfaceContainer, contentColor, tonalElevation=0, windowInsets=true, children }) 80dp + gesture inset. */
export function NavigationBar(p, ...kids) {
  const bar = h('nav', { class: 'm3-navigation-bar' });
  Object.assign(bar.style, {
    background: p.containerColor || 'var(--c-surfaceContainer)', color: p.contentColor || 'var(--c-onSurface)',
    paddingBottom: p.windowInsets === false ? '0' : WindowInsets.navigationBars, width: '100%', flex: 'none',
  });
  const row = h('div', { role: 'tablist' });
  Object.assign(row.style, { display: 'flex', alignItems: 'center', gap: '8px', minHeight: '80px' });
  row.append(...slot(p.children ?? kids));
  bar.append(row);
  return mod(bar, p);
}
/**
 * NavigationBarItem({ selected, onClick, icon, label?, enabled?, alwaysShowLabel=true,
 *   colors?: {selectedIconColor, selectedTextColor, indicatorColor, unselectedIconColor, unselectedTextColor} })
 */
export function NavigationBarItem(p) {
  const sel = !!p.selected;
  const c = p.colors || {};
  const item = h('div', { class: 'm3-nav-item' });
  Object.assign(item.style, { flex: '1 1 0', minWidth: '0', height: '80px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', userSelect: 'none' });
  const ind = h('div');
  Object.assign(ind.style, { width: '64px', height: '32px', borderRadius: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', isolation: 'isolate', overflow: 'hidden', color: sel ? (c.selectedIconColor ?? 'var(--c-onSecondaryContainer)') : (c.unselectedIconColor ?? 'var(--c-onSurfaceVariant)') });
  const pill = h('div');
  Object.assign(pill.style, { position: 'absolute', inset: '0', borderRadius: '16px', background: c.indicatorColor ?? 'var(--c-secondaryContainer)', zIndex: '-2', transform: sel ? 'scaleX(1)' : 'scaleX(0)', opacity: sel ? '1' : '0' });
  ind.append(pill, ...slot(p.icon));
  item.append(ind);
  if (p.label != null && (p.alwaysShowLabel !== false || sel)) {
    const lbl = h('div', { class: 't-labelMedium', style: { marginTop: '4px', color: sel ? (c.selectedTextColor ?? 'var(--c-onSurface)') : (c.unselectedTextColor ?? 'var(--c-onSurfaceVariant)'), whiteSpace: 'nowrap' } }, ...slot(p.label));
    item.append(lbl);
  }
  attachRipple(ind, { bounded: true });
  item.setAttribute('role', 'tab'); item.setAttribute('aria-selected', String(sel)); item.tabIndex = 0;
  // Ripple is drawn in the indicator; the whole item is the touch area.
  item.addEventListener('pointerdown', (e) => { if (e.target !== ind && !ind.contains(e.target)) ind.dispatchEvent(new PointerEvent('pointerdown', e)); });
  if (p.enabled !== false) {
    item.addEventListener('click', () => p.onClick?.());
    item.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); p.onClick?.(); } });
  }
  return mod(item, p);
}

/**
 * SecondaryTabRow({ selectedTabIndex, containerColor=surface, contentColor=onSurface, indicator?: (tabRect)=>Node |
 *   {height, color}, divider?: Node|fn, children: Tab[] })  Fixed tabs, equal widths. PrimaryTabRow/TabRow alias it
 *   (Primary indicator: 3dp rounded, content width; Secondary: full tab width).
 */
function tabRowImpl(p, kids, primary) {
  const row = h('div', { class: 'm3-tab-row', role: 'tablist' });
  Object.assign(row.style, { position: 'relative', background: p.containerColor || 'var(--c-surface)', color: p.contentColor || (primary ? 'var(--c-primary)' : 'var(--c-onSurface)'), width: '100%', flex: 'none' });
  const tabs = h('div', { style: { display: 'flex', alignItems: 'stretch', minHeight: '48px' } });
  const kidsArr = slot(p.children ?? kids);
  kidsArr.forEach((t) => { t.style.flex = '1 1 0'; t.style.minWidth = '0'; });
  tabs.append(...kidsArr);
  row.append(tabs);
  const dividerNode = p.divider === undefined ? HorizontalDivider({ color: 'var(--c-surfaceVariant)' }) : slot(p.divider)[0];
  if (dividerNode) { dividerNode.style.position = 'absolute'; dividerNode.style.left = '0'; dividerNode.style.right = '0'; dividerNode.style.bottom = '0'; row.append(dividerNode); }
  const spec = typeof p.indicator === 'object' && p.indicator && !(p.indicator instanceof Node) ? p.indicator : {};
  const ind = h('div', { class: 'm3-tab-indicator' });
  Object.assign(ind.style, {
    position: 'absolute', bottom: '0', left: '0', height: px(spec.height ?? (primary ? 3 : 2)),
    background: spec.color || (primary ? 'var(--c-primary)' : 'var(--c-primary)'),
    borderRadius: primary ? '3px 3px 0 0' : '0', transition: 'transform 250ms cubic-bezier(0.4,0,0.2,1), width 250ms cubic-bezier(0.4,0,0.2,1)',
    zIndex: '1',
  });
  row.append(ind);
  const scope = currentScope();
  const memo = scope ? remember('tabRow:lastIndex', () => null) : { value: null };
  const placeInd = (animate) => {
    const t = kidsArr[p.selectedTabIndex]; if (!t) { ind.style.display = 'none'; return; }
    let x = t.offsetLeft, w = t.offsetWidth;
    if (primary && spec.matchContentSize !== false) {
      const content = t.querySelector('.tab-content') || t; const cw = Math.max(24, content.scrollWidth); x += (w - cw) / 2; w = cw;
    }
    if (!animate) ind.style.transition = 'none';
    ind.style.width = w + 'px'; ind.style.transform = `translateX(${x}px)`;
    if (!animate) requestAnimationFrame(() => { ind.style.transition = 'transform 250ms cubic-bezier(0.4,0,0.2,1), width 250ms cubic-bezier(0.4,0,0.2,1)'; });
  };
  requestAnimationFrame(() => {
    const prevIdx = memo.value;
    if (prevIdx != null && prevIdx !== p.selectedTabIndex && kidsArr[prevIdx]) {
      const t = kidsArr[prevIdx]; ind.style.transition = 'none'; ind.style.width = t.offsetWidth + 'px'; ind.style.transform = `translateX(${t.offsetLeft}px)`;
      requestAnimationFrame(() => { ind.style.transition = ''; ind.style.transition = 'transform 250ms cubic-bezier(0.4,0,0.2,1), width 250ms cubic-bezier(0.4,0,0.2,1)'; placeInd(true); });
    } else placeInd(false);
    memo.value = p.selectedTabIndex;
  });
  new ResizeObserver(() => placeInd(false)).observe(row);
  return mod(row, p);
}
export const SecondaryTabRow = (p, ...k) => tabRowImpl(p, k, false);
export const PrimaryTabRow = (p, ...k) => tabRowImpl(p, k, true);
export const TabRow = PrimaryTabRow;
export const TabRowDefaults = { SecondaryIndicator: (o = {}) => ({ height: o.height ?? 2, color: o.color }), PrimaryIndicator: (o = {}) => ({ height: o.height ?? 3, color: o.color }) };

/**
 * Tab({ selected, onClick, text?, icon?, enabled?, selectedContentColor=LocalContentColor, unselectedContentColor=selected,
 *   css? })  48dp (text or icon only), 72dp (both); a css height overrides (e.g. Size.syllabusSubjectTabHeight).
 */
export function Tab(p) {
  const sel = !!p.selected;
  const color = sel ? (p.selectedContentColor || 'currentColor') : (p.unselectedContentColor || p.selectedContentColor || 'currentColor');
  const t = h('div', { class: 'm3-tab' });
  const textS = slot(p.text), iconS = slot(p.icon);
  const both = textS.length && iconS.length;
  Object.assign(t.style, { position: 'relative', overflow: 'hidden', isolation: 'isolate', color, height: both ? '72px' : '48px', padding: '0 16px', display: 'flex', alignItems: 'center', justifyContent: 'center' });
  const content = h('div', { class: 'tab-content t-titleSmall', style: { display: 'flex', flexDirection: 'column', alignItems: 'center', minWidth: '0', maxWidth: '100%', textAlign: 'center' } });
  if (both) {
    // TabBaselineLayout: text baseline 14dp above the bottom, icon bottom 20sp above that baseline.
    t.style.alignItems = 'stretch';
    content.style.position = 'relative'; content.style.width = '100%';
    const iconBox = h('div', { style: { position: 'absolute', left: '50%', transform: 'translateX(-50%)', bottom: 'calc(14px + 20px)', display: 'flex' } }, ...iconS);
    const textBox = h('div', { style: { position: 'absolute', left: '0', right: '0', bottom: '14px', display: 'flex', justifyContent: 'center' } });
    const baselineProbe = h('div', { style: { display: 'flex', alignItems: 'baseline' } }, ...textS, h('span', { style: { display: 'inline-block', width: '0', height: '0' } }));
    textBox.append(baselineProbe);
    content.append(iconBox, textBox);
    // The flex baseline sits at the probe's bottom (zero-size inline-block), i.e. the text baseline.
    baselineProbe.style.alignItems = 'baseline';
    baselineProbe.lastChild.style.verticalAlign = 'baseline';
    textBox.style.transform = 'translateY(var(--tab-baseline-descent, 4px))';
  } else content.append(...iconS, ...textS);
  t.append(content);
  clickable(t, { onClick: p.onClick, enabled: p.enabled, role: 'tab', selected: sel });
  return mod(t, p);
}

/** SingleChoiceSegmentedButtonRow({ children }) — 40dp tall row (48dp touch), segments share width, 1dp overlap. */
export function SingleChoiceSegmentedButtonRow(p = {}, ...kids) {
  const row = h('div', { class: 'm3-segmented-row', role: 'radiogroup' });
  Object.assign(row.style, { display: 'flex', alignItems: 'stretch', minHeight: '48px', paddingBlock: '4px', boxSizing: 'border-box', width: 'fit-content' });
  const items = slot(p.children ?? kids);
  items.forEach((s, i) => { s.style.flex = '1 1 0'; if (i > 0) s.style.marginLeft = '-1px'; });
  row.append(...items);
  return mod(row, p);
}
/** SegmentedButtonDefaults.itemShape(index, count) / .colors({...}) */
export const SegmentedButtonDefaults = {
  itemShape: ({ index, count }) => (count === 1 ? '9999px' : index === 0 ? '9999px 0 0 9999px' : index === count - 1 ? '0 9999px 9999px 0' : '0'),
  colors: (c = {}) => c,
  IconSize: '18px',
};
/**
 * SegmentedButton({ selected, onClick, shape, label|children, icon? (default: check when selected), enabled?,
 *   colors?: {activeContainerColor, activeContentColor, activeBorderColor, inactiveContainerColor, inactiveContentColor,
 *   inactiveBorderColor} })
 */
export function SegmentedButton(p, ...kids) {
  const sel = !!p.selected, enabled = p.enabled !== false;
  const c = p.colors || {};
  const b = h('div', { class: 'm3-segment t-labelLarge' });
  Object.assign(b.style, {
    minHeight: '40px', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '0 12px', gap: '8px',
    borderRadius: p.shape || '9999px', position: 'relative', overflow: 'hidden', isolation: 'isolate', whiteSpace: 'nowrap',
    background: sel ? (c.activeContainerColor ?? 'var(--c-secondaryContainer)') : (c.inactiveContainerColor ?? 'transparent'),
    color: !enabled ? alpha('var(--c-onSurface)', 0.38) : sel ? (c.activeContentColor ?? 'var(--c-onSecondaryContainer)') : (c.inactiveContentColor ?? 'var(--c-onSurface)'),
    zIndex: sel ? '1' : '0',
  });
  applyBorder(b, BorderStroke(1, sel ? (c.activeBorderColor ?? 'var(--c-outline)') : (c.inactiveBorderColor ?? 'var(--c-outline)')));
  const iconS = p.icon !== undefined ? slot(p.icon) : (sel ? [Icon({ imageVector: 'Filled.Check', size: '18px' })] : []);
  if (iconS.length) b.append(...iconS);
  b.append(...slot(p.label ?? p.children ?? kids));
  clickable(b, { onClick: p.onClick, enabled, role: 'radio', checked: sel });
  return mod(b, p);
}

// =============================================================================================
// Text field
// =============================================================================================
/**
 * OutlinedTextField({ value, onValueChange(str), label?, placeholder?, leadingIcon?, trailingIcon?, prefix?, suffix?,
 *   supportingText?, isError?, enabled?, readOnly?, singleLine?, maxLines?, minLines?, shape=extraSmall(4),
 *   keyboardOptions?: {keyboardType: 'Number'|'Text'|…, imeAction}, keyboardActions?: {onDone}, colors?: {
 *   focusedBorderColor, unfocusedBorderColor, focusedContainerColor, unfocusedContainerColor, focusedTextColor,
 *   unfocusedTextColor, focusedLabelColor, unfocusedLabelColor, focusedPlaceholderColor, unfocusedPlaceholderColor,
 *   focusedLeadingIconColor, unfocusedLeadingIconColor, focusedTrailingIconColor, unfocusedTrailingIconColor, cursorColor},
 *   textStyle?: typography name, key? })
 * 56dp min height, min width 280dp (OutlinedTextFieldDefaults.MinWidth) unless a width is given; notched label;
 * with a label the root gets M3's 8dp top padding (half the minimized label line).
 * Give it a stable `key` so focus survives re-renders.
 */
export function OutlinedTextField(p) {
  const c = p.colors || {};
  const enabled = p.enabled !== false;
  const isError = !!p.isError;
  const wrap = h('div', { class: 'm3-textfield' });
  Object.assign(wrap.style, { display: 'flex', flexDirection: 'column', minWidth: has(p, 'width') || hasClass(p, 'fill-w', 'weight', 'fill') ? '0' : '280px' });
  const field = h('div');
  Object.assign(field.style, {
    position: 'relative', display: 'flex', alignItems: p.singleLine === false || p.maxLines > 1 ? 'flex-start' : 'center', minHeight: '56px',
    borderRadius: p.shape || 'var(--radius-extraSmall)', cursor: 'text',
  });
  const fieldset = h('fieldset', { 'aria-hidden': 'true' });
  Object.assign(fieldset.style, { position: 'absolute', inset: '-5px 0 0 0', margin: '0', padding: '0 12px', borderRadius: 'inherit', pointerEvents: 'none', minWidth: '0', border: '1px solid', textAlign: 'left' });
  const legend = h('legend');
  Object.assign(legend.style, { padding: '0', fontSize: '12px', lineHeight: '11px', visibility: 'hidden', maxWidth: '0.01px', whiteSpace: 'nowrap', height: '11px', transition: 'max-width 50ms', letterSpacing: '0.4px', fontWeight: '400' });
  fieldset.append(legend);
  field.append(fieldset);
  const multi = p.singleLine === false || p.maxLines > 1 || p.minLines > 1;
  const input = multi ? h('textarea', { rows: p.minLines || 1 }) : h('input', { type: 'text' });
  const kt = p.keyboardOptions?.keyboardType;
  if (kt === 'Number' || kt === 'NumberPassword') input.inputMode = 'numeric';
  else if (kt === 'Decimal') input.inputMode = 'decimal';
  else if (kt === 'Email') input.type = 'email';
  else if (kt === 'Phone') input.inputMode = 'tel';
  else if (kt === 'Password' && !multi) input.type = 'password';
  input.className = 't-' + (p.textStyle || 'bodyLarge');
  Object.assign(input.style, {
    flex: '1 1 auto', minWidth: '0', border: '0', outline: 'none', background: 'transparent', padding: '16px', margin: '0',
    resize: 'none', caretColor: c.cursorColor || (isError ? 'var(--c-error)' : 'var(--c-primary)'), width: '100%',
  });
  input.value = p.value ?? '';
  input.disabled = !enabled; input.readOnly = !!p.readOnly;
  if (p.key != null) input.setAttribute('data-key', 'tf:' + p.key);
  const lead = slot(p.leadingIcon), trail = slot(p.trailingIcon);
  if (lead.length) { field.append(h('div', { class: 'tf-lead', style: { width: '48px', minWidth: '48px', display: 'flex', alignItems: 'center', justifyContent: 'center', marginLeft: '0', alignSelf: 'center' } }, ...lead)); input.style.paddingLeft = '0'; }
  const prefix = slot(p.prefix);
  if (prefix.length) field.append(h('div', { class: 't-bodyLarge', style: { paddingLeft: lead.length ? '0' : '16px', color: 'var(--c-onSurfaceVariant)' } }, ...prefix));
  if (prefix.length) input.style.paddingLeft = '2px';
  field.append(input);
  const suffix = slot(p.suffix);
  if (suffix.length) { field.append(h('div', { class: 't-bodyLarge', style: { paddingRight: trail.length ? '0' : '16px', color: 'var(--c-onSurfaceVariant)' } }, ...suffix)); input.style.paddingRight = '2px'; }
  if (trail.length) { field.append(h('div', { class: 'tf-trail', style: { width: '48px', minWidth: '48px', display: 'flex', alignItems: 'center', justifyContent: 'center', alignSelf: 'center' } }, ...trail)); input.style.paddingRight = '0'; }
  if (multi) {
    const autosize = () => { input.style.height = 'auto'; let hgt = input.scrollHeight; if (p.maxLines) hgt = Math.min(hgt, p.maxLines * 24 + 32); input.style.height = hgt + 'px'; };
    input.addEventListener('input', autosize); queueMicrotask(autosize);
  }
  const labelS = slot(p.label);
  let labelEl = null;
  // OutlinedTextField(label = …) pads its top by half the minimized label line (bodySmall 16sp / 2 = 8dp) so the
  // floated, notched label sits inside the field's bounds (and inside clipping parents such as a dialog's text).
  if (labelS.length) wrap.style.paddingTop = 'calc(var(--type-bodySmall-line) / 2)';
  if (labelS.length) {
    labelEl = h('div', { class: 't-bodyLarge' }, ...labelS);
    Object.assign(labelEl.style, { position: 'absolute', left: lead.length ? '52px' : '16px', top: '16px', pointerEvents: 'none', transformOrigin: 'left top', transition: 'transform 150ms cubic-bezier(0.4,0,0.2,1), color 150ms', whiteSpace: 'nowrap' });
    legend.append(...slot(p.label).map((n) => n.cloneNode(true)));
    field.append(labelEl);
  }
  const placeholderS = slot(p.placeholder);
  let ph = null;
  if (placeholderS.length) {
    ph = h('div', { class: 't-' + (p.textStyle || 'bodyLarge') }, ...placeholderS);
    Object.assign(ph.style, { position: 'absolute', left: (lead.length ? 48 : 16) + 'px', top: multi ? '16px' : '50%', transform: multi ? '' : 'translateY(-50%)', pointerEvents: 'none', whiteSpace: 'nowrap', overflow: 'hidden', right: '16px' });
    field.append(ph);
  }
  const support = slot(p.supportingText);
  const supportEl = support.length ? h('div', { class: 't-bodySmall', style: { padding: '4px 16px 0' } }, ...support) : null;
  wrap.append(field);
  if (supportEl) wrap.append(supportEl);
  const paint = () => {
    const focused = document.activeElement === input;
    const filled = input.value.length > 0;
    const pick = (f, u, err, dis) => (!enabled ? dis : isError ? err : focused ? f : u);
    const borderColor = pick(c.focusedBorderColor ?? 'var(--c-primary)', c.unfocusedBorderColor ?? 'var(--c-outline)', 'var(--c-error)', alpha('var(--c-onSurface)', 0.12));
    fieldset.style.borderWidth = focused && enabled ? '2px' : '1px';
    fieldset.style.borderColor = borderColor;
    fieldset.style.padding = focused ? '0 11px' : '0 12px';
    field.style.background = focused ? (c.focusedContainerColor ?? 'transparent') : (c.unfocusedContainerColor ?? 'transparent');
    input.style.color = pick(c.focusedTextColor ?? 'var(--c-onSurface)', c.unfocusedTextColor ?? 'var(--c-onSurface)', c.errorTextColor ?? 'var(--c-onSurface)', alpha('var(--c-onSurface)', 0.38));
    const iconColor = (f, u) => pick(f, u, 'var(--c-error)', alpha('var(--c-onSurface)', 0.38));
    field.querySelector('.tf-lead')?.style.setProperty('color', iconColor(c.focusedLeadingIconColor ?? 'var(--c-onSurfaceVariant)', c.unfocusedLeadingIconColor ?? 'var(--c-onSurfaceVariant)'));
    field.querySelector('.tf-trail')?.style.setProperty('color', isError ? 'var(--c-error)' : iconColor(c.focusedTrailingIconColor ?? 'var(--c-onSurfaceVariant)', c.unfocusedTrailingIconColor ?? 'var(--c-onSurfaceVariant)'));
    const floated = focused || filled || (labelEl && ph == null && false);
    if (labelEl) {
      labelEl.style.color = pick(c.focusedLabelColor ?? 'var(--c-primary)', c.unfocusedLabelColor ?? 'var(--c-onSurfaceVariant)', 'var(--c-error)', alpha('var(--c-onSurface)', 0.38));
      labelEl.style.transform = floated ? `translate(${lead.length ? -36 : 0}px, -24px) scale(0.75)` : 'none';
      legend.style.maxWidth = floated ? '100%' : '0.01px';
      legend.style.padding = floated ? '0 4px' : '0';
    }
    if (ph) {
      ph.style.display = filled || (labelEl && !focused) ? 'none' : '';
      ph.style.color = pick(c.focusedPlaceholderColor ?? 'var(--c-onSurfaceVariant)', c.unfocusedPlaceholderColor ?? 'var(--c-onSurfaceVariant)', 'var(--c-onSurfaceVariant)', alpha('var(--c-onSurface)', 0.38));
    }
    if (supportEl) supportEl.style.color = isError ? 'var(--c-error)' : 'var(--c-onSurfaceVariant)';
  };
  input.addEventListener('focus', paint);
  input.addEventListener('blur', paint);
  input.addEventListener('input', () => {
    const v = input.value;
    paint();
    p.onValueChange?.(v);
    // Controlled: if the owner rejected/reshaped the value and does not re-render, keep showing what it accepted.
  });
  if (p.keyboardActions?.onDone) input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !multi) p.keyboardActions.onDone(); });
  field.addEventListener('pointerdown', (e) => { if (e.target !== input && enabled) { e.preventDefault(); input.focus(); } });
  paint();
  return mod(wrap, p);
}

// =============================================================================================
// App bars, snackbar, scaffold
// =============================================================================================
/** TopAppBar({ title, navigationIcon?, actions?, colors?: {containerColor, titleContentColor, navigationIconContentColor, actionIconContentColor} }) 64dp. */
export function TopAppBar(p, centered = false) {
  const c = p.colors || {};
  const bar = h('header', { class: 'm3-top-app-bar' });
  Object.assign(bar.style, { height: '64px', display: 'flex', alignItems: 'center', padding: '0 4px', background: c.containerColor || 'var(--c-surface)', flex: 'none', position: 'relative' });
  const nav = slot(p.navigationIcon);
  bar.append(h('div', { style: { display: 'flex', color: c.navigationIconContentColor || 'var(--c-onSurface)' } }, ...nav));
  const title = h('div', { class: 't-titleLarge ellipsis', style: { flex: '1 1 auto', padding: nav.length ? '0 4px' : '0 12px', color: c.titleContentColor || 'var(--c-onSurface)', textAlign: centered ? 'center' : 'start' } }, ...slot(p.title));
  bar.append(title);
  bar.append(h('div', { style: { display: 'flex', color: c.actionIconContentColor || 'var(--c-onSurfaceVariant)' } }, ...slot(p.actions)));
  return mod(bar, p);
}
export const CenterAlignedTopAppBar = (p) => TopAppBar(p, true);

/** SnackbarHostState: showSnackbar({message, actionLabel?, duration='Short'|'Long'|'Indefinite'}) → Promise<'ActionPerformed'|'Dismissed'> */
export class SnackbarHostState {
  constructor() { this._host = null; this._queue = Promise.resolve(); }
  showSnackbar(opts) {
    if (typeof opts === 'string') opts = { message: opts };
    const run = () => new Promise((resolve) => {
      if (!this._host) { resolve('Dismissed'); return; }
      this._host._show(opts, resolve);
    });
    const r = this._queue.then(run);
    this._queue = r.catch(() => {});
    return r;
  }
}
/** SnackbarHost({ hostState }) — place at the bottom of a Box/Scaffold. */
export function SnackbarHost(p) {
  const host = h('div', { class: 'm3-snackbar-host' });
  Object.assign(host.style, { position: 'relative', display: 'flex', justifyContent: 'center', pointerEvents: 'none' });
  host._show = (opts, resolve) => {
    const bar = h('div', { class: 't-bodyMedium' });
    Object.assign(bar.style, { margin: '12px', minHeight: '48px', padding: '0 8px 0 16px', display: 'flex', alignItems: 'center', gap: '8px', borderRadius: 'var(--radius-extraSmall)', background: 'var(--c-inverseSurface)', color: 'var(--c-inverseOnSurface)', boxShadow: 'var(--shadow-6)', pointerEvents: 'auto', width: 'calc(100% - 24px)', maxWidth: '600px' });
    bar.append(h('div', { style: { flex: '1 1 auto', padding: '14px 0' } }, opts.message));
    let done = false;
    const finish = (r) => { if (done) return; done = true; bar.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 75, fill: 'forwards' }).onfinish = () => bar.remove(); resolve(r); };
    if (opts.actionLabel) bar.append(TextButton({ onClick: () => finish('ActionPerformed'), colors: { contentColor: 'var(--c-inversePrimary)' }, children: Text(opts.actionLabel) }));
    host.append(bar);
    bar.animate([{ opacity: 0, transform: 'scale(0.8)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 150, easing: 'cubic-bezier(0,0,0.2,1)' });
    const ms = opts.duration === 'Indefinite' ? 0 : opts.duration === 'Long' ? 10000 : 4000;
    if (ms) setTimeout(() => finish('Dismissed'), ms);
  };
  if (p.hostState) p.hostState._host = host;
  return mod(host, p);
}

/**
 * Scaffold({ containerColor=background, contentColor, topBar?, bottomBar?, snackbarHost?, floatingActionButton?,
 *   content: (padding) => Node })  padding = {top, bottom} css lengths (status/gesture insets + bars) like
 *   contentWindowInsets = safeDrawing. Insets are the *unconsumed* ones (WindowInsets.*): when the caller's
 *   modifier already padded by the system bars (the shell's route modifier = padding(p).consumeWindowInsets(p)),
 *   they are 0 here and nothing is applied twice.
 */
export function Scaffold(p) {
  const root = h('div', { class: 'm3-scaffold' });
  const container = p.containerColor || 'var(--c-background)';
  Object.assign(root.style, { position: 'relative', display: 'flex', flexDirection: 'column', height: '100%', background: container, color: p.contentColor || contentColorFor(container) || 'var(--c-onBackground)' });
  const top = slot(p.topBar), bottom = slot(p.bottomBar);
  const body = h('div', { style: { flex: '1 1 auto', minHeight: '0', position: 'relative', display: 'flex', flexDirection: 'column' } });
  // mod() first: a caller modifier that consumed the insets zeroes WindowInsets.* for everything below.
  mod(root, p);
  const padding = {
    top: top.length ? '0px' : WindowInsets.statusBars,
    bottom: bottom.length ? '0px' : WindowInsets.navigationBars,
  };
  if (top.length) root.append(h('div', { style: { paddingTop: WindowInsets.statusBars, flex: 'none' } }, ...top));
  body.append(...slot(typeof p.content === 'function' ? p.content(padding) : p.content));
  root.append(body);
  const fab = slot(p.floatingActionButton);
  if (fab.length) root.append(h('div', { style: { position: 'absolute', right: '16px', bottom: `calc(16px + ${bottom.length ? '0px' : padding.bottom})`, zIndex: '2' } }, ...fab));
  const snack = slot(p.snackbarHost);
  if (snack.length) root.append(h('div', { style: { position: 'absolute', left: '0', right: '0', bottom: padding.bottom, zIndex: '3' } }, ...snack));
  if (bottom.length) root.append(...bottom);
  return root;
}

// =============================================================================================
// Date picker
// =============================================================================================
const DAY_MS = 86400000;
const utcMidnight = (y, m, d) => Date.UTC(y, m, d);
const todayUtc = () => { const n = new Date(); return utcMidnight(n.getFullYear(), n.getMonth(), n.getDate()); };
const fmt = (ms, opts) => new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', ...opts }).format(new Date(ms));
/**
 * rememberDatePickerState({ initialSelectedDateMillis?, initialDisplayedMonthMillis?, yearRange=[1900,2100],
 *   selectableDates?: {isSelectableDate(utcMs), isSelectableYear?(y)}, key='datePicker' })
 * → { selectedDateMillis, displayedMonthMillis, ... } (UTC midnight millis, like Compose).
 * Composable-scoped (nav.rememberScoped): a picker that leaves the composition and comes back starts from the
 * initial values again.
 */
export function rememberDatePickerState(o = {}) {
  const box = rememberScoped('datePickerState:' + (o.key || 'datePicker'), () => {
    const sel = o.initialSelectedDateMillis ?? null;
    const disp = o.initialDisplayedMonthMillis ?? sel ?? todayUtc();
    const d = new Date(disp);
    return {
      selectedDateMillis: sel,
      displayedMonthMillis: utcMidnight(d.getUTCFullYear(), d.getUTCMonth(), 1),
      yearRange: o.yearRange || [1900, 2100],
      selectableDates: o.selectableDates || { isSelectableDate: () => true, isSelectableYear: () => true },
      displayMode: 'Picker',
    };
  });
  return box.value;
}
/**
 * DatePicker({ state, showModeToggle=true (input mode not implemented), title?, headline?, colors?: {containerColor,
 *   selectedDayContainerColor, selectedDayContentColor, todayContentColor, todayDateBorderColor, dayContentColor} })
 */
export function DatePicker(p) {
  const st = p.state;
  const c = p.colors || {};
  const root = h('div', { class: 'm3-date-picker' });
  Object.assign(root.style, { width: '360px', maxWidth: '100%', display: 'flex', flexDirection: 'column', background: c.containerColor || 'transparent', color: 'var(--c-onSurface)' });
  let yearMode = false;
  const draw = () => {
    root.replaceChildren();
    // Header (DateEntryContainer/DatePickerHeader): title (min height 120dp only with a title), headline row,
    // divider only when there is a title or headline. null title/headline = omitted (undefined = M3 defaults).
    // The mode toggle (showModeToggle) is not implemented: input mode does not exist here.
    const titleS = p.title !== undefined ? slot(p.title) : ['Select date'];
    const headlineS = p.headline !== undefined ? slot(p.headline)
      : [st.selectedDateMillis != null ? fmt(st.selectedDateMillis, { month: 'short', day: 'numeric', year: 'numeric' }) : 'Selected date'];
    if (titleS.length || headlineS.length) {
      const header = h('div', { class: 'm3-date-picker-header', style: { display: 'flex', flexDirection: 'column', justifyContent: 'space-between', flex: 'none', minHeight: titleS.length ? '120px' : '' } });
      if (titleS.length) header.append(h('div', { class: 't-labelLarge', style: { padding: '16px 12px 0 24px', color: c.titleContentColor || 'var(--c-onSurfaceVariant)' } }, ...titleS));
      if (headlineS.length) header.append(h('div', { class: 't-headlineLarge', style: { padding: '0 12px 12px 24px', color: c.headlineContentColor || 'var(--c-onSurfaceVariant)', whiteSpace: 'nowrap' } }, ...headlineS));
      root.append(header, HorizontalDivider({ color: c.dividerColor || 'var(--c-outlineVariant)', class: 'm3-date-picker-divider' }));
    }
    // Month / year navigation (56dp)
    const nav = h('div', { style: { height: '56px', display: 'flex', alignItems: 'center', padding: '0 12px', flex: 'none' } });
    const monthLabel = TextButton({
      onClick: () => { yearMode = !yearMode; draw(); },
      colors: { contentColor: 'var(--c-onSurfaceVariant)' },
      contentPadding: '8px 12px',
      children: [Text({ text: fmt(st.displayedMonthMillis, { month: 'long', year: 'numeric' }) }), Icon({ imageVector: 'Filled.ArrowDropDown', size: '18px', css: { transform: yearMode ? 'rotate(180deg)' : '' } })],
    });
    monthLabel.firstChild.style.setProperty('--btn-min-width', '0px');
    nav.append(monthLabel, h('div', { style: { flex: '1 1 auto' } }));
    const d = new Date(st.displayedMonthMillis);
    const y = d.getUTCFullYear(), m = d.getUTCMonth();
    if (!yearMode) {
      const shift = (n) => { st.displayedMonthMillis = utcMidnight(y, m + n, 1); draw(); };
      const minY = st.yearRange[0], maxY = st.yearRange[1];
      nav.append(
        IconButton({ onClick: () => shift(-1), enabled: !(y === minY && m === 0), colors: { contentColor: 'var(--c-onSurfaceVariant)' }, children: Icon({ imageVector: 'AutoMirrored.Filled.KeyboardArrowLeft', contentDescription: 'Change to previous month' }) }),
        IconButton({ onClick: () => shift(1), enabled: !(y === maxY && m === 11), colors: { contentColor: 'var(--c-onSurfaceVariant)' }, children: Icon({ imageVector: 'AutoMirrored.Filled.KeyboardArrowRight', contentDescription: 'Change to next month' }) }),
      );
    }
    root.append(nav);
    if (yearMode) {
      const grid = h('div', { class: 'scroll-y', style: { height: '336px', display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', alignContent: 'start', rowGap: '16px', padding: '16px 12px', flex: 'none' } });
      for (let yr = st.yearRange[0]; yr <= st.yearRange[1]; yr++) {
        const selY = yr === y;
        const isCur = yr === new Date().getFullYear();
        const enabled = st.selectableDates.isSelectableYear ? st.selectableDates.isSelectableYear(yr) : true;
        const cell = h('div', { class: 't-bodyLarge' });
        Object.assign(cell.style, { width: '72px', height: '36px', justifySelf: 'center', borderRadius: '18px', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', overflow: 'hidden', isolation: 'isolate', background: selY ? 'var(--c-primary)' : 'transparent', color: selY ? 'var(--c-onPrimary)' : isCur ? 'var(--c-primary)' : enabled ? 'var(--c-onSurfaceVariant)' : alpha('var(--c-onSurface)', 0.38) });
        if (isCur && !selY) applyBorder(cell, BorderStroke(1, 'var(--c-primary)'));
        cell.textContent = String(yr);
        clickable(cell, { enabled, onClick: () => { st.displayedMonthMillis = utcMidnight(yr, m, 1); yearMode = false; draw(); } });
        grid.append(cell);
      }
      root.append(grid);
      requestAnimationFrame(() => { const i = y - st.yearRange[0]; grid.scrollTop = Math.max(0, Math.floor(i / 3) * 52 - 104); });
      return;
    }
    // Weekdays (48dp) + 6 rows of 48dp
    const body = h('div', { style: { padding: '0 12px', flex: 'none' } });
    const wk = h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', height: '48px', alignItems: 'center' } });
    ['S', 'M', 'T', 'W', 'T', 'F', 'S'].forEach((n) => wk.append(h('div', { class: 't-bodyLarge', style: { textAlign: 'center', color: 'var(--c-onSurface)' } }, n)));
    body.append(wk);
    const grid = h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gridAutoRows: '48px' } });
    const first = new Date(utcMidnight(y, m, 1)).getUTCDay();
    const days = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
    const today = todayUtc();
    for (let i = 0; i < 42; i++) {
      const dayN = i - first + 1;
      const cell = h('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'center' } });
      if (dayN >= 1 && dayN <= days) {
        const ms = utcMidnight(y, m, dayN);
        const sel = st.selectedDateMillis === ms;
        const isToday = ms === today;
        const enabled = st.selectableDates.isSelectableDate(ms);
        const dot = h('div', { class: 't-bodyLarge' });
        Object.assign(dot.style, {
          width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', overflow: 'hidden', isolation: 'isolate',
          background: sel ? (c.selectedDayContainerColor || 'var(--c-primary)') : 'transparent',
          color: sel ? (c.selectedDayContentColor || 'var(--c-onPrimary)') : !enabled ? alpha('var(--c-onSurface)', 0.38) : isToday ? (c.todayContentColor || 'var(--c-primary)') : (c.dayContentColor || 'var(--c-onSurface)'),
          boxShadow: isToday && !sel ? `inset 0 0 0 1px ${c.todayDateBorderColor || 'var(--c-primary)'}` : 'none',
        });
        dot.textContent = String(dayN);
        clickable(dot, { enabled, onClick: () => { st.selectedDateMillis = ms; draw(); p.onSelectionChange?.(ms); } });
        cell.append(dot);
      }
      grid.append(cell);
    }
    body.append(grid);
    root.append(body);
  };
  draw();
  return mod(root, p);
}
/**
 * DatePickerDialog({ onDismissRequest, confirmButton, dismissButton?, shape=extraLarge, colors?: {containerColor},
 *   properties?, children: DatePicker })  360dp wide, ≤568dp tall.
 */
export function DatePickerDialog(p, ...kids) {
  const card = h('div', { class: 'm3-date-picker-dialog' });
  Object.assign(card.style, { width: '360px', maxWidth: '100%', maxHeight: '568px', background: p.colors?.containerColor || 'var(--c-surfaceContainerHigh)', borderRadius: p.shape || 'var(--radius-extraLarge)', display: 'flex', flexDirection: 'column', boxShadow: 'var(--shadow-6)', overflow: 'hidden', color: 'var(--c-onSurface)' });
  card.append(h('div', { style: { flex: '1 1 auto', minHeight: '0', overflow: 'hidden' } }, ...slot(p.children ?? kids)));
  const buttons = h('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: '8px', padding: '0 6px 8px 0', flex: 'none' } }, ...slot(p.dismissButton), ...slot(p.confirmButton));
  card.append(buttons);
  return Dialog({ onDismissRequest: p.onDismissRequest, properties: { ...(p.properties || {}), usePlatformDefaultWidth: false }, key: p.key, children: card });
}
export const DatePickerDefaults = { colors: (c = {}) => c };

// =============================================================================================
// Time picker
// =============================================================================================
/** android.text.format.DateFormat.is24HourFormat(): the device (browser locale) hour cycle. */
export function is24HourFormat() {
  try {
    const hc = new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions().hourCycle;
    return hc === 'h23' || hc === 'h24';
  } catch { return false; }
}
/**
 * rememberTimePickerState({ initialHour=0, initialMinute=0, is24Hour=is24HourFormat(), key='timePicker' })
 * → { hour, minute, is24hour }. Composable-scoped (nav.rememberScoped) like rememberDatePickerState.
 */
export function rememberTimePickerState(o = {}) {
  const box = rememberScoped('timePickerState:' + (o.key || 'timePicker'), () => ({
    hour: o.initialHour ?? 0, minute: o.initialMinute ?? 0,
    is24hour: o.is24Hour == null ? is24HourFormat() : !!o.is24Hour, selection: 'Hour',
  }));
  return box.value;
}
/**
 * TimePickerDefaults.colors({...}) — unspecified roles fall back to M3 1.3.1 TimePickerTokens:
 * clockDial surfaceContainerHighest, dial labels onPrimary/onSurface, selector primary, period border outline,
 * period selected tertiaryContainer/onTertiaryContainer, unselected transparent/onSurfaceVariant, time selector
 * selected primaryContainer/onPrimaryContainer, unselected surfaceContainerHighest/onSurface.
 */
export const TimePickerDefaults = { colors: (c = {}) => c };
/**
 * TimePicker({ state, colors?: {clockDialColor, clockDialSelectedContentColor, clockDialUnselectedContentColor,
 *   selectorColor, periodSelectorBorderColor, periodSelectorSelectedContainerColor, periodSelectorUnselectedContainerColor,
 *   periodSelectorSelectedContentColor, periodSelectorUnselectedContentColor, timeSelectorSelectedContainerColor,
 *   timeSelectorUnselectedContainerColor, timeSelectorSelectedContentColor, timeSelectorUnselectedContentColor} })
 * Vertical layout: 96×80 time selectors, 52×80 period selector, 256dp dial.
 */
export function TimePicker(p) {
  const st = p.state;
  const c = p.colors || {};
  const col = {
    dial: c.clockDialColor || 'var(--c-surfaceContainerHighest)', dialSel: c.clockDialSelectedContentColor || 'var(--c-onPrimary)',
    dialUnsel: c.clockDialUnselectedContentColor || 'var(--c-onSurface)', selector: c.selectorColor || 'var(--c-primary)',
    perBorder: c.periodSelectorBorderColor || 'var(--c-outline)', perSelBg: c.periodSelectorSelectedContainerColor || 'var(--c-tertiaryContainer)',
    perUnselBg: c.periodSelectorUnselectedContainerColor || 'transparent', perSelFg: c.periodSelectorSelectedContentColor || 'var(--c-onTertiaryContainer)',
    perUnselFg: c.periodSelectorUnselectedContentColor || 'var(--c-onSurfaceVariant)', tsSelBg: c.timeSelectorSelectedContainerColor || 'var(--c-primaryContainer)',
    tsUnselBg: c.timeSelectorUnselectedContainerColor || 'var(--c-surfaceContainerHighest)', tsSelFg: c.timeSelectorSelectedContentColor || 'var(--c-onPrimaryContainer)',
    tsUnselFg: c.timeSelectorUnselectedContentColor || 'var(--c-onSurface)',
  };
  const root = h('div', { class: 'm3-time-picker' });
  Object.assign(root.style, { display: 'flex', flexDirection: 'column', alignItems: 'center' });
  const draw = () => {
    root.replaceChildren();
    const is24 = !!(st.is24hour ?? st.is24Hour);
    const display = h('div', { style: { display: 'flex', alignItems: 'center', marginBottom: '36px' } });
    const selW = is24 ? 114 : 96;
    const sel = (kind, value) => {
      const on = st.selection === kind;
      const b = h('div', { class: 't-displayLarge' });
      Object.assign(b.style, { width: selW + 'px', height: '80px', borderRadius: 'var(--radius-small)', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', overflow: 'hidden', isolation: 'isolate', background: on ? col.tsSelBg : col.tsUnselBg, color: on ? col.tsSelFg : col.tsUnselFg });
      b.textContent = value;
      clickable(b, { onClick: () => { st.selection = kind; draw(); }, role: 'radio', checked: on });
      return b;
    };
    const hr = is24 ? st.hour : (st.hour % 12 === 0 ? 12 : st.hour % 12);
    display.append(sel('Hour', is24 ? String(hr).padStart(2, '0') : String(hr)), h('div', { class: 't-displayLarge', style: { width: '24px', textAlign: 'center', color: 'var(--c-onSurface)' } }, ':'), sel('Minute', String(st.minute).padStart(2, '0')));
    if (!is24) {
      const per = h('div', { style: { marginLeft: '12px', width: '52px', height: '80px', borderRadius: 'var(--radius-small)', display: 'flex', flexDirection: 'column', overflow: 'hidden' } });
      applyBorder(per, BorderStroke(1, col.perBorder));
      const pm = st.hour >= 12;
      const half = (label, on, onClick) => {
        const x = h('div', { class: 't-titleMedium' });
        Object.assign(x.style, { flex: '1 1 0', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', overflow: 'hidden', isolation: 'isolate', background: on ? col.perSelBg : col.perUnselBg, color: on ? col.perSelFg : col.perUnselFg });
        x.textContent = label;
        clickable(x, { onClick, role: 'radio', checked: on });
        return x;
      };
      per.append(half('AM', !pm, () => { if (pm) { st.hour -= 12; draw(); } }), h('div', { style: { height: '1px', background: col.perBorder, flex: 'none' } }), half('PM', pm, () => { if (!pm) { st.hour += 12; draw(); } }));
      display.append(per);
    }
    root.append(display);
    // Dial
    const S = 256, R = 101, center = S / 2;
    const dial = h('div', { style: { width: S + 'px', height: S + 'px', borderRadius: '50%', background: col.dial, position: 'relative', marginBottom: '24px', touchAction: 'none', userSelect: 'none', flex: 'none' } });
    const svg = h('svg', { attrs: { viewBox: `0 0 ${S} ${S}`, width: S, height: S } });
    svg.style.cssText = 'position:absolute;inset:0';
    const minuteMode = st.selection === 'Minute';
    const labels = minuteMode ? [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55] : [12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
    let angle, radius = R;
    if (minuteMode) angle = (st.minute / 60) * 2 * Math.PI;
    else {
      const h12 = st.hour % 12;
      angle = (h12 / 12) * 2 * Math.PI;
      if (is24 && (st.hour === 0 || st.hour > 12)) radius = 69;
    }
    const hx = center + radius * Math.sin(angle), hy = center - radius * Math.cos(angle);
    let s = `<line x1="${center}" y1="${center}" x2="${hx}" y2="${hy}" style="stroke:${col.selector}" stroke-width="2"/>`;
    s += `<circle cx="${center}" cy="${center}" r="4" style="fill:${col.selector}"/>`;
    s += `<circle cx="${hx}" cy="${hy}" r="24" style="fill:${col.selector}"/>`;
    svg.innerHTML = s;
    dial.append(svg);
    const addLabel = (text, ang, r, selected) => {
      const x = center + r * Math.sin(ang), y = center - r * Math.cos(ang);
      const l = h('div', { class: 't-bodyLarge' });
      Object.assign(l.style, { position: 'absolute', left: x - 24 + 'px', top: y - 24 + 'px', width: '48px', height: '48px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: selected ? col.dialSel : col.dialUnsel, pointerEvents: 'none' });
      l.textContent = text;
      dial.append(l);
    };
    labels.forEach((v, i) => {
      const ang = (i / 12) * 2 * Math.PI;
      const selected = minuteMode ? st.minute === v : (is24 ? (st.hour === (v === 12 ? 12 : v)) : (st.hour % 12 === v % 12));
      addLabel(minuteMode ? String(v).padStart(2, '0') : String(v), ang, R, selected && radius === R);
    });
    if (is24 && !minuteMode) {
      [0, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23].forEach((v, i) => addLabel(v === 0 ? '00' : String(v), (i / 12) * 2 * Math.PI, 69, st.hour === v));
    }
    const pick = (e, fromTap) => {
      const r = dial.getBoundingClientRect();
      const x = e.clientX - r.left - center, y = e.clientY - r.top - center;
      let a = Math.atan2(x, -y); if (a < 0) a += 2 * Math.PI;
      if (minuteMode) {
        let mnt = Math.round((a / (2 * Math.PI)) * 60) % 60;
        if (fromTap) mnt = (Math.round(mnt / 5) * 5) % 60;
        st.minute = mnt;
      } else {
        let hh = Math.round((a / (2 * Math.PI)) * 12) % 12;
        const inner = is24 && Math.hypot(x, y) < (R + 69) / 2;
        if (is24) st.hour = inner ? (hh === 0 ? 0 : hh + 12) : (hh === 0 ? 12 : hh);
        else st.hour = (st.hour >= 12 ? 12 : 0) + hh;
      }
      draw();
    };
    dial.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      let moved = false;
      const mv = (ev) => { moved = true; pick(ev, false); };
      const up = (ev) => {
        window.removeEventListener('pointermove', mv); window.removeEventListener('pointerup', up);
        if (!moved) pick(ev, true);
        if (st.selection === 'Hour') { st.selection = 'Minute'; draw(); }
      };
      window.addEventListener('pointermove', mv); window.addEventListener('pointerup', up);
    });
    root.append(dial);
  };
  draw();
  return mod(root, p);
}

// MaterialTheme facade (typography names, colorScheme vars) for code that reads MaterialTheme.*.
export const MaterialTheme = {
  colorScheme: C,
  typography: new Proxy({}, { get: (_, k) => String(k) }),
  shapes: Shapes,
};

// =============================================================================================
// core/design — AppComponents.kt
// =============================================================================================
export const AppCardTone = Object.freeze({ Standard: 'Standard', Raised: 'Raised' });
/** AppShapes.card = RoundedCornerShape(Radius.lg), iconTile = RoundedCornerShape(Radius.sm) */
export const AppShapes = Object.freeze({ card: 'var(--radius-lg)', iconTile: 'var(--radius-sm)' });

/** AppCard({ tone=Standard, onClick?, children (ColumnScope), class?, css? }) */
export function AppCard(p = {}, ...kids) {
  const isRaised = p.tone === AppCardTone.Raised;
  const container = isRaised ? 'var(--c-elevated)' : 'var(--c-surfaceCard)';
  const content = Column({ class: 'fill-w' }, ...slot(p.children ?? p.content ?? kids));
  return Surface({
    class: p.class, css: p.css, modifier: p.modifier, key: p.key,
    onClick: p.onClick || undefined,
    shape: AppShapes.card, color: container, contentColor: 'var(--c-onSurface)',
    tonalElevation: isRaised ? 2 : 1, shadowElevation: isRaised ? 3 : 1,
    children: content,
  });
}
/** AppListGroup({ children }) — a grouped Settings/Profile list (AppCard). */
export function AppListGroup(p = {}, ...kids) { return AppCard({ class: p.class, css: p.css, modifier: p.modifier, children: slot(p.children ?? kids) }); }
/** AppListDivider() — HorizontalDivider(color = borderSubtle) */
export function AppListDivider(p = {}) { return HorizontalDivider({ ...p, color: 'var(--c-borderSubtle)' }); }

/** AppListRow({ icon: 'Outlined.Info', title, subtitle?, onClick, trailing?: Node|fn }) */
export function AppListRow(p) {
  const tile = Surface({ shape: AppShapes.iconTile, color: 'var(--c-surfaceControl)', children: Icon({ imageVector: p.icon, tint: 'var(--c-brandDeep)', css: { margin: 'var(--space-sm)' } }) });
  tile.style.flex = 'none';
  const texts = Column({ class: 'weight', css: { paddingLeft: 'var(--space-md)' } },
    Text({ text: p.title, style: 'titleSmall', color: 'var(--c-text)' }),
    p.subtitle != null ? Text({ text: p.subtitle, style: 'bodySmall', color: 'var(--c-textMuted)' }) : null,
  );
  const trailing = p.trailing ? slot(p.trailing) : [Icon({ imageVector: 'AutoMirrored.Filled.KeyboardArrowRight', tint: 'var(--c-textMuted)' })];
  const row = Row({ verticalAlignment: 'CenterVertically', css: { padding: 'var(--space-md) var(--space-lg)' }, class: 'fill-w' }, tile, texts, ...trailing);
  return Surface({ class: cx('fill-w', p.class), css: { width: '100%', ...(p.css || {}) }, onClick: p.onClick, color: 'var(--c-surfaceCard)', children: row });
}

/** AppValueStepper({ label, value, decreaseContentDescription, increaseContentDescription, onDecrease, onIncrease, decreaseEnabled?, increaseEnabled? }) */
export function AppValueStepper(p) {
  return Row({ class: cx('fill-w', p.class), css: p.css, verticalAlignment: 'CenterVertically' },
    Text({ text: p.label, class: 'weight', style: 'titleSmall', color: 'var(--c-textSecondary)' }),
    AppStepperControl({
      value: p.value, decreaseContentDescription: p.decreaseContentDescription, increaseContentDescription: p.increaseContentDescription,
      onDecrease: p.onDecrease, onIncrease: p.onIncrease, decreaseEnabled: p.decreaseEnabled, increaseEnabled: p.increaseEnabled,
    }),
  );
}
/** AppStepperControl({ value, decreaseContentDescription, increaseContentDescription, onDecrease, onIncrease, decreaseEnabled?, increaseEnabled? }) */
export function AppStepperControl(p) {
  const row = Row({ verticalAlignment: 'CenterVertically' },
    IconButton({ onClick: p.onDecrease, enabled: p.decreaseEnabled !== false, css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' },
      children: Icon({ imageVector: 'Filled.Remove', contentDescription: p.decreaseContentDescription, size: 'var(--size-smallIcon)' }) }),
    Text({ text: p.value, css: { minWidth: 'var(--size-touchTarget)' }, style: 'titleMedium', color: 'var(--c-brandDeep)', textAlign: 'Center' }),
    IconButton({ onClick: p.onIncrease, enabled: p.increaseEnabled !== false, css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' },
      children: Icon({ imageVector: 'Filled.Add', contentDescription: p.increaseContentDescription, size: 'var(--size-smallIcon)' }) }),
  );
  return Surface({ class: p.class, css: p.css, shape: 'var(--radius-pill)', color: 'var(--c-surfaceControl)', children: row });
}

// =============================================================================================
// core/design — SessionListItem.kt
// =============================================================================================
/** SessionListItem({ time, title, detail, duration, onClick?, trailing?: Node|fn }) */
export function SessionListItem(p) {
  const row = Row({ class: cx('fill-w', p.class), css: { padding: 'var(--space-md) var(--space-lg)', ...(p.css || {}) }, verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-md)' } },
    Text({ text: p.time, style: 'labelMedium', color: 'var(--c-textMuted)' }),
    Column({ class: 'weight' },
      Text({ text: p.title, style: 'titleSmall', color: 'var(--c-text)', fontWeight: 'SemiBold', maxLines: 2, overflow: 'Ellipsis' }),
      p.detail ? Text({ text: p.detail, style: 'bodySmall', color: 'var(--c-textSecondary)', maxLines: 1, overflow: 'Ellipsis', css: { paddingTop: 'var(--space-xxs)' } }) : null,
    ),
    Text({ text: p.duration, style: 'titleSmall', color: 'var(--c-text)', fontWeight: 'SemiBold' }),
    ...slot(p.trailing),
  );
  if (p.onClick) clickable(row, { onClick: p.onClick });
  return row;
}

// =============================================================================================
// core/design — ProLock.kt
// =============================================================================================
/** ProLockCard({ title, body, actionLabel, onUnlock }) */
export function ProLockCard(p) {
  return AppCard({ class: cx('fill-w', p.class), css: { width: '100%', ...(p.css || {}) }, onClick: p.onUnlock, children:
    Column({ css: { padding: 'var(--space-lg)' }, verticalArrangement: { spacedBy: 'var(--space-xs)' }, class: 'fill-w' },
      Row({ verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
        ProLockIcon(),
        Text({ text: p.title, style: 'titleSmall', color: 'var(--c-text)', fontWeight: 'SemiBold' }),
      ),
      Text({ text: p.body, style: 'bodySmall', color: 'var(--c-textSecondary)' }),
      OutlinedButton({ onClick: p.onUnlock, css: { paddingTop: 'var(--space-sm)' }, children: Text(p.actionLabel) }),
    ),
  });
}
/** ProLockIcon({ class?, css? }) — Outlined.Lock 16dp, textMuted. */
export function ProLockIcon(p = {}) {
  return Icon({ imageVector: 'Outlined.Lock', contentDescription: null, tint: 'var(--c-textMuted)', size: 'var(--size-compactIcon)', class: p.class, css: p.css });
}

// =============================================================================================
// core/design — LengthPicker.kt
// =============================================================================================
/** LengthPickerLabels({ minutes: (n)=>string, custom, dialogTitle, fieldLabel, ok, cancel }) */
export function LengthPickerLabels(o) { return Object.freeze({ ...o }); }
/**
 * LengthPicker({ presets: number[], selectedMinutes, onSelect(n), parseCustom(str)→number|null, labels, enabled?, key? })
 * Local state (`editing`, `text`) lives in remember() under key 'lengthPicker:<key>'.
 */
export function LengthPicker(p) {
  const k = 'lengthPicker:' + (p.key ?? '');
  const editing = remember(k + ':editing', false);
  const text = remember(k + ':text', String(p.selectedMinutes));
  const enabled = p.enabled !== false;
  const isCustom = !p.presets.includes(p.selectedMinutes);
  return observe(editing, (isEditing) => {
    const row = Row({ class: cx('scroll-x', p.class), css: p.css, horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      ...p.presets.map((minutes) => FilterChip({ selected: p.selectedMinutes === minutes, onClick: () => p.onSelect(minutes), enabled, label: Text(p.labels.minutes(minutes)) })),
      FilterChip({ selected: isCustom, onClick: () => { text.value = String(p.selectedMinutes); editing.value = true; }, enabled, label: Text(isCustom ? p.labels.minutes(p.selectedMinutes) : p.labels.custom) }),
    );
    row.setAttribute('data-scroll-key', k);
    if (!isEditing) return row;
    const ok = () => TextButton({
      onClick: () => { const v = p.parseCustom(text.value); if (v != null) p.onSelect(v); editing.value = false; },
      enabled: p.parseCustom(text.value) != null, children: Text(p.labels.ok),
    });
    const okHost = h('div', { style: { display: 'contents' } }, ok());
    const dialog = AlertDialog({
      key: k,
      onDismissRequest: () => { editing.value = false; },
      title: Text(p.labels.dialogTitle),
      text: OutlinedTextField({
        key: k, value: text.value, singleLine: true, label: Text(p.labels.fieldLabel), keyboardOptions: { keyboardType: 'Number' },
        onValueChange: (v) => {
          const filtered = v.replace(/\D/g, '').slice(0, 3);
          text.value = filtered;
          const input = document.activeElement;
          if (input && 'value' in input && input.value !== filtered) input.value = filtered;
          okHost.replaceChildren(ok());
        },
      }),
      confirmButton: okHost,
      dismissButton: TextButton({ onClick: () => { editing.value = false; }, children: Text(p.labels.cancel) }),
      containerColor: 'var(--c-surface)',
    });
    return [row, dialog];
  }, { style: { display: 'contents' } });
}
