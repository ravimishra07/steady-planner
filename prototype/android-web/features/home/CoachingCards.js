// Port of feature/home CoachingCards.kt.
import {
  C, Surface, Text, Row, Column, Button, TextButton, ButtonDefaults, AlertDialog, FilterChip, RadioButton,
  LazyRow, LazyColumn, AppValueStepper,
} from '../../design/components.js';
import { MAX_COACHING_COUNT } from '../../domain/CoachingBacklog.js';
import { formatInsightDate } from '../../domain/StudyAnalytics.js';
import { coerceAtLeast, coerceAtMost, distinctBy } from '../../domain/kt.js';
import { R, stringResource, rememberMutable } from './_local.js';

/**
 * "Taught in class today" (§4.5, §5.4): one tap moves the batch on a lecture in the same chapter;
 * "Different chapter" picks a chapter and lecture number.
 * TaughtTodayCard({ coaching, onNextLecture, onLogClass, css? })
 */
export function TaughtTodayCard(p) {
  const { coaching, onNextLecture, onLogClass } = p;
  const pickerOpen = rememberMutable('TaughtTodayCard.pickerOpen', false);
  const chapterTitle = coaching.batchChapterTitle;
  const card = Surface({
    shape: 'var(--radius-lg)', color: C.elevated, class: 'fill-w', css: p.css,
    children: Column({ class: 'fill-w', css: { padding: 'var(--space-lg)' }, verticalArrangement: { spacedBy: 'var(--space-sm)' } },
      Text({ text: R.string.home_coaching_taught_title, style: 'titleMedium', color: C.text }),
      Text({
        text: chapterTitle != null
          ? stringResource(R.string.home_coaching_batch_at, chapterTitle, coaching.batchLecture)
          : R.string.home_coaching_batch_unset,
        style: 'bodyMedium', color: C.textSecondary,
      }),
      Row({ horizontalArrangement: { spacedBy: 'var(--space-sm)' }, verticalAlignment: 'CenterVertically' },
        chapterTitle != null && coaching.batchLecture < MAX_COACHING_COUNT
          ? Button({
            onClick: onNextLecture,
            colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }),
            shape: 'var(--radius-pill)',
          }, Text({ text: stringResource(R.string.home_coaching_next_lecture, coaching.batchLecture + 1) }))
          : null,
        TextButton({ onClick: () => { pickerOpen.value = true; } },
          Text({ text: chapterTitle != null ? R.string.home_coaching_other_chapter : R.string.home_coaching_pick_chapter })),
      ),
    ),
  });
  const dialog = pickerOpen.value
    ? TaughtTodayDialog({ coaching, onSave: onLogClass, onDismiss: () => { pickerOpen.value = false; } })
    : null;
  return dialog ? [card, dialog] : card;
}

function TaughtTodayDialog({ coaching, onSave, onDismiss }) {
  const subjects = distinctBy(coaching.chapters.map((it) => [it.subjectId, it.subjectName]), (it) => `${it[0]}\u0000${it[1]}`);
  const initialSubject = coaching.chapters.find((it) => it.chapterId === coaching.batchChapterId)?.subjectId ?? subjects[0]?.[0] ?? null;
  const subjectId = rememberMutable('TaughtTodayDialog.subjectId', initialSubject);
  const chapterId = rememberMutable('TaughtTodayDialog.chapterId', coaching.batchChapterId);
  const lecture = rememberMutable('TaughtTodayDialog.lecture', coerceAtLeast(coaching.batchLecture, 1));
  const shown = coaching.chapters.filter((it) => it.subjectId === subjectId.value);
  return AlertDialog({
    key: 'home:taughtToday',
    onDismissRequest: onDismiss,
    title: Text({ text: R.string.home_coaching_taught_title }),
    text: Column({ class: 'fill-w', verticalArrangement: { spacedBy: 'var(--space-sm)' } },
      LazyRow({ class: 'fill-w', scrollKey: 'home-taught-subjects', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
        ...subjects.map((subject) => FilterChip({
          key: subject[0],
          selected: subject[0] === subjectId.value,
          onClick: () => { subjectId.value = subject[0]; },
          label: Text({ text: subject[1] }),
        }))),
      LazyColumn({ class: 'fill-w', scrollKey: 'home-taught-chapters', css: { maxHeight: 'var(--size-dialogListMaxHeight)' } },
        ...shown.map((chapter) => Row({ class: 'fill-w', key: chapter.chapterId, verticalAlignment: 'CenterVertically' },
          RadioButton({
            selected: chapter.chapterId === chapterId.value,
            onClick: () => {
              if (chapter.chapterId !== chapterId.value) lecture.value = 1;
              chapterId.value = chapter.chapterId;
            },
          }),
          Text({ text: chapter.title, style: 'bodyMedium', color: C.text }),
        ))),
      AppValueStepper({
        label: R.string.home_coaching_lecture_number,
        value: String(lecture.value),
        decreaseContentDescription: R.string.home_coaching_lecture_earlier,
        increaseContentDescription: R.string.home_coaching_lecture_later,
        onDecrease: () => { lecture.value = coerceAtLeast(lecture.value - 1, 1); },
        onIncrease: () => { lecture.value = coerceAtMost(lecture.value + 1, MAX_COACHING_COUNT); },
        decreaseEnabled: lecture.value > 1,
        increaseEnabled: lecture.value < MAX_COACHING_COUNT,
      }),
    ),
    confirmButton: TextButton({
      enabled: chapterId.value != null,
      onClick: () => {
        if (chapterId.value != null) onSave(chapterId.value, lecture.value);
        onDismiss();
      },
    }, Text({ text: R.string.home_coaching_save })),
    dismissButton: TextButton({ onClick: onDismiss }, Text({ text: R.string.home_coaching_cancel })),
  });
}

/** Lectures and DPPs behind the batch, by subject, and the clear date at the current split. CoachingBacklogCard({ coaching, css? }) */
export function CoachingBacklogCard(p) {
  const { coaching } = p;
  const rows = [Text({ text: R.string.home_coaching_backlog_title, style: 'titleMedium', color: C.text })];
  if (coaching.backlog.length === 0) {
    rows.push(Text({ text: R.string.home_coaching_backlog_none, style: 'bodyMedium', color: C.textSecondary }));
  } else {
    for (const row of coaching.backlog) {
      rows.push(Text({ text: stringResource(R.string.home_coaching_backlog_row, row.subjectName, row.lectures, row.dpps), style: 'bodyMedium', color: C.text }));
    }
    const clear = coaching.clearDate;
    rows.push(Text({
      text: clear != null
        ? stringResource(R.string.home_coaching_clear_by, formatInsightDate(clear), coaching.liveSplit, 100 - coaching.liveSplit)
        : R.string.home_coaching_no_clear_date,
      style: 'bodySmall', color: C.textSecondary, css: { paddingTop: 'var(--space-xs)' },
    }));
  }
  return Surface({
    shape: 'var(--radius-lg)', color: C.elevated, class: 'fill-w', css: p.css,
    children: Column({ class: 'fill-w', css: { padding: 'var(--space-lg)' }, verticalArrangement: { spacedBy: 'var(--space-xs)' } }, ...rows),
  });
}
