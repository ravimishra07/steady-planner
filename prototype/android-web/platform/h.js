// h.js — tiny DOM builder. Composable = function(props) → Node.
//
// h(tag, props?, ...children) → Element
//   tag       'div' | 'svg' | 'svg:path' (the svg: prefix creates an SVG-namespaced element; use it for every SVG child) | function
//             (a function tag is called as tag({...props, children}) and its return value used)
//   props     null | object:
//     class / className   string | array (falsy entries dropped) | {name: bool}
//     style               object (camelCase or '--custom-prop' keys; null/undefined/false values skipped) | string
//     on<Event>           listener, e.g. onClick, onInput, onPointerDown (lower-cased event name)
//     ref                 fn(el) called after creation
//     attrs               object of raw attributes
//     dataset             object → data-* attributes
//     html                innerHTML string (trusted only)
//     value/checked/disabled/selected/hidden/tabIndex/... DOM properties are set directly
//     anything else       set as attribute (true → '', false/null/undefined → omitted)
//   children  Node | string | number | array (nested, flattened) | null/undefined/false (skipped)
//
// text(str)             → Text node
// fragment(...children) → DocumentFragment
// mount(parent, node)   replace parent's children with node
// cx(...classes)        class string builder (same rules as `class` prop)
// styleOf(obj)          apply style object to an element: styleOf(el, {...})
const SVG_NS = 'http://www.w3.org/2000/svg';
const PROPS = new Set([
  'value', 'checked', 'disabled', 'selected', 'hidden', 'tabIndex', 'readOnly', 'multiple', 'indeterminate',
  'placeholder', 'type', 'name', 'id', 'title', 'min', 'max', 'step', 'inputMode', 'autocomplete', 'maxLength',
  'draggable', 'spellcheck', 'scrollTop', 'scrollLeft', 'textContent',
]);

export function cx(...parts) {
  const out = [];
  const add = (p) => {
    if (!p) return;
    if (typeof p === 'string') out.push(p);
    else if (Array.isArray(p)) p.forEach(add);
    else if (typeof p === 'object') for (const k in p) if (p[k]) out.push(k);
  };
  parts.forEach(add);
  return out.join(' ');
}

export function styleOf(el, style) {
  if (!style) return el;
  if (typeof style === 'string') { el.style.cssText += ';' + style; return el; }
  for (const k in style) {
    const v = style[k];
    if (v === null || v === undefined || v === false) continue;
    if (k.startsWith('--') || k.includes('-')) el.style.setProperty(k, String(v));
    else el.style[k] = typeof v === 'number' && !UNITLESS.has(k) ? v + 'px' : v;
  }
  return el;
}
const UNITLESS = new Set(['opacity', 'zIndex', 'flex', 'flexGrow', 'flexShrink', 'fontWeight', 'lineHeight', 'order', 'zoom', 'aspectRatio']);

function appendChildren(el, children, svg) {
  for (const c of children) {
    if (c === null || c === undefined || c === false || c === true) continue;
    if (Array.isArray(c)) { appendChildren(el, c, svg); continue; }
    if (c instanceof Node) { el.appendChild(c); continue; }
    el.appendChild(document.createTextNode(String(c)));
  }
}

export function h(tag, props, ...children) {
  if (typeof tag === 'function') return tag({ ...(props || {}), children: children.flat(Infinity) });
  let svg = false;
  let name = tag;
  if (tag.startsWith('svg:')) { svg = true; name = tag.slice(4); } else if (tag === 'svg') svg = true;
  const el = svg ? document.createElementNS(SVG_NS, name) : document.createElement(name);
  if (props) applyProps(el, props, svg);
  appendChildren(el, children, svg);
  if (props && typeof props.ref === 'function') props.ref(el);
  return el;
}

function applyProps(el, props, svg) {
  for (const k in props) {
    const v = props[k];
    if (k === 'ref' || k === 'children') continue;
    if (k === 'class' || k === 'className') {
      const c = cx(v); if (c) el.setAttribute('class', c);
    } else if (k === 'style') styleOf(el, v);
    else if (k === 'attrs') { for (const a in v) if (v[a] != null && v[a] !== false) el.setAttribute(a, v[a] === true ? '' : v[a]); }
    else if (k === 'dataset') { for (const d in v) if (v[d] != null) el.dataset[d] = v[d]; }
    else if (k === 'html') el.innerHTML = v;
    else if (k.length > 2 && k[0] === 'o' && k[1] === 'n' && k[2] === k[2].toUpperCase()) {
      if (typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    } else if (!svg && PROPS.has(k)) { if (v !== undefined) el[k] = v; }
    else if (v === true) el.setAttribute(k, '');
    else if (v !== false && v !== null && v !== undefined) el.setAttribute(k, String(v));
  }
}

export const text = (s) => document.createTextNode(String(s ?? ''));
export function fragment(...children) {
  const f = document.createDocumentFragment();
  appendChildren(f, children, false);
  return f;
}
export function mount(parent, node) {
  parent.replaceChildren(...(node ? [node] : []));
  return node;
}
