// Port of feature/syllabus/PriorityUi.kt
import { h } from '../../platform/h.js';
import { Column, Text, AlertDialog, TextButton, clickable } from '../../design/components.js';
import { PriorityBand } from '../../domain/ChapterPriority.js';
import { R, str } from './_local.js';

/** What the student taps to see where a chapter's yield comes from. */
export const PriorityDetail = (chapterTitle, priority) => Object.freeze({ chapterTitle, priority });

function bandLabel(band) {
  switch (band) {
    case PriorityBand.HIGH: return R.string.syllabus_yield_high;
    case PriorityBand.MEDIUM: return R.string.syllabus_yield_medium;
    case PriorityBand.LOW: return R.string.syllabus_yield_low;
    default: return R.string.syllabus_yield_no_data;
  }
}

/** "2021–25": the range of years the counts cover. */
export function pyqYearsLabel(firstYear, lastYear) {
  return firstYear <= 0 || lastYear <= 0 ? '' : `${firstYear}–${String(lastYear % 100).padStart(2, '0')}`;
}

const meanLabel = (mean) => mean.toFixed(1);

/** "High yield · ~4.2 Qs/yr (2021–25)", or "No PYQ data". Tap to see the sources. */
export function PriorityLabel({ priority, firstYear, lastYear, onClick, modifier }) {
  const mean = priority.meanQuestionsPerYear;
  const text = priority.band === PriorityBand.NO_DATA || mean == null
    ? R.string.syllabus_yield_no_data
    : str(R.string.syllabus_yield_with_rate, bandLabel(priority.band), meanLabel(mean), pyqYearsLabel(firstYear, lastYear));
  const high = priority.band === PriorityBand.HIGH;
  const el = Text({
    text,
    style: 'labelSmall',
    fontWeight: high ? 'SemiBold' : 'Normal',
    color: high ? 'var(--c-text)' : 'var(--c-textSecondary)',
    modifier,
    css: { padding: 'var(--space-xs) 0' },
  });
  // Modifier.clickable then padding: the clickable area includes the padding. Wrap so the ripple is bounded.
  const box = h('div', { style: { alignSelf: 'flex-start' } }, el);
  clickable(box, { onClick });
  return box;
}

export function PriorityDetailDialog({ detail, firstYear, lastYear, onDismiss }) {
  const priority = detail.priority;
  const mean = priority.meanQuestionsPerYear;
  const body = [];
  if (mean == null || priority.band === PriorityBand.NO_DATA) {
    body.push(Text(R.string.syllabus_yield_no_data_body));
  } else {
    body.push(Text({ text: bandLabel(priority.band), style: 'titleMedium' }));
    body.push(Text({
      text: str(R.string.syllabus_yield_rate_detail, meanLabel(mean), pyqYearsLabel(firstYear, lastYear), priority.yearsUsed),
      css: { paddingTop: 'var(--space-xs)' },
    }));
    if (priority.sourceNames.length > 0) {
      body.push(Text({ text: str(R.string.syllabus_yield_sources, priority.sourceNames.join(', ')), css: { paddingTop: 'var(--space-sm)' } }));
    }
  }
  body.push(Text({ text: R.string.syllabus_yield_method, style: 'bodySmall', color: 'var(--c-textMuted)', css: { paddingTop: 'var(--space-md)' } }));
  return AlertDialog({
    key: 'syllabus:priority',
    onDismissRequest: onDismiss,
    confirmButton: TextButton({ onClick: onDismiss, children: Text(R.string.syllabus_yield_close) }),
    title: Text(detail.chapterTitle),
    text: Column({}, ...body),
  });
}
