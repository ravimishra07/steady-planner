// colors.js — port of core/design AppColors.kt, AccentPalette.kt, BackgroundAppearance.kt,
// Palette.kt (generated hues) and Theme.kt colour resolution. Hex values copied from Kotlin.
// Colours are CSS strings: '#RRGGBB' or '#RRGGBBAA' (Kotlin 0xAARRGGBB re-ordered).
//
// API
//   AccentPalette.{Blue,Purple,Green,Amber,Rose}   { id, name, brand, brandSoft, brandDeep, onSwatch,
//                                                  primaryFor(dark), onPrimaryFor(dark), roles }
//   AccentPalette.Default, .entries, .fromId(id)
//   BackgroundAppearance.{System,Light,Dark,Grey,Slate}  { id, name, previewBackground, previewInk, isDark,
//                                                  resolved(systemDark) }
//   BackgroundAppearance.Default, .entries, .fromId(id)
//   resolveAppColors(background, palette, systemDark=false) → AppColors (all fields + every semantic alias
//                                                  as plain properties, e.g. colors.textMuted)
//   APP_COLOR_FIELDS / APP_COLOR_ALIASES           field name lists
//   systemDark()                                   prefers-color-scheme: dark
//   systemDarkFlow                                 StateFlow<boolean>
//   applyTheme(background, palette, root=document.documentElement) → AppColors
//                                                  writes `--c-<field>` for every field+alias on root;
//                                                  re-applies when system scheme changes (System appearance)
//   AppTheme.colors                                last applied AppColors (like AppTheme.colors)
//   appColorsFlow                                  StateFlow<AppColors>
//   withAlpha(color, a)                            Color.copy(alpha = a) for a hex colour
//   cvar(field)                                    'var(--c-<field>)'
//   calpha(field, a)                               CSS colour = var(--c-field) with alpha a (color-mix)
import { MutableStateFlow } from '../platform/flow.js';

const blueRoles = {
    light: {
        primary: '#315CAA', onPrimary: '#FFFFFF',
        primaryContainer: '#D7E3FF', onPrimaryContainer: '#00458F',
        inversePrimary: '#ABC7FF', primaryFixed: '#D7E3FF',
        primaryFixedDim: '#ABC7FF', onPrimaryFixed: '#001B3F',
        onPrimaryFixedVariant: '#00458F', secondary: '#565E71',
        onSecondary: '#FFFFFF', secondaryContainer: '#DAE2F9',
        onSecondaryContainer: '#3E4759', tertiary: '#006A6A',
        onTertiary: '#FFFFFF', tertiaryContainer: '#00FBFB',
        onTertiaryContainer: '#004F4F',
    },
    dark: {
        primary: '#B3C4FF', onPrimary: '#1C1B1F',
        primaryContainer: '#00458F', onPrimaryContainer: '#D7E3FF',
        inversePrimary: '#005CBB', primaryFixed: '#D7E3FF',
        primaryFixedDim: '#ABC7FF', onPrimaryFixed: '#001B3F',
        onPrimaryFixedVariant: '#00458F', secondary: '#BEC6DC',
        onSecondary: '#283041', secondaryContainer: '#3E4759',
        onSecondaryContainer: '#DAE2F9', tertiary: '#00DDDD',
        onTertiary: '#003737', tertiaryContainer: '#004F4F',
        onTertiaryContainer: '#00FBFB',
    },
};

const purpleRoles = {
    light: {
        primary: '#734D99', onPrimary: '#FFFFFF',
        primaryContainer: '#ECDCFF', onPrimaryContainer: '#5F00C0',
        inversePrimary: '#D5BAFF', primaryFixed: '#ECDCFF',
        primaryFixedDim: '#D5BAFF', onPrimaryFixed: '#270057',
        onPrimaryFixedVariant: '#5F00C0', secondary: '#645B70',
        onSecondary: '#FFFFFF', secondaryContainer: '#EADEF7',
        onSecondaryContainer: '#4B4357', tertiary: '#343DFF',
        onTertiary: '#FFFFFF', tertiaryContainer: '#E0E0FF',
        onTertiaryContainer: '#0000EF',
    },
    dark: {
        primary: '#DCB9F9', onPrimary: '#1C1B1F',
        primaryContainer: '#5F00C0', onPrimaryContainer: '#ECDCFF',
        inversePrimary: '#7D00FA', primaryFixed: '#ECDCFF',
        primaryFixedDim: '#D5BAFF', onPrimaryFixed: '#270057',
        onPrimaryFixedVariant: '#5F00C0', secondary: '#CEC2DB',
        onSecondary: '#352D40', secondaryContainer: '#4B4357',
        onSecondaryContainer: '#EADEF7', tertiary: '#BEC2FF',
        onTertiary: '#0001AC', tertiaryContainer: '#0000EF',
        onTertiaryContainer: '#E0E0FF',
    },
};

