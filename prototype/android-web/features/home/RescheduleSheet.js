// Port of feature/home RescheduleSheet.kt.
import {
  C, Surface, Text, Row, Column, Button, OutlinedButton, TextButton, ButtonDefaults, Dialog, TimePicker, rememberTimePickerState,
} from '../../design/components.js';
import { R, stringResource, rememberMutable } from './_local.js';

/** RescheduleSheet({ block, onMoveToNextSlot, onMoveToTomorrow, onChooseTime, onSkip, onDismiss }) → overlay anchors */
export function RescheduleSheet({ block, onMoveToNextSlot, onMoveToTomorrow, onChooseTime, onSkip, onDismiss }) {
  const showTimePicker = rememberMutable('RescheduleSheet.showTimePicker', false);
  // rememberTimePickerState inside `if (showTimePicker)`: a fresh state per opening.
  const pickerOpenings = rememberMutable('RescheduleSheet.pickerOpenings', 0);
  const full = { class: 'fill-w', css: { width: '100%' } };

  const out = [Dialog({
    key: 'home:reschedule',
    onDismissRequest: onDismiss,
    children: Surface({
      shape: 'var(--radius-lg)', color: C.surfaceCard, class: 'fill-w', css: { width: '100%' },
      children: Column({ class: 'fill-w', css: { padding: 'var(--space-lg)' }, verticalArrangement: { spacedBy: 'var(--space-sm)' } },
        Text({ text: R.string.home_reschedule, style: 'titleMedium', color: C.text }),
        Text({
          text: stringResource(R.string.home_subject_title, block.subjectLabel, block.title),
          style: 'bodyMedium', color: C.textSecondary, css: { paddingBottom: 'var(--space-sm)' },
        }),
        Button({
          ...full, onClick: onMoveToNextSlot, shape: 'var(--radius-lg)',
          colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }),
        }, Text({ text: R.string.home_reschedule_next_slot })),
        OutlinedButton({
          ...full, onClick: onMoveToTomorrow, shape: 'var(--radius-lg)',
          colors: ButtonDefaults.outlinedButtonColors({ contentColor: C.text }),
        }, Text({ text: R.string.home_reschedule_tomorrow })),
        OutlinedButton({
          ...full, onClick: () => { pickerOpenings.value = pickerOpenings.value + 1; showTimePicker.value = true; }, shape: 'var(--radius-lg)',
          colors: ButtonDefaults.outlinedButtonColors({ contentColor: C.text }),
        }, Text({ text: R.string.home_reschedule_choose_time })),
        TextButton({ ...full, onClick: onSkip }, Text({ text: R.string.home_skip_block, color: C.danger })),
        TextButton({ ...full, onClick: onDismiss }, Text({ text: R.string.home_cancel })),
      ),
    }),
  })];

  if (showTimePicker.value) {
    const timePickerState = rememberTimePickerState({
      key: `home:rescheduleTime:${pickerOpenings.value}`,
      initialHour: Math.trunc(block.startMinuteOfDay / 60),
      initialMinute: block.startMinuteOfDay % 60,
      is24Hour: false,
    });
    out.push(Dialog({
      key: 'home:rescheduleTime',
      onDismissRequest: () => { showTimePicker.value = false; },
      children: Surface({
        shape: 'var(--radius-lg)', color: C.surfaceCard,
        children: Column({ css: { padding: 'var(--space-lg)' }, horizontalAlignment: 'CenterHorizontally' },
          TimePicker({ state: timePickerState }),
          Row({ class: 'fill-w', css: { paddingTop: 'var(--space-md)' }, horizontalArrangement: 'End' },
            TextButton({ onClick: () => { showTimePicker.value = false; } }, Text({ text: R.string.home_cancel })),
            TextButton({
              onClick: () => {
                showTimePicker.value = false;
                onChooseTime(timePickerState.hour * 60 + timePickerState.minute);
              },
            }, Text({ text: R.string.home_ok })),
          ),
        ),
      }),
    }));
  }
  return out;
}
