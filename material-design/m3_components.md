# Material 3 components

Use an existing Material component when it already fits the job. Do not invent a
new control that behaves like a standard button, field, picker, menu, or
navigation item.

## Choose by the job

### Start an action

| Need | Use | Simple rule |
|---|---|---|
| The main action on a screen | Filled button | Use one clear main action at a time. |
| A less important action | Outlined or text button | Do not make every action filled. |
| A small action with a familiar icon | Icon button | Give it an accessible label. |
| The main action that should stay available while scrolling | Floating action button | Use for one important creation or start action. |
| Pick one view, sort option, or mode | Segmented button | Use only when the choices are short and directly comparable. |

### Pick or enter information

| Need | Use | Simple rule |
|---|---|---|
| Enter text, a number, or a value | Text field | Keep the label visible. Use helper or error text when needed. |
| Pick one option from a short set | Radio buttons or segmented button | Show the selected option clearly. |
| Pick several independent options | Checkboxes | Each choice must stand on its own. |
| Turn one setting on or off | Switch | The label must say what changes. |
| Pick a value on a range | Slider | Show the value when precision matters. |
| Pick a date or date range | Date picker | Do not use a text field when a date picker is clearer. |
| Pick a time | Time picker | Use it for a clock time, not a duration. |
| Filter, choose, or act on compact content | Chip | Keep chip labels short. |
| Choose from a hidden list of actions | Menu | Use when showing all actions at once would add clutter. |

### Show content

| Need | Use | Simple rule |
|---|---|---|
| One clear item with related details or actions | Card | One card should describe one thing. Do not put every section in a card. |
| A long vertical set of similar items | List | Keep rows consistent. |
| Separate groups in a list | Divider | Use only when grouping needs help. |
| Show content that can be scrolled sideways | Carousel | Do not hide essential content in a carousel. |
| Show loading or progress | Progress indicator | Use an exact progress value when one is known. |
| Show a small count or status on an icon or navigation item | Badge | Keep the number short and useful. |

### Move around the app

| Need | Use | Simple rule |
|---|---|---|
| Move between 3 to 5 main destinations on a phone | Navigation bar | Keep the same destinations across screens. |
| Move between main destinations on a medium-width layout | Navigation rail | Put it on the side. |
| Move between many destinations or account/settings areas | Navigation drawer | Use a drawer when the list is too long for a bar or rail. |
| Move between closely related views on the same screen | Tabs | Do not use tabs for a multi-step task. |
| Give screen title, back action, and a few actions | Top app bar | Keep actions limited to the important ones. |
| Hold the screen's top bar, bottom bar, floating action, and content together | Scaffold | Use this as the screen frame in Compose. |

### Ask, confirm, or explain

| Need | Use | Simple rule |
|---|---|---|
| Confirm a destructive or important choice | Dialog | Ask only when interruption is worth it. |
| Show secondary content from the bottom of the screen | Bottom sheet | Use for a focused choice or short task, not a full new screen. |
| Show a short result after an action | Snackbar | Keep it brief. Offer Undo when it is useful. |
| Explain an unfamiliar icon or control | Tooltip | Do not use it to hide essential instructions. |

## Do not use the wrong component

- Do not use a dialog for a normal screen or a long form.
- Do not use a bottom sheet for a deep navigation flow.
- Do not use a card just to put a border around every section.
- Do not use a switch when the user is choosing between several options.
- Do not use a checkbox when only one option can be selected.
- Do not use a navigation bar for more than five equal main destinations.
- Do not use an icon alone when its meaning is not obvious.

## This repository

For Android, start with Material 3 Compose components inside the app theme. Use
the repository's shared design components when they already exist. Do not
rebuild a standard Material control from raw shapes, text, and click handlers.

For the prototype, match the visible intent of the chosen component. Do not
assume a prototype component should be ported to Android unless the user asks.

## Sources

- Material 3 components: <https://m3.material.io/components>
- Material components in Compose: <https://developer.android.com/develop/ui/compose/components>
