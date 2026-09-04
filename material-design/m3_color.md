# Material 3 color

Use named colors for what a color does. Do not choose a new hex value for each
screen or component.

For every filled area, use its matching `on...` color for text and icons. This
is the core Material 3 rule:

| Fill | Text and icons on it |
|---|---|
| `primary` | `onPrimary` |
| `primaryContainer` | `onPrimaryContainer` |
| `secondary` | `onSecondary` |
| `secondaryContainer` | `onSecondaryContainer` |
| `tertiary` | `onTertiary` |
| `tertiaryContainer` | `onTertiaryContainer` |
| `surface` | `onSurface` |
| `error` | `onError` |
| `errorContainer` | `onErrorContainer` |

Never mix a fill from one row with text from another row. For example, do not
put `primaryContainer` text on a `tertiaryContainer` button.

## Pick the right color

| Color name | Use it for | Do not use it for |
|---|---|---|
| `primary` | The main button, selected state, active control, or most important action. | Every card, every icon, or ordinary body text. |
| `primaryContainer` | A selected card, highlighted area, or important but softer block. | The default background for the whole screen. |
| `secondary` | A less important accent, such as a filter chip. | The primary action. |
| `secondaryContainer` | A quieter accented block. | The main call to action. |
| `tertiary` / `tertiaryContainer` | A clearly different accent when primary and secondary are already in use. | Decoration with no meaning. |
| `background` | The page behind the content. | A card or dialog. |
| `surface` | Cards, sheets, menus, and other content areas. | Text and icons. |
| `surfaceContainerLow` | A large contained area that should sit quietly above the page. | The most important card. |
| `surfaceContainer` | The normal contained area, such as a card or panel. | The whole page. |
| `surfaceContainerHigh` | A contained area that needs more separation than a normal card. | Every card on the screen. |
| `surfaceContainerHighest` | The strongest contained neutral area. | A main action, which should normally use primary. |
| `onSurface` | Main text and icons on a surface. | A colored primary or error fill. |
| `onSurfaceVariant` | Secondary text and less important icons on a surface. | Main headings or primary actions. |
| `outline` | A border that needs to be clearly visible. | Text, fills, or decoration. |
| `outlineVariant` | A very quiet decorative divider or border. | A required boundary or focus state. |
| `error` | An error action, error icon, or strong error state. | A warning, success, or ordinary accent. |
| `errorContainer` | A softer error message area. | The destructive button itself. |
| `scrim` | The dim layer behind a dialog, sheet, or modal surface. | A normal page background. |

## Light and dark mode

Do not reuse the same raw colors in light and dark mode. Use the same names,
but give each name the correct value for that theme.

Example: both themes use `primary` for the main button. The light-theme button
and dark-theme button can be different actual colors, while the code stays the
same.

## How to color a normal screen

1. Put `background` behind the whole page.
2. Put content in `surface` or one of the `surfaceContainer...` colors.
3. Use `onSurface` for main text and `onSurfaceVariant` for supporting text.
4. Use one `primary` action per decision point.
5. Use `primaryContainer` only when selection or extra emphasis needs to be
   visible.
6. Use `error` colors only when something is actually wrong.

Most of the screen should be background and surface colors. Accent colors are
for meaning: an action, selection, status, or a clear point of attention.

## Before adding a color

Ask these questions in order:

1. Is this a page, a content area, text, an action, a selection, or an error?
2. Does an existing named color already cover that job?
3. What text and icon color belongs on this fill?
4. Will the same named color work in both light and dark mode?
5. Does the color communicate something useful, rather than just decorate?

If an existing named color answers the question, reuse it. Do not add a new
color just to make one screen look different.

## This repository

For Android, use `AppTheme.colors` in feature UI. For Material components, use
the color scheme provided by the app theme. Do not write `Color(0x...)` in
feature code.

For the HTML prototype, use the existing `--sam-*` color variables. Do not add
one-off hex values to a screen.

`tokens.json` is a generated export. Do not hand-edit it. Keep all color
changes in the shared design system, then regenerate the Android palette as the
repository instructions require.

## Sources

- Material 3 color system: <https://m3.material.io/styles/color/system/overview>
- Material 3 color use in Compose: <https://developer.android.com/develop/ui/compose/designsystems/material3>
