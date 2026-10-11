// format.js — java.util.Formatter (String.format / Context.getString(id, args…)) for Android string resources.
//
//   formatAndroid(fmt, ...args) → string
//
// Grammar: %[argument_index$ | <][flags][width][.precision]conversion
//   argument_index  1$, 2$ … (explicit), '<' = previous argument; otherwise the next ordinary argument
//   flags           '-' left-justify, '#' alternate form (o/x prefix), '+' sign, ' ' leading space, '0' zero pad,
//                   ',' grouping separators, '(' negatives in parentheses
//   conversions     s S (String.valueOf; precision truncates), b B (null → false, boolean as is, else true),
//                   c C (char / code point), d (decimal), o x X (octal/hex; negatives as 32-bit two's complement when
//                   they fit an Int, else 64-bit like Long), e E f g G a A (floating point, HALF_UP rounding on the
//                   shortest decimal representation like Java's Double formatting), h H (hashCode hex for strings /
//                   integers), n (line separator '\n'), % (literal), t T + suffix (date/time: H I k l M S L p
//                   B b h A a C Y y j m d e R T r D F c Z s Q) for Date / epoch-millis arguments.
// Locale: Locale.ROOT-like symbols ('.' decimal, ',' grouping, groups of 3) — the app's strings are English.
// Errors follow Java's intent without throwing: a missing argument formats as 'null'.

