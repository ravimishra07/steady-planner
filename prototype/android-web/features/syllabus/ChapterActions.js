// Port of feature/syllabus/ChapterActions.kt
import { h } from '../../platform/h.js';
import { observe } from '../../platform/nav.js';
import { enumClass } from '../../domain/kt.js';
import { MAX_CHAPTER_TITLE_LENGTH } from '../../domain/ChapterTitles.js';
import {
  Column, Text, Icon, IconButton, DropdownMenu, DropdownMenuItem, AlertDialog, TextButton, OutlinedTextField,
} from '../../design/components.js';
import { R, str, rememberFor } from './_local.js';
import { remember } from '../../platform/nav.js';

/** What the chapter menu on a Syllabus row can do. */
export const ChapterAction = enumClass(['RENAME', 'RESET_NAME', 'PARK', 'COACHING']);

/**
 * Overflow menu on a chapter row: rename, reset name, park/unpark, coaching counts.
 * menuKey: stable id of the row (Compose keeps `open` per call site).
 */
export function ChapterMenuButton({ renamed, parked, onAction, showCoaching = false, menuKey }) {
  const open = remember('chapterMenu:' + menuKey, false);
  return observe(open, (isOpen) => {
    const box = h('div', { style: { position: 'relative', display: 'inline-flex', flex: 'none' } });
    box.append(
      IconButton({
        onClick: () => { open.value = true; },
        css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' },
        children: Icon({ imageVector: 'Filled.MoreVert', contentDescription: R.string.syllabus_chapter_menu, tint: 'var(--c-textSecondary)' }),
      }),
      DropdownMenu({
        expanded: isOpen,
        onDismissRequest: () => { open.value = false; },
        key: 'chapterMenu:' + menuKey,
        children: [
          DropdownMenuItem({ text: Text(R.string.syllabus_chapter_rename), onClick: () => { open.value = false; onAction(ChapterAction.RENAME); } }),
          renamed ? DropdownMenuItem({ text: Text(R.string.syllabus_chapter_reset_name), onClick: () => { open.value = false; onAction(ChapterAction.RESET_NAME); } }) : null,
          DropdownMenuItem({
            text: Text(parked ? R.string.syllabus_chapter_unpark : R.string.syllabus_chapter_park),
            onClick: () => { open.value = false; onAction(ChapterAction.PARK); },
          }),
          showCoaching ? DropdownMenuItem({ text: Text(R.string.syllabus_chapter_coaching), onClick: () => { open.value = false; onAction(ChapterAction.COACHING); } }) : null,
        ].filter(Boolean),
      }),
    );
    return box;
  }, { style: { display: 'contents' } });
}

/** Rename a chapter. Saving an empty name, or the original one, restores the original. openId: see rememberFor. */
export function RenameChapterDialog({ currentName, originalName, onSave, onDismiss, openId }) {
  const name = rememberFor('renameChapter:name', openId, currentName);
  // Only the text field reads `name`; keep the rest of the dialog static so typing never rebuilds it.
  const field = OutlinedTextField({
    key: 'renameChapter',
    value: name.value,
    onValueChange: (v) => {
      const next = v.slice(0, MAX_CHAPTER_TITLE_LENGTH);
      name.value = next;
      const input = document.activeElement;
      if (input && 'value' in input && input.value !== next) input.value = next;
    },
    singleLine: true,
    placeholder: Text(originalName),
  });
  return AlertDialog({
    key: 'syllabus:rename',
    onDismissRequest: onDismiss,
    title: Text(R.string.syllabus_chapter_rename),
    text: Column({},
      field,
      Text({ text: str(R.string.syllabus_chapter_original_name, originalName), style: 'bodySmall', color: 'var(--c-textSecondary)', css: { paddingTop: 'var(--space-sm)' } }),
      currentName !== originalName
        ? TextButton({ onClick: () => { onSave(null); onDismiss(); }, children: Text(R.string.syllabus_chapter_reset_name) })
        : null),
    confirmButton: TextButton({ onClick: () => { onSave(name.value); onDismiss(); }, children: Text(R.string.syllabus_stage_labels_save) }),
    dismissButton: TextButton({ onClick: onDismiss, children: Text(R.string.syllabus_stage_labels_cancel) }),
  });
}
