# Material 3 spacing

Use this file when choosing spacing in a design or implementing a layout.

This guide follows Material 3's grid rule: use an **8dp grid** for layouts,
components, and ordinary gaps. Use **4dp** only for smaller details inside a
component, such as an icon beside text.

On Android, write spacing in `dp`. For font size, use `sp`, not `dp`. In the
HTML prototype, use the matching CSS pixel value through an existing spacing
variable instead of a new one-off number.

## The rule in one line

Pick spacing from this scale, then reuse the same named value everywhere the
same relationship appears:

| Token | Value | Use it for |
|---|---:|---|
| `space-0` | 0dp | No gap. Use only when items must touch. |
| `space-1` | 4dp | Tiny internal adjustment: icon-to-label, small text stacks. |
| `space-2` | 8dp | Standard gap between closely related items. |
| `space-3` | 12dp | Compact internal padding or a slightly relaxed item gap. |
| `space-4` | 16dp | Default screen inset and normal component padding. |
| `space-5` | 20dp | Comfortable card padding when 16dp is too tight. |
| `space-6` | 24dp | Gap between groups or sections; roomy component padding. |
| `space-8` | 32dp | Strong separation between major sections. |
| `space-10` | 40dp | Large screen breathing room, not ordinary list spacing. |
| `space-12` | 48dp | Deliberate large separation or touch-target-related layout room. |
| `space-16` | 64dp | Major page-level separation only. |

Do not choose values such as 5dp, 7dp, 10dp, 14dp, 18dp, or 22dp just to make
a screen look right. First try the closest value from the scale. If the exact
component guidance specifies another value, follow that component guidance.

## How to apply it

### 1. Start with the screen edge

For a compact phone layout, start with `16dp` (`space-4`) horizontal content
insets unless the component or system bar requires something different. Keep
the left and right inset equal unless there is a real layout reason not to.

### 2. Space by relationship, not by visual guesswork

Use the smallest gap for items that belong together and a larger gap when the
reader should see a new group.

| Relationship | Starting value |
|---|---:|
| Icon and its label | 4dp or 8dp |
| Label and its field | 8dp |
| Items in one list | 8dp |
| Related controls in one group | 8dp or 12dp |
| Inside a standard card or input area | 16dp |
| Between form groups or content sections | 24dp |
| Between major page regions | 32dp or more |

Example: a screen title, its supporting text, and the first form group are not
three equal gaps. Keep the title and supporting text closer (`8dp`), then use
`24dp` before the form group so the hierarchy is clear.

### 3. Use padding and gaps for different jobs

- **Padding** is empty space inside a component, between its edge and content.
  Example: `16dp` inside a card.
- **Gap** is empty space between siblings in a row, column, or list.
  Example: `8dp` between list rows.
- **Margin / outer inset** is empty space between a component and its parent or
  the screen edge. Example: `16dp` screen padding.

Do not stack all three without a reason. If a card already has `16dp` internal
padding, do not add another unexplained `16dp` wrapper around the same content.

## Tokens in this repository

Use the existing Android `Spacing` tokens before adding a new value:

```kotlin
Spacing.xs   // 4.dp
Spacing.sm   // 8.dp
Spacing.md   // 12.dp
Spacing.lg   // 16.dp
Spacing.xl   // 20.dp
Spacing.xxl  // 24.dp
```

For Android UI, use these tokens in `padding`, `spacedBy`, and layout gaps. Do
not write raw `12.dp`, `16.dp`, or `24.dp` in feature UI code.

For the HTML prototype, use the existing `--sam-space-*` variables. Do not add
an inline pixel value when a matching variable already exists.

If a needed value is not in the shared scale, first decide whether the design
can use an existing value. Add a new shared token only when it represents a
reusable spacing role, not a one-screen adjustment.

## Checks before finishing a spacing change

1. Are related items closer together than unrelated groups?
2. Do repeated rows and cards use the same gap and padding?
3. Are all normal layout values on the 8dp grid, with 4dp reserved for small
   details?
4. Does the layout still work when text becomes larger or wraps?
5. Did you reuse a shared token instead of adding a raw number?

## Sources

- Material Design 3 spacing overview: <https://m3.material.io/styles/spacing/overview>
- Android grid and unit guidance: <https://developer.android.com/design/ui/mobile/guides/layout-and-content/grids-and-units>