const SPEC = /%(?:(\d+)\$|(<))?([-#+ 0,(]*)(\d+)?(?:\.(\d+))?(?:([tT])([a-zA-Z])|([a-zA-Z%]))/g;

export function formatAndroid(fmt, ...args) {
  let next = 0;
  let last = -1;
  return String(fmt).replace(SPEC, (m, pos, prevFlag, flags = '', width, prec, t, tSuffix, conv) => {
    width = width ? Number(width) : 0;
    if (conv === '%') return pad('%', flags, width);
    if (conv === 'n') return '\n';
    let idx;
    if (prevFlag) idx = last;
    else if (pos) idx = Number(pos) - 1;
    else idx = next++;
    last = idx;
    const v = idx >= 0 && idx < args.length ? args[idx] : null;
    if (t) {
      const out = v == null ? 'null' : dateTime(v, tSuffix);
      return pad(t === 'T' ? out.toUpperCase() : out, flags, width);
    }
    return convert(v, conv, flags, width, prec != null ? Number(prec) : null);
  });
}
/** Alias with the Java name. */
export const stringFormat = formatAndroid;

function convert(v, conv, flags, width, prec) {
  const upper = conv >= 'A' && conv <= 'Z' && conv !== 'X' ? true : conv === 'X';
  switch (conv.toLowerCase()) {
    case 's': {
      let out = v == null ? 'null' : typeof v === 'object' && typeof v.toString === 'function' ? v.toString() : String(v);
      if (prec != null) out = out.slice(0, prec);
      return pad(upper ? out.toUpperCase() : out, flags, width);
    }
    case 'b': {
      let out = v == null ? 'false' : typeof v === 'boolean' ? String(v) : 'true';
      if (prec != null) out = out.slice(0, prec);
      return pad(upper ? out.toUpperCase() : out, flags, width);
    }
    case 'c': {
      if (v == null) return pad('null', flags, width);
      const out = typeof v === 'number' ? String.fromCodePoint(v) : String(v).slice(0, 1);
      return pad(upper ? out.toUpperCase() : out, flags, width);
    }
    case 'h': {
      const out = v == null ? 'null' : (hashCode(v) >>> 0).toString(16);
      return pad(upper ? out.toUpperCase() : out, flags, width);
    }
    case 'd': return v == null ? pad('null', flags, width) : integer(v, flags, width);
    case 'o': case 'x': return v == null ? pad('null', flags, width) : radix(v, conv, flags, width);
    case 'e': case 'f': case 'g': case 'a':
      return v == null ? pad('null', flags, width) : floating(Number(v), conv, flags, width, prec);
    default:
      return '%' + flags + (width || '') + (prec != null ? '.' + prec : '') + conv; // unknown: leave as written
  }
}

function pad(s, flags, width) {
  width = Number(width) || 0;
  if (s.length >= width) return s;
  const fill = ' '.repeat(width - s.length);
  return (flags || '').includes('-') ? s + fill : fill + s;
}

/** Sign, grouping, zero padding and parentheses for a magnitude string `digits` (no sign). */
function finishNumber(neg, digits, flags, width) {
  if (flags.includes(',')) {
    const [i, f] = digits.split('.');
    digits = i.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (f != null ? '.' + f : '');
  }
  let prefix = '', suffix = '';
  if (neg) { if (flags.includes('(')) { prefix = '('; suffix = ')'; } else prefix = '-'; }
  else if (flags.includes('+')) prefix = '+';
  else if (flags.includes(' ')) prefix = ' ';
  if (flags.includes('0') && !flags.includes('-')) {
    const n = width - prefix.length - suffix.length - digits.length;
    if (n > 0) digits = '0'.repeat(n) + digits;
  }
  return pad(prefix + digits + suffix, flags, width);
}

function toBigInt(v) {
  if (typeof v === 'bigint') return v;
  const n = Number(v);
  if (!Number.isFinite(n)) return null;
  return BigInt(Math.trunc(n));
}

function integer(v, flags, width) {
  const b = toBigInt(v);
  if (b == null) return pad(String(v), flags, width);
  const neg = b < 0n;
  return finishNumber(neg, (neg ? -b : b).toString(), flags, width);
}

function radix(v, conv, flags, width) {
  let b = toBigInt(v);
  if (b == null) return pad(String(v), flags, width);
  if (b < 0n) b = (b >= -(2n ** 31n) ? 2n ** 32n : 2n ** 64n) + b; // Integer / Long two's complement
  let out = b.toString(conv.toLowerCase() === 'o' ? 8 : 16);
  if (conv === 'X') out = out.toUpperCase();
  if (flags.includes('#')) out = (conv === 'o' ? '0' : conv === 'X' ? '0X' : '0x') + out;
  if (flags.includes('0') && !flags.includes('-') && out.length < width) out = '0'.repeat(width - out.length) + out;
  return pad(out, flags, width);
}

/** Decimal digits of |x| as { digits: '123456', point: 3 } (value = 0.digits × 10^point), shortest repr. */
function decimalOf(x) {
  const s = Math.abs(x).toExponential(); // shortest round-trip mantissa: 'd.ddde±n'
  const [mant, exp] = s.split('e');
  const digits = mant.replace('.', '');
  return { digits, point: Number(exp) + 1 };
}
/** |x| rounded HALF_UP (on its shortest decimal representation) to `frac` fraction digits → plain string. */
function fixedHalfUp(x, frac) {
  if (x === 0) return frac > 0 ? '0.' + '0'.repeat(frac) : '0';
  let { digits, point } = decimalOf(x);
  const keep = point + frac;
  let kept;
  if (keep < 0) { kept = ''; }
  else if (keep >= digits.length) kept = digits;
  else {
    kept = digits.slice(0, keep);
    if (digits.charCodeAt(keep) >= 53) { // >= '5' → HALF_UP
      const inc = (BigInt(kept || '0') + 1n).toString();
      if (inc.length > kept.length) point += 1;
      kept = inc;
    }
  }
  if (!/[1-9]/.test(kept)) return frac > 0 ? '0.' + '0'.repeat(frac) : '0';
  let intPart, fracPart;
  if (point <= 0) { intPart = '0'; fracPart = '0'.repeat(-point) + kept; }
  else if (point >= kept.length) { intPart = kept + '0'.repeat(point - kept.length); fracPart = ''; }
  else { intPart = kept.slice(0, point); fracPart = kept.slice(point); }
  fracPart = (fracPart + '0'.repeat(frac)).slice(0, frac);
  return frac > 0 ? intPart + '.' + fracPart : intPart;
}
/** Scientific with `frac` mantissa decimals, HALF_UP; exponent ≥ 2 digits (Java). */
function sciHalfUp(x, frac) {
  if (x === 0) return { mant: frac > 0 ? '0.' + '0'.repeat(frac) : '0', exp: 0 };
  const { point } = decimalOf(x);
  let exp = point - 1;
  let mant = fixedHalfUp(Math.abs(x) / 10 ** exp, frac);
  if (/^10/.test(mant)) { exp += 1; mant = fixedHalfUp(Math.abs(x) / 10 ** exp, frac); }
  return { mant, exp };
}
const expStr = (exp, upper) => (upper ? 'E' : 'e') + (exp < 0 ? '-' : '+') + String(Math.abs(exp)).padStart(2, '0');

function floating(x, conv, flags, width, prec) {
  const upper = conv === 'E' || conv === 'G' || conv === 'A';
  if (Number.isNaN(x)) return pad(upper ? 'NAN' : 'NaN', flags, width);
  const neg = x < 0 || Object.is(x, -0);
  if (!Number.isFinite(x)) {
    const s = upper ? 'INFINITY' : 'Infinity';
    return pad(neg ? (flags.includes('(') ? '(' + s + ')' : '-' + s) : flags.includes('+') ? '+' + s : flags.includes(' ') ? ' ' + s : s, flags, width);
  }
  const ax = Math.abs(x);
  let body;
  switch (conv.toLowerCase()) {
    case 'f': body = fixedHalfUp(ax, prec ?? 6); break;
    case 'e': { const { mant, exp } = sciHalfUp(ax, prec ?? 6); body = mant + expStr(exp, upper); break; }
    case 'g': {
      let p = prec ?? 6; if (p === 0) p = 1;
      const rounded = ax === 0 ? 0 : Number(sciHalfUp(ax, p - 1).mant) * 10 ** sciHalfUp(ax, p - 1).exp;
      if (rounded !== 0 && (rounded < 1e-4 || rounded >= 10 ** p)) {
        const { mant, exp } = sciHalfUp(ax, p - 1); body = mant + expStr(exp, upper);
      } else {
        const intDigits = rounded === 0 ? 1 : Math.floor(Math.log10(rounded)) + 1;
        body = fixedHalfUp(ax, Math.max(0, p - intDigits));
      }
      break;
    }
    case 'a': {
      body = hexFloat(ax, prec);
      if (upper) body = body.toUpperCase();
      return pad((neg ? '-' : flags.includes('+') ? '+' : '') + body, flags, width);
    }
    default: body = String(ax);
  }
  if (upper) body = body.toUpperCase();
  // Grouping applies to %f / %g only (Java rejects ',' with %e).
  return finishNumber(neg, body, conv.toLowerCase() === 'e' ? flags.replace(',', '') : flags, width);
}

function hexFloat(ax, prec) {
  if (ax === 0) return '0x0.0p0';
  const buf = new DataView(new ArrayBuffer(8));
  buf.setFloat64(0, ax);
  const hi = buf.getUint32(0), lo = buf.getUint32(4);
  const expBits = (hi >>> 20) & 0x7ff;
  let mant = ((BigInt(hi & 0xfffff) << 32n) | BigInt(lo)).toString(16).padStart(13, '0');
  const subnormal = expBits === 0;
  const exp = subnormal ? -1022 : expBits - 1023;
  if (prec == null) mant = mant.replace(/0+$/, '') || '0';
  else mant = mant.slice(0, Math.max(1, prec)).padEnd(Math.max(1, prec), '0');
  return `0x${subnormal ? 0 : 1}.${mant}p${exp}`;
}

function hashCode(v) {
  if (typeof v === 'number' && Number.isInteger(v)) return v | 0;
  if (typeof v === 'boolean') return v ? 1231 : 1237;
  const s = String(v);
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return h;
}

// ---- %t / %T ------------------------------------------------------------------------------------
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const two = (n) => String(n).padStart(2, '0');
function dateTime(v, c) {
  const d = v instanceof Date ? v : new Date(typeof v === 'number' || typeof v === 'bigint' ? Number(v) : String(v));
  if (Number.isNaN(d.getTime())) return String(v);
  const H = d.getHours(), I = H % 12 === 0 ? 12 : H % 12;
  switch (c) {
    case 'H': return two(H);
    case 'I': return two(I);
    case 'k': return String(H);
    case 'l': return String(I);
    case 'M': return two(d.getMinutes());
    case 'S': return two(d.getSeconds());
    case 'L': return String(d.getMilliseconds()).padStart(3, '0');
    case 'p': return H < 12 ? 'am' : 'pm';
    case 'B': return MONTHS[d.getMonth()];
    case 'b': case 'h': return MONTHS[d.getMonth()].slice(0, 3);
    case 'A': return DAYS[d.getDay()];
    case 'a': return DAYS[d.getDay()].slice(0, 3);
    case 'C': return two(Math.floor(d.getFullYear() / 100));
    case 'Y': return String(d.getFullYear()).padStart(4, '0');
    case 'y': return two(d.getFullYear() % 100);
    case 'j': { const start = new Date(d.getFullYear(), 0, 1); return String(Math.floor((d - start) / 86400000) + 1).padStart(3, '0'); }
    case 'm': return two(d.getMonth() + 1);
    case 'd': return two(d.getDate());
    case 'e': return String(d.getDate());
    case 'R': return `${two(H)}:${two(d.getMinutes())}`;
    case 'T': return `${two(H)}:${two(d.getMinutes())}:${two(d.getSeconds())}`;
    case 'r': return `${two(I)}:${two(d.getMinutes())}:${two(d.getSeconds())} ${H < 12 ? 'AM' : 'PM'}`;
    case 'D': return `${two(d.getMonth() + 1)}/${two(d.getDate())}/${two(d.getFullYear() % 100)}`;
    case 'F': return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())}`;
    case 'c': return `${DAYS[d.getDay()].slice(0, 3)} ${MONTHS[d.getMonth()].slice(0, 3)} ${two(d.getDate())} ${two(H)}:${two(d.getMinutes())}:${two(d.getSeconds())} ${d.getFullYear()}`;
    case 'Z': return Intl.DateTimeFormat(undefined, { timeZoneName: 'short' }).formatToParts(d).find((x) => x.type === 'timeZoneName')?.value ?? '';
    case 's': return String(Math.floor(d.getTime() / 1000));
    case 'Q': return String(d.getTime());
    default: return '%t' + c;
  }
}
