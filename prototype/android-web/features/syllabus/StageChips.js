// Port of feature/syllabus/StageChips.kt
import { h } from '../../platform/h.js';
import { combine } from '../../platform/flow.js';
import { observe } from '../../platform/nav.js';
import {
  Row, Column, Text, Icon, AlertDialog, TextButton, Checkbox, OutlinedTextField, ProLockIcon,
} from '../../design/components.js';
import { ChapterStage, StageLabels } from '../../domain/ChapterStage.js';
import { R, str, combinedClickable, rememberFor } from './_local.js';

export function defaultStageLabel(stage) {
  switch (stage) {
    case ChapterStage.LEARNED: return R.string.syllabus_stage_learned;
    case ChapterStage.NCERT: return R.string.syllabus_stage_ncert;
    case ChapterStage.PRACTICE: return R.string.syllabus_stage_practice;
    case ChapterStage.PYQ: return R.string.syllabus_stage_pyq;
    case ChapterStage.REVISED: return R.string.syllabus_stage_revised;
    default: return R.string.syllabus_stage_custom;
  }
}

/** One-tap stage chips on a chapter row. No dialog: a tap is the tick. FlowRow spacedBy(xs) both ways. */
export function StageChipRow({ chapterId, chips, onTapStage, onUndoRevision, modifier }) {
  return Row({
    modifier,
    css: { flexWrap: 'wrap', columnGap: 'var(--space-xs)', rowGap: 'var(--space-xs)' },
  }, ...chips.map((chip) => {
    const name = chip.customLabel ?? defaultStageLabel(chip.stage);
    const label = chip.stage === ChapterStage.REVISED && chip.count > 0
      ? str(R.string.syllabus_stage_revised_count, name, chip.count) : name;
    return StageChip({
      label,
      selected: chip.count > 0,
      onClick: () => onTapStage(chapterId, chip.stage),
      onLongClick: chip.stage === ChapterStage.REVISED ? () => onUndoRevision(chapterId) : null,
    });
  }));
}

function StageChip({ label, selected, onClick, onLongClick }) {
  const el = h('div', { class: 'surface' });
  Object.assign(el.style, {
    height: 'var(--size-compactControl)', borderRadius: 'var(--radius-small)', boxSizing: 'border-box',
    background: selected ? 'var(--c-selectionContainer)' : 'transparent',
    border: selected ? 'none' : 'var(--stroke-hairline) solid var(--c-outlineVariant)',
    color: selected ? 'var(--c-onSelectionContainer)' : 'var(--c-textSecondary)',
    display: 'flex', alignItems: 'center', flex: 'none',
  });
  el.append(Row({
    verticalAlignment: 'CenterVertically',
    horizontalArrangement: { spacedBy: 'var(--space-xxs)' },
    css: { padding: '0 var(--space-sm)' },
  },
  selected ? Icon({ imageVector: 'Filled.Check', contentDescription: null, tint: 'var(--c-onSelectionContainer)', size: 'var(--size-smallIcon)' }) : null,
  Text({ text: label, style: 'labelMedium', fontWeight: 'Medium', color: selected ? 'var(--c-onSelectionContainer)' : 'var(--c-textSecondary)' })));
  return combinedClickable(el, { onClick, onLongClick });
}

const MAX_LABEL_LENGTH = 24;

/**
 * Rename or hide each stage, and name the one custom stage.
 * openId: the identity of this open (see _local.rememberFor) so state is fresh per open.
 */
export function StageLabelsDialog({ labels, onSave, onDismiss, customLocked = false, onUnlockCustom = () => {}, openId }) {
  const names = rememberFor('stageLabels:names', openId, () => new Map(ChapterStage.entries.map((s) => [s, labels.labelFor(s) ?? ''])));
  const shown = rememberFor('stageLabels:shown', openId, () => new Map(ChapterStage.entries.map((s) => [s, !labels.hidden.has(s)])));
  const dirty = rememberFor('stageLabels:dirty', openId, false);
  return observe(combine([names, shown, dirty], (n, s, d) => ({ n, s, d })), ({ n, s, d }) => AlertDialog({
    key: 'syllabus:stageLabels',
    onDismissRequest: onDismiss,
    title: Text(R.string.syllabus_stage_labels_title),
    text: Column({ class: 'fill-w' },
      Text({ text: R.string.syllabus_stage_labels_body, style: 'bodyMedium', color: 'var(--c-textSecondary)' }),
      ...ChapterStage.entries.map((stage) => {
        if (stage === ChapterStage.CUSTOM && customLocked) {
          return TextButton({
            onClick: onUnlockCustom, class: 'fill-w', css: { paddingTop: 'var(--space-sm)' },
            children: [ProLockIcon({ css: { marginRight: 'var(--space-sm)' } }), Text({ text: R.string.syllabus_stage_custom_pro, class: 'weight' })],
          });
        }
        return Row({ class: 'fill-w', css: { paddingTop: 'var(--space-sm)' }, verticalAlignment: 'CenterVertically' },
          Checkbox({
            checked: s.get(stage) === true,
            onCheckedChange: (v) => { const m = new Map(s); m.set(stage, v); shown.value = m; dirty.value = true; },
          }),
          OutlinedTextField({
            key: 'stageLabel:' + stage.name,
            value: n.get(stage) ?? '',
            onValueChange: (v) => { const m = new Map(n); m.set(stage, v.slice(0, MAX_LABEL_LENGTH)); names.value = m; dirty.value = true; },
            singleLine: true,
            placeholder: Text(defaultStageLabel(stage)),
            class: 'weight',
          }));
      })),
    confirmButton: TextButton({
      enabled: d,
      onClick: () => {
        const renamed = new Map([...n].filter(([k, v]) => k !== ChapterStage.CUSTOM && v.trim() !== ''));
        const hidden = new Set([...s].filter(([, v]) => !v).map(([k]) => k));
        const custom = n.get(ChapterStage.CUSTOM);
        onSave(StageLabels({
          renamed, hidden,
          customLabel: custom != null && custom.trim() !== '' && !customLocked ? custom : null,
        }));
        onDismiss();
      },
      children: Text(R.string.syllabus_stage_labels_save),
    }),
    dismissButton: TextButton({ onClick: onDismiss, children: Text(R.string.syllabus_stage_labels_cancel) }),
  }), { style: { display: 'contents' } });
}
