// Port of feature/syllabus/CoachingDialog.kt
// A chapter's coaching lectures and DPPs: two counts with +/−, and "done up to N" for each as one slider.
import { observe } from '../../platform/nav.js';
import { Column, Text, AlertDialog, TextButton, AppValueStepper, Slider } from '../../design/components.js';
import { ChapterCoaching, MAX_COACHING_COUNT } from '../../domain/CoachingBacklog.js';
import { R, str, rememberFor } from './_local.js';

/** openId: identity of this open (see _local.rememberFor). */
export function CoachingDialog({ chapterId, chapterName, initial, onSave, onDismiss, openId }) {
  const st = rememberFor('coaching', openId, () => ({
    lectures: initial?.lectures ?? 0,
    dpps: initial?.dpps ?? 0,
    lecturesDone: initial?.lecturesDone ?? 0,
    dppsDone: initial?.dppsDone ?? 0,
  }));
  const set = (patch) => { st.value = { ...st.value, ...patch }; };
  return observe(st, ({ lectures, dpps, lecturesDone, dppsDone }) => AlertDialog({
    key: 'syllabus:coaching',
    onDismissRequest: onDismiss,
    title: Text(R.string.syllabus_chapter_coaching),
    text: Column({ class: 'fill-w', verticalArrangement: { spacedBy: 'var(--space-sm)' } },
      Text({ text: chapterName, style: 'titleSmall', color: 'var(--c-textSecondary)' }),
      AppValueStepper({
        label: R.string.coaching_lectures,
        value: String(lectures),
        decreaseContentDescription: R.string.coaching_fewer_lectures,
        increaseContentDescription: R.string.coaching_more_lectures,
        // Kotlin reads the already-decremented `lectures` when clamping lecturesDone.
        onDecrease: () => { const l = Math.max(lectures - 1, 0); set({ lectures: l, lecturesDone: Math.min(lecturesDone, l) }); },
        onIncrease: () => set({ lectures: Math.min(lectures + 1, MAX_COACHING_COUNT) }),
        decreaseEnabled: lectures > 0,
        increaseEnabled: lectures < MAX_COACHING_COUNT,
      }),
      ...DoneUpTo({
        label: str(R.string.coaching_lectures_done_up_to, lecturesDone, lectures),
        done: lecturesDone, total: lectures, onChange: (v) => set({ lecturesDone: v }),
      }),
      AppValueStepper({
        label: R.string.coaching_dpps,
        value: String(dpps),
        decreaseContentDescription: R.string.coaching_fewer_dpps,
        increaseContentDescription: R.string.coaching_more_dpps,
        onDecrease: () => { const d = Math.max(dpps - 1, 0); set({ dpps: d, dppsDone: Math.min(dppsDone, d) }); },
        onIncrease: () => set({ dpps: Math.min(dpps + 1, MAX_COACHING_COUNT) }),
        decreaseEnabled: dpps > 0,
        increaseEnabled: dpps < MAX_COACHING_COUNT,
      }),
      ...DoneUpTo({
        label: str(R.string.coaching_dpps_done_up_to, dppsDone, dpps),
        done: dppsDone, total: dpps, onChange: (v) => set({ dppsDone: v }),
      })),
    confirmButton: TextButton({
      onClick: () => { onSave(ChapterCoaching({ chapterId, lectures, dpps, lecturesDone, dppsDone })); onDismiss(); },
      children: Text(R.string.syllabus_stage_labels_save),
    }),
    dismissButton: TextButton({ onClick: onDismiss, children: Text(R.string.syllabus_stage_labels_cancel) }),
  }), { style: { display: 'contents' } });
}

function DoneUpTo({ label, done, total, onChange }) {
  const out = [Text({ text: label, style: 'bodyMedium', color: 'var(--c-text)' })];
  if (total > 0) {
    out.push(Slider({
      value: done,
      onValueChange: (it) => onChange(Math.min(Math.max(Math.round(it), 0), total)),
      valueRange: [0, total],
      steps: Math.max(total - 1, 0),
      class: 'fill-w',
    }));
  }
  return out;
}
