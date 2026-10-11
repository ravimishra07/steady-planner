// Port of feature/settings/AppearanceScreen.kt. The shell recomposes this destination when the theme changes
// (themeFlow), so `background` / `accentPalette` props are always current; isSystemInDarkTheme() → systemDark().
import { h } from '../../platform/h.js';
import { AccentPalette, BackgroundAppearance, resolveAppColors, systemDark as isSystemInDarkTheme } from '../../design/colors.js';
import {
  Row, Column, Box, Text, Icon, IconButton, Surface, HorizontalDivider, AppShapes, CircleShape, clickable,
} from '../../design/components.js';
import { stringResource, ScrollColumn } from './_local.js';

/** Native, inset-safe counterpart of the prototype's dedicated Appearance page. */
export function AppearanceScreen({ background, onBackground, accentPalette, onAccentPalette, onBack, modifier }) {
  const systemDark = isSystemInDarkTheme();
  const themeRows = [];
  BackgroundAppearance.entries.forEach((option, index) => {
    themeRows.push(BackgroundAppearanceRow({
      option, selected: option === background, accentPalette, systemDark, onClick: () => onBackground(option),
    }));
    if (index !== BackgroundAppearance.entries.length - 1) themeRows.push(HorizontalDivider({ color: 'var(--c-borderSubtle)' }));
  });
  const themeGroup = Surface({
    shape: AppShapes.card, color: 'var(--c-surfaceCard)', tonalElevation: 'var(--elev-cardTonal)', class: 'fill-w',
    children: Column({ class: 'fill-w' }, ...themeRows),
  });
  themeGroup.setAttribute('role', 'radiogroup');
  const accentGroup = Surface({
    shape: AppShapes.card, color: 'var(--c-surfaceCard)', tonalElevation: 'var(--elev-cardTonal)', class: 'fill-w',
    children: Row({ class: 'fill-w', css: { padding: 'var(--space-lg)' }, horizontalArrangement: 'SpaceBetween', verticalAlignment: 'CenterVertically' },
      ...AccentPalette.entries.map((palette) => AccentChoice({ palette, selected: palette === accentPalette, onClick: () => onAccentPalette(palette) }))),
  });
  accentGroup.setAttribute('role', 'radiogroup');
  return ScrollColumn({
    modifier,
    outerCss: { background: 'var(--c-bg)' },
    css: { padding: '0 var(--space-screen)', background: 'var(--c-bg)' },
    scrollKey: 'appearance-scroll',
    children: [
      Row({ class: 'fill-w', css: { minHeight: 'var(--size-topAppBarHeight)' }, verticalAlignment: 'CenterVertically' },
        IconButton({ onClick: onBack, css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' },
          children: Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: stringResource('settings_back'), tint: 'var(--c-text)' }) }),
        Text({ text: stringResource('settings_appearance'), style: 'headlineSmall', color: 'var(--c-text)', css: { paddingLeft: 'var(--space-sm)' } })),
      AppearanceSectionTitle(stringResource('settings_theme_title')),
      themeGroup,
      AppearanceSectionTitle(stringResource('settings_theme_color')),
      accentGroup,
      Text({ text: stringResource('settings_theme_description'), style: 'bodySmall', color: 'var(--c-textSecondary)', css: { padding: 'var(--space-md) 0 var(--space-xxl)' } }),
    ],
  });
}

function AppearanceSectionTitle(text) {
  return Text({ text, style: 'titleSmall', color: 'var(--c-text)', css: { padding: 'var(--space-xl) 0 var(--space-sm)' } });
}

function BackgroundAppearanceRow({ option, selected, accentPalette, systemDark, onClick }) {
  const preview = resolveAppColors(option, accentPalette, systemDark);
  const swatch = Box({ css: {
    width: 'var(--size-appearanceModeSwatch)', height: 'var(--size-appearanceModeSwatch)', flex: 'none',
    borderRadius: 'var(--radius-sm)', background: preview.bg, border: `var(--stroke-hairline) solid ${preview.border}`, overflow: 'hidden',
  } });
  const row = Row({
    class: 'fill-w', verticalAlignment: 'CenterVertically',
    css: {
      minHeight: 'var(--size-appearanceRow)', padding: 'var(--space-sm) var(--space-lg)',
      background: selected ? 'var(--c-selectionContainer)' : 'var(--c-surfaceCard)',
      color: selected ? 'var(--c-onSelectionContainer)' : 'var(--c-text)',
    },
  },
  swatch,
  Text({ text: stringResource(labelRes(option)), style: 'bodyLarge', color: selected ? 'var(--c-onSelectionContainer)' : 'var(--c-text)', class: 'weight', css: { paddingLeft: 'var(--space-lg)' } }),
  selected
    ? Icon({ imageVector: 'Filled.CheckCircle', contentDescription: null, tint: 'var(--c-brandDeep)' })
    : h('div', { style: { width: 'var(--size-standardIcon)', height: 'var(--size-standardIcon)', flex: 'none' } }));
  row.setAttribute('data-testid', 'appearance-' + option.id);
  return clickable(row, { role: 'radio', checked: selected, selected, onClick });
}

function AccentChoice({ palette, selected, onClick }) {
  const el = Box({
    contentAlignment: 'Center',
    css: {
      width: 'var(--size-themeSwatch)', height: 'var(--size-themeSwatch)', flex: 'none', borderRadius: CircleShape,
      background: palette.brand,
      boxShadow: `inset 0 0 0 ${selected ? 'var(--stroke-selected)' : 'var(--stroke-hairline)'} ${selected ? 'var(--c-text)' : 'var(--c-border)'}`,
      overflow: 'hidden', color: 'var(--c-onBrand)',
    },
  }, selected ? Icon({ imageVector: 'Filled.Check', contentDescription: null, tint: 'var(--c-onBrand)' }) : null);
  el.setAttribute('aria-label', stringResource(paletteLabelRes(palette)));
  el.setAttribute('data-testid', 'accent-' + palette.id);
  return clickable(el, { role: 'radio', checked: selected, selected, onClick });
}

function paletteLabelRes(p) {
  switch (p) {
    case AccentPalette.Blue: return 'settings_palette_blue';
    case AccentPalette.Purple: return 'settings_palette_purple';
    case AccentPalette.Green: return 'settings_palette_green';
    case AccentPalette.Amber: return 'settings_palette_amber';
    default: return 'settings_palette_rose';
  }
}

function labelRes(b) {
  switch (b) {
    case BackgroundAppearance.System: return 'settings_theme_system';
    case BackgroundAppearance.Light: return 'settings_theme_light';
    case BackgroundAppearance.Dark: return 'settings_theme_dark';
    case BackgroundAppearance.Grey: return 'settings_background_grey';
    default: return 'settings_background_slate';
  }
}