const greenRoles = {
    light: {
        primary: '#2F6B23', onPrimary: '#FFFFFF',
        primaryContainer: '#77FF61', onPrimaryContainer: '#015300',
        inversePrimary: '#02E600', primaryFixed: '#77FF61',
        primaryFixedDim: '#02E600', onPrimaryFixed: '#002200',
        onPrimaryFixedVariant: '#015300', secondary: '#54634D',
        onSecondary: '#FFFFFF', secondaryContainer: '#D7E8CD',
        onSecondaryContainer: '#3C4B37', tertiary: '#006D33',
        onTertiary: '#FFFFFF', tertiaryContainer: '#63FF94',
        onTertiaryContainer: '#005225',
    },
    dark: {
        primary: '#A6D397', onPrimary: '#1C1B1F',
        primaryContainer: '#015300', onPrimaryContainer: '#77FF61',
        inversePrimary: '#026E00', primaryFixed: '#77FF61',
        primaryFixedDim: '#02E600', onPrimaryFixed: '#002200',
        onPrimaryFixedVariant: '#015300', secondary: '#BBCBB2',
        onSecondary: '#263422', secondaryContainer: '#3C4B37',
        onSecondaryContainer: '#D7E8CD', tertiary: '#00E472',
        onTertiary: '#003917', tertiaryContainer: '#005225',
        onTertiaryContainer: '#63FF94',
    },
};

const amberRoles = {
    light: {
        primary: '#8F4D17', onPrimary: '#FFFFFF',
        primaryContainer: '#FFDCC7', onPrimaryContainer: '#723600',
        inversePrimary: '#FFB787', primaryFixed: '#FFDCC7',
        primaryFixedDim: '#FFB787', onPrimaryFixed: '#311300',
        onPrimaryFixedVariant: '#723600', secondary: '#755846',
        onSecondary: '#FFFFFF', secondaryContainer: '#FFDCC7',
        onSecondaryContainer: '#5B4130', tertiary: '#626200',
        onTertiary: '#FFFFFF', tertiaryContainer: '#EAEA00',
        onTertiaryContainer: '#494900',
    },
    dark: {
        primary: '#F9B98D', onPrimary: '#1C1B1F',
        primaryContainer: '#723600', onPrimaryContainer: '#FFDCC7',
        inversePrimary: '#964900', primaryFixed: '#FFDCC7',
        primaryFixedDim: '#FFB787', onPrimaryFixed: '#311300',
        onPrimaryFixedVariant: '#723600', secondary: '#E5BFA8',
        onSecondary: '#422B1B', secondaryContainer: '#5B4130',
        onSecondaryContainer: '#FFDCC7', tertiary: '#CDCD00',
        onTertiary: '#323200', tertiaryContainer: '#494900',
        onTertiaryContainer: '#EAEA00',
    },
};

const roseRoles = {
    light: {
        primary: '#A3365D', onPrimary: '#FFFFFF',
        primaryContainer: '#FFD9E1', onPrimaryContainer: '#8F0045',
        inversePrimary: '#FFB1C5', primaryFixed: '#FFD9E1',
        primaryFixedDim: '#FFB1C5', onPrimaryFixed: '#3F001B',
        onPrimaryFixedVariant: '#8F0045', secondary: '#74565D',
        onSecondary: '#FFFFFF', secondaryContainer: '#FFD9E1',
        onSecondaryContainer: '#5B3F46', tertiary: '#A900A9',
        onTertiary: '#FFFFFF', tertiaryContainer: '#FFD7F5',
        onTertiaryContainer: '#810081',
    },
    dark: {
        primary: '#FFADC5', onPrimary: '#1C1B1F',
        primaryContainer: '#8F0045', onPrimaryContainer: '#FFD9E1',
        inversePrimary: '#BA005C', primaryFixed: '#FFD9E1',
        primaryFixedDim: '#FFB1C5', onPrimaryFixed: '#3F001B',
        onPrimaryFixedVariant: '#8F0045', secondary: '#E3BDC5',
        onSecondary: '#422930', secondaryContainer: '#5B3F46',
        onSecondaryContainer: '#FFD9E1', tertiary: '#FFABF3',
        onTertiary: '#5B005B', tertiaryContainer: '#810081',
        onTertiaryContainer: '#FFD7F5',
    },
};

const lightNeutralRoles = {
    background: '#FEF8FC',
    onBackground: '#1D1B1E',
    surface: '#FEF8FC',
    onSurface: '#1D1B1E',
    surfaceVariant: '#E8E0EB',
    onSurfaceVariant: '#49454E',
    inverseSurface: '#323033',
    inverseOnSurface: '#F5EFF4',
    error: '#BA1A1A',
    onError: '#FFFFFF',
    errorContainer: '#FFDAD6',
    onErrorContainer: '#93000A',
    outline: '#7B757F',
    outlineVariant: '#CBC4CF',
    scrim: '#000000',
    surfaceBright: '#FEF8FC',
    surfaceDim: '#DED8DD',
    surfaceContainerLowest: '#FFFFFF',
    surfaceContainerLow: '#F8F2F6',
    surfaceContainer: '#F2ECF1',
    surfaceContainerHigh: '#EDE6EB',
    surfaceContainerHighest: '#E6E1E6',
};

const darkNeutralRoles = {
    background: '#151316',
    onBackground: '#E6E1E6',
    surface: '#151316',
    onSurface: '#E6E1E6',
    surfaceVariant: '#49454E',
    onSurfaceVariant: '#E8E0EB',
    inverseSurface: '#E6E1E6',
    inverseOnSurface: '#323033',
    error: '#FFB4AB',
    onError: '#690005',
    errorContainer: '#93000A',
    onErrorContainer: '#FFDAD6',
    outline: '#958E99',
    outlineVariant: '#49454E',
    scrim: '#000000',
    surfaceBright: '#3B383C',
    surfaceDim: '#151316',
    surfaceContainerLowest: '#0F0D11',
    surfaceContainerLow: '#1D1B1E',
    surfaceContainer: '#211F22',
    surfaceContainerHigh: '#2B292D',
    surfaceContainerHighest: '#363437',
};

export const SharedHues = Object.freeze({
  brand: '#7C3AED',
  brandSoft: '#B098FB',
  brandDeep: '#6D35C8',
  accentCyan: '#5BC8E6',
  success: '#34D399',
  successStrong: '#10BE84',
  warning: '#FFB74D',
  danger: '#EF4444',
  info: '#8B95D6',
  mood: '#4DD0E1',
  energy: '#81C784',
  sleep: '#9FA8DA',
  onBrand: '#FFFFFF',
  tabSelected: '#6D35C8'
});

export const DarkPalette = Object.freeze({
  bg: '#0A0A0F',
  bgDeep: '#080511',
  surface: '#1C1C24',
  surfaceTinted: '#151519',
  elevated: '#24242E',
  surface3: '#2A2A36',
  surfaceCard: '#1E1E28',
  surfaceControl: '#1A1B22',
  surfaceInk: '#121216',
  border: '#252530',
  borderSubtle: '#1A1B22',
  hairline: '#FFFFFF14',
  hairlineSoft: '#FFFFFF0F',
  text: '#FFFFFF',
  textSecondary: '#FFFFFFC7',
  textMuted: '#FFFFFF94',
  textDisabled: '#FFFFFF61',
  brandContainer: '#1A1530',
  successContainer: '#1B2D1B',
  dangerContainer: '#2D1B1B',
  dangerSoft: '#EEA3A3',
  dangerStripe: '#DC2626',
  glassTint: '#FFFFFF0E',
  glassStroke: '#FFFFFF24',
  tabBg: '#0A0A0FB8',
  tabUnselected: '#FFFFFF59',
  ctaBorder: '#B098FB3D',
  onSuccess: '#0A0A0F',
  infoTint: '#8B95D624',
  warningTint: '#FFB74D1F',
  warningRow: '#FFB74D0F'
});

export const LightPalette = Object.freeze({
  bg: '#FFFFFF',
  bgDeep: '#F5F7FA',
  surface: '#F0F4F8',
  surfaceTinted: '#EDE7FF',
  elevated: '#E8ECF2',
  surface3: '#E0E4EC',
  surfaceCard: '#F0F4F8',
  surfaceControl: '#E8ECF2',
  surfaceInk: '#F5F7FA',
  border: '#D8DEE8',
  borderSubtle: '#E3E8F0',
  hairline: '#00000014',
  hairlineSoft: '#0000000F',
  text: '#111827',
  textSecondary: '#374151',
  textMuted: '#4B5563',
  textDisabled: '#64748B',
  brandContainer: '#EDE7FF',
  successContainer: '#EAF8F0',
  dangerContainer: '#FDECEC',
  dangerSoft: '#B91C1C',
  dangerStripe: '#DC2626',
  glassTint: '#0000000A',
  glassStroke: '#0000001A',
  tabBg: '#FFFFFFEB',
  tabUnselected: '#11182761',
  ctaBorder: '#6D35C833',
  onSuccess: '#0A0A0F',
  infoTint: '#6D35C81A',
  warningTint: '#FFB74D33',
  warningRow: '#FFB74D1A'
});


// ---- AccentPalette ----------------------------------------------------------------------------
function accent(name, id, roles) {
  return Object.freeze({
    name, id, roles,
    get brand() { return roles.light.primary; },
    get brandSoft() { return roles.dark.primary; },
    get brandDeep() { return roles.light.primary; },
    get onSwatch() { return '#FFFFFF'; },
    primaryFor(dark) { return dark ? roles.dark.primary : roles.light.primary; },
    onPrimaryFor(dark) { return dark ? roles.dark.onPrimary : roles.light.onPrimary; },
    toString() { return name; },
  });
}
const _accents = [
  accent('Blue', 'blue', blueRoles),
  accent('Purple', 'purple', purpleRoles),
  accent('Green', 'green', greenRoles),
  accent('Amber', 'amber', amberRoles),
  accent('Rose', 'rose', roseRoles),
];
export const AccentPalette = Object.freeze({
  Blue: _accents[0], Purple: _accents[1], Green: _accents[2], Amber: _accents[3], Rose: _accents[4],
  entries: _accents,
  Default: _accents[1],
  fromId(id) {
    switch (id) {
      case 'violet': case 'indigo': return AccentPalette.Purple;
      case 'teal': case 'forest': return AccentPalette.Green;
      case 'ocean': return AccentPalette.Blue;
      case 'sunset': return AccentPalette.Amber;
      default: return _accents.find((p) => p.id === id) ?? AccentPalette.Default;
    }
  },
});

// ---- BackgroundAppearance --------------------------------------------------------------------
const PREVIEW_BG = { System: '#D8D7DD', Light: '#FCFCFF', Dark: '#0A0A0F', Grey: '#F3F4F6', Slate: '#111820' };
const PREVIEW_INK = { System: '#343239', Light: '#191C25', Dark: '#F4F3F8', Grey: '#1B1C20', Slate: '#F0F4F8' };
function appearance(name, id) {
  return {
    name, id,
    previewBackground: PREVIEW_BG[name],
    previewInk: PREVIEW_INK[name],
    isDark: name === 'Dark' || name === 'Slate',
    resolved(systemDark) {
      if (name === 'System') return systemDark ? BackgroundAppearance.Dark : BackgroundAppearance.Light;
      return this;
    },
    toString() { return name; },
  };
}
const _bgs = [
  appearance('System', 'system'), appearance('Light', 'light'), appearance('Dark', 'dark'),
  appearance('Grey', 'grey'), appearance('Slate', 'slate'),
];
_bgs.forEach(Object.freeze);
export const BackgroundAppearance = Object.freeze({
  System: _bgs[0], Light: _bgs[1], Dark: _bgs[2], Grey: _bgs[3], Slate: _bgs[4],
  entries: _bgs,
  Default: _bgs[2],
  fromId(id) { return _bgs.find((b) => b.id === id) ?? BackgroundAppearance.Default; },
});

function neutralRolesOf(bg) {
  switch (bg.name) {
    case 'System': case 'Light': return lightNeutralRoles;
    case 'Dark': return darkNeutralRoles;
    case 'Grey': return {
      ...lightNeutralRoles,
      surface: '#F3F4F6',
      surfaceContainerLowest: '#FFFFFF',
      surfaceContainerLow: '#EEF0F3',
      surfaceContainer: '#E9EAEE',
      surfaceContainerHigh: '#E2E4E9',
      surfaceContainerHighest: '#DCDEE4',
    };
    case 'Slate': return {
      ...darkNeutralRoles,
      surface: '#111820',
      surfaceContainerLowest: '#0C1218',
      surfaceContainerLow: '#18212A',
      surfaceContainer: '#1A232D',
      surfaceContainerHigh: '#202B36',
      surfaceContainerHighest: '#293542',
    };
  }
  return darkNeutralRoles;
}

// ---- AppColors -------------------------------------------------------------------------------
export const APP_COLOR_FIELDS = [
  'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer', 'inversePrimary', 'primaryFixed',
  'primaryFixedDim', 'onPrimaryFixed', 'onPrimaryFixedVariant', 'secondary', 'onSecondary',
  'secondaryContainer', 'onSecondaryContainer', 'tertiary', 'onTertiary', 'tertiaryContainer',
  'onTertiaryContainer', 'background', 'onBackground', 'surface', 'onSurface', 'surfaceVariant',
  'onSurfaceVariant', 'inverseSurface', 'inverseOnSurface', 'error', 'onError', 'errorContainer',
  'onErrorContainer', 'outline', 'outlineVariant', 'scrim', 'surfaceBright', 'surfaceDim',
  'surfaceContainerLowest', 'surfaceContainerLow', 'surfaceContainer', 'surfaceContainerHigh',
  'surfaceContainerHighest',
];

/** Color.copy(alpha = a) for "#RRGGBB" / "#RRGGBBAA" (replaces alpha, like Compose). */
export function withAlpha(color, a) {
  const rgb = color.slice(0, 7);
  const v = Math.round(Math.max(0, Math.min(1, a)) * 255);
  return rgb + v.toString(16).padStart(2, '0').toUpperCase();
}

/** Semantic aliases (getters in AppColors.kt), computed from the role fields. */
function aliases(c) {
  return {
    bg: c.surface,
    bgDeep: c.surfaceContainerLowest,
    surfaceTinted: c.primaryContainer,
    surfaceCard: c.surfaceContainerLow,
    surfaceControl: c.surfaceContainer,
    surfaceInk: c.surfaceContainerLowest,
    elevated: c.surfaceContainerHigh,
    surface3: c.surfaceContainerHighest,
    border: c.outlineVariant,
    borderSubtle: withAlpha(c.outlineVariant, 0.62),
    hairline: withAlpha(c.outlineVariant, 0.45),
    hairlineSoft: withAlpha(c.outlineVariant, 0.28),
    glassTint: withAlpha(c.onSurface, 0.06),
    glassStroke: withAlpha(c.onSurface, 0.14),
    ctaBorder: withAlpha(c.primary, 0.32),
    text: c.onSurface,
    textSecondary: c.onSurfaceVariant,
    textMuted: c.isDark ? c.onSurface : withAlpha(c.onSurfaceVariant, 0.80),
    textDisabled: withAlpha(c.onSurface, 0.38),
    tabBg: c.surfaceContainer,
    tabUnselected: c.onSurfaceVariant,
    selectionContainer: c.secondaryContainer,
    onSelectionContainer: c.onSecondaryContainer,
    brandContainer: c.primaryContainer,
    successContainer: c.tertiaryContainer,
    dangerContainer: c.errorContainer,
    dangerSoft: c.onErrorContainer,
    dangerStripe: c.error,
    infoTint: withAlpha(c.tertiaryContainer, 0.55),
    warningTint: withAlpha(c.secondaryContainer, 0.62),
    warningRow: withAlpha(c.secondaryContainer, 0.35),
    onSuccess: c.onTertiaryContainer,
    onBrandContainer: c.onPrimaryContainer,
    brand: c.primary,
    brandSoft: c.primaryFixedDim,
    brandDeep: c.primary,
    onBrand: c.onPrimary,
    tabSelected: c.primary,
    success: c.tertiary,
    successStrong: c.tertiary,
    warning: c.secondary,
    danger: c.error,
    info: c.tertiary,
    accentCyan: c.tertiary,
  };
}
export const APP_COLOR_ALIASES = Object.keys(aliases(Object.fromEntries(APP_COLOR_FIELDS.map((f) => [f, '#000000']))));

export function resolveAppColors(background, palette, systemDark = false) {
  const resolvedBackground = background.resolved(systemDark);
  const neutral = neutralRolesOf(resolvedBackground);
  const accent = resolvedBackground.isDark ? palette.roles.dark : palette.roles.light;
  const c = {
    ...accent,
    background: neutral.background,
    onBackground: neutral.onBackground,
    surface: neutral.surface,
    onSurface: neutral.onSurface,
    surfaceVariant: neutral.surfaceVariant,
    // No grey text on dark backgrounds: secondary text = primary text there.
    onSurfaceVariant: resolvedBackground.isDark ? neutral.onSurface : neutral.onSurfaceVariant,
    inverseSurface: neutral.inverseSurface,
    inverseOnSurface: neutral.inverseOnSurface,
    error: neutral.error,
    onError: neutral.onError,
    errorContainer: neutral.errorContainer,
    onErrorContainer: neutral.onErrorContainer,
    outline: neutral.outline,
    outlineVariant: neutral.outlineVariant,
    scrim: neutral.scrim,
    surfaceBright: neutral.surfaceBright,
    surfaceDim: neutral.surfaceDim,
    surfaceContainerLowest: neutral.surfaceContainerLowest,
    surfaceContainerLow: neutral.surfaceContainerLow,
    surfaceContainer: neutral.surfaceContainer,
    surfaceContainerHigh: neutral.surfaceContainerHigh,
    surfaceContainerHighest: neutral.surfaceContainerHighest,
    isDark: resolvedBackground.isDark,
  };
  return Object.freeze({ ...c, ...aliases(c) });
}

export const defaultAppColors = resolveAppColors(BackgroundAppearance.Default, AccentPalette.Default);

// ---- Theme application -----------------------------------------------------------------------
const mq = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
export function systemDark() { return !!mq?.matches; }
const _systemDark = new MutableStateFlow(systemDark());
export const systemDarkFlow = _systemDark.asStateFlow();
mq?.addEventListener?.('change', () => { _systemDark.value = systemDark(); });

const _colors = new MutableStateFlow(defaultAppColors);
export const appColorsFlow = _colors.asStateFlow();
export const AppTheme = { get colors() { return _colors.value; } };

let _applied = null; // {background, palette, root, unsub}
export function applyTheme(background = BackgroundAppearance.Default, palette = AccentPalette.Default,
  root = (typeof document !== 'undefined' ? document.documentElement : null)) {
  _applied?.unsub?.();
  const write = (dark) => {
    const colors = resolveAppColors(background, palette, dark);
    if (root) {
      for (const [k, v] of Object.entries(colors)) {
        if (typeof v === 'string') root.style.setProperty(`--c-${k}`, v);
      }
      root.dataset.dark = colors.isDark ? 'true' : 'false';
      root.style.colorScheme = colors.isDark ? 'dark' : 'light';
    }
    _colors.value = colors;
    return colors;
  };
  let first = true;
  let result;
  const unsub = systemDarkFlow.subscribe((dark) => {
    if (first) { result = write(dark); first = false; return; }
    if (background === BackgroundAppearance.System) write(dark);
  });
  _applied = { background, palette, root, unsub };
  return result;
}

export const cvar = (field) => `var(--c-${field})`;
export const calpha = (field, a) => `color-mix(in srgb, var(--c-${field}) ${+(a * 100).toFixed(2)}%, transparent)`;
