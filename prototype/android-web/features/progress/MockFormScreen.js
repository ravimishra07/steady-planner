// Port of feature/progress/MockFormScreen.kt — MockFormRoute, MockFormActions, MockFormScreen, MistakeLog.
// Web-only: MockFormActions carries `current()` (the ViewModel's latest state) so a keystroke the ViewModel
// rejects is put back into the input (Compose's controlled TextField does this by itself).
import { viewModel, LaunchedEffect, remember, observe } from '../../platform/nav.js';
import {
  C, Column, Row, Box, Spacer, Text, Icon, IconButton, Button, OutlinedButton, TextButton, FilterChip,
  HorizontalDivider, AlertDialog, LazyColumn, OutlinedTextField, AppCard, ProLockCard, clickable,
} from '../../design/components.js';
import { MockSubject, MockTestType, MistakeType } from '../../domain/MockTest.js';
import { chunked } from '../../domain/kt.js';
import { formatInsightDate } from '../../domain/StudyAnalytics.js';
import { MockFormViewModel } from './MockFormViewModel.js';
import { mockTypeLabel, subjectLabel } from './TestsScreen.js';
import { SessionDatePicker } from './LogSessionScreen.js';
import { R } from './strings.js';
import { RouteHost, noShrink, keepFieldValue } from './_local.js';

const sp = (k) => `var(--space-${k})`;
const sz = (k) => `var(--size-${k})`;

export function MockFormRoute({
  mockId, examPackRepository, attemptRepository, mockTestRepository, proRepository, onDone, modifier, onOpenPaywall = () => {},
}) {
  const vm = viewModel(`mock-form-${mockId ?? 'new'}`, () =>
    new MockFormViewModel(mockId, examPackRepository, attemptRepository, mockTestRepository, proRepository));
  const actions = MockFormActions({
    onBack: onDone,
    onName: (v) => vm.setName(v),
    onType: (v) => vm.setType(v),
    onDate: (v) => vm.setDate(v),
    onSubjectScore: (s, v) => vm.setSubjectScore(s, v),
    onTotal: (v) => vm.setTotal(v),
    onMaxTotal: (v) => vm.setMaxTotal(v),
    onAttempted: (v) => vm.setAttempted(v),
    onCorrect: (v) => vm.setCorrect(v),
    onWrong: (v) => vm.setWrong(v),
    onTagChapter: (id) => vm.tagChapter(id),
    onMistakeType: (id, t) => vm.setMistakeType(id, t),
    onRemoveMistake: (id) => vm.removeMistake(id),
    onSave: () => vm.save(),
    onDelete: () => vm.delete(),
    onOpenPaywall,
    current: () => vm.state.value,
  });
  return RouteHost(vm.state, (state) => {
    LaunchedEffect([state.finished], () => { if (state.finished) onDone(); });
    return MockFormScreen({ state, actions, modifier });
  });
}

/** Every callback the form needs, kept in one value so the screen signature stays readable. */
export const MockFormActions = (a) => Object.freeze({ onOpenPaywall: () => {}, current: null, ...a });

export function MockFormScreen({ state, actions, modifier }) {
  const confirmDelete = remember('mockForm:confirmDelete', false);
  const header = Row({ class: 'fill-w', css: { padding: `${sp('sm')} ${sp('xs')}` }, verticalAlignment: 'CenterVertically' },
    IconButton({ onClick: actions.onBack, children: Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: R.string.day_log_back, tint: C.textSecondary }) }),
    Text({ text: state.isEdit ? R.string.mock_form_edit_title : R.string.mock_form_title, style: 'titleLarge', color: C.text, fontWeight: 'SemiBold', class: 'weight' }),
    state.isEdit && !state.unavailable
      ? IconButton({ onClick: () => { confirmDelete.value = true; }, enabled: !state.saving, children: Icon({ imageVector: 'Outlined.Delete', contentDescription: R.string.mock_form_delete, tint: C.danger }) })
      : null,
  );
  let body;
  if (state.loading) body = FormMessage(R.string.insights_loading);
  else if (state.unavailable) body = FormMessage(R.string.mock_form_unavailable);
  else body = MockFormFields(state, actions);
  return Column({ modifier, class: 'weight fill-w' },
    header,
    body,
    observe(confirmDelete, (open) => (open ? AlertDialog({
      key: 'deleteMock',
      onDismissRequest: () => { confirmDelete.value = false; },
      title: Text(R.string.mock_form_delete_title),
      text: Text(R.string.mock_form_delete_body),
      confirmButton: TextButton({ onClick: () => { confirmDelete.value = false; actions.onDelete(); }, children: Text({ text: R.string.mock_form_delete, color: C.danger }) }),
      dismissButton: TextButton({ onClick: () => { confirmDelete.value = false; }, children: Text(R.string.log_session_cancel) }),
      containerColor: C.surface,
    }) : null), { style: { display: 'contents' } }),
  );
}

/** A field callback that writes through the ViewModel and keeps the input showing what it accepted. */
const field = (actions, set, read) => (v) => { set(v); if (actions.current) keepFieldValue(read(actions.current())); };

function MockFormFields(state, actions) {
  const pickDate = remember('mockForm:pickDate', 0);
  const showDetails = remember('mockForm:showDetails', state.attemptedText.length > 0 || state.correctText.length > 0 || state.wrongText.length > 0);
  const chips = Row({ class: 'scroll-x', css: { maxWidth: '100%' }, horizontalArrangement: { spacedBy: sp('sm') } },
    ...MockTestType.entries.map((type) => FilterChip({ selected: type === state.type, onClick: () => actions.onType(type), label: Text(mockTypeLabel(type)) })),
  );
  chips.setAttribute('data-scroll-key', 'mock-types');
  const scoreText = (s) => (s == null ? '' : s);
  const form = Column({ class: 'weight fill-w scroll-y', css: { padding: `0 ${sp('screen')}` }, verticalArrangement: { spacedBy: sp('sm') } },
    chips,
    Row({ class: 'fill-w', horizontalArrangement: { spacedBy: sp('sm') }, verticalAlignment: 'CenterVertically' },
      OutlinedTextField({
        key: 'mock-name', value: state.name, singleLine: true,
        onValueChange: field(actions, actions.onName, (s) => s.name),
        label: Text(R.string.mock_form_name), placeholder: Text(mockTypeLabel(state.type)), class: 'weight',
      }),
      OutlinedButton({ onClick: () => { pickDate.value = Date.now(); }, css: { minHeight: sz('touchTarget') }, children: Text(formatInsightDate(state.date)) }),
    ),
    FormLabel(R.string.mock_form_scores),
    Text({ text: R.string.mock_form_scores_hint, style: 'bodySmall', color: C.textMuted }),
    ...chunked(MockSubject.entries, 2).map((pair) => Row({ class: 'fill-w', horizontalArrangement: { spacedBy: sp('sm') } },
      ...pair.map((subject) => NumberField({
        key: `score-${subject.name}`,
        value: scoreText(state.subjectScores.get(subject)),
        onValue: field(actions, (v) => actions.onSubjectScore(subject, v), (s) => scoreText(s.subjectScores.get(subject))),
        label: subjectLabel(subject), signed: true,
      })),
    )),
    Row({ class: 'fill-w', horizontalArrangement: { spacedBy: sp('sm') } },
      NumberField({
        key: 'total',
        value: state.totalIsAuto ? (state.total != null ? String(state.total) : '') : state.totalText,
        onValue: field(actions, actions.onTotal, (s) => s.totalText),
        label: state.totalIsAuto ? R.string.mock_form_total_auto : R.string.mock_form_total,
        signed: true, enabled: !state.totalIsAuto,
      }),
      NumberField({ key: 'max', value: state.maxTotalText, onValue: field(actions, actions.onMaxTotal, (s) => s.maxTotalText), label: R.string.mock_form_out_of }),
    ),
    observe(showDetails, (show) => [
      TextButton({ onClick: () => { showDetails.value = !showDetails.value; }, children: Text(show ? R.string.mock_form_hide_details : R.string.mock_form_show_details) }),
      show ? Row({ class: 'fill-w', horizontalArrangement: { spacedBy: sp('sm') } },
        NumberField({ key: 'attempted', value: state.attemptedText, onValue: field(actions, actions.onAttempted, (s) => s.attemptedText), label: R.string.mock_form_attempted }),
        NumberField({ key: 'correct', value: state.correctText, onValue: field(actions, actions.onCorrect, (s) => s.correctText), label: R.string.mock_form_correct }),
        NumberField({ key: 'wrong', value: state.wrongText, onValue: field(actions, actions.onWrong, (s) => s.wrongText), label: R.string.mock_form_wrong }),
      ) : null,
    ], { style: { display: 'contents' } }),
    ...(state.proUnlocked
      ? MistakeLog(state, actions)
      // Mistakes tagged while Pro stay in state, so saving never drops them.
      : [ProLockCard({ title: R.string.pro_lock_mistakes_title, body: R.string.pro_lock_mistakes_body, actionLabel: R.string.pro_lock_action, onUnlock: actions.onOpenPaywall, css: { marginTop: sp('md') } })]),
    state.saveFailed ? Text({ text: R.string.log_session_save_failed, style: 'bodySmall', color: C.danger }) : null,
    Button({
      onClick: actions.onSave, enabled: state.canSave, class: 'fill-w', css: { height: sz('ctaHeight'), paddingTop: sp('xs') },
      children: Text(state.isEdit ? R.string.log_session_save_changes : R.string.mock_form_save),
    }),
    Spacer({ height: sp('xl') }),
  );
  form.setAttribute('data-scroll-key', 'mock-form');
  noShrink(form);
  return Column({ class: 'weight fill-w' },
    form,
    observe(pickDate, (seq) => (seq ? SessionDatePicker({
      key: `mockDate:${seq}`, date: state.date,
      onDate: (d) => { actions.onDate(d); pickDate.value = 0; },
      onDismiss: () => { pickDate.value = 0; },
    }) : null), { style: { display: 'contents' } }),
  );
}

// Pro: shown only when unlocked (MockFormFields).
export function MistakeLog(state, actions) {
  const picking = remember('mockForm:picking', 0);
  return [
    FormLabel(R.string.mock_form_mistakes),
    Text({ text: R.string.mock_form_mistakes_hint, style: 'bodySmall', color: C.textMuted }),
    state.mistakes.length > 0
      ? AppCard({ class: 'fill-w', children: state.mistakes.flatMap((mistake, index) => [
        MistakeRow(state.chapterTitle(mistake.chapterId), mistake, actions),
        index < state.mistakes.length - 1 ? HorizontalDivider({ color: C.borderSubtle }) : null,
      ]).filter(Boolean) })
      : null,
    OutlinedButton({
      onClick: () => { picking.value = Date.now(); }, enabled: state.subjects.length > 0,
      class: 'fill-w', css: { minHeight: sz('touchTarget') }, children: Text(R.string.mock_form_tag_chapter),
    }),
    observe(picking, (seq) => (seq ? TagChapterDialog({
      seq,
      subjects: state.subjects,
      tagged: new Set(state.mistakes.map((it) => it.chapterId)),
      onPick: (id) => { actions.onTagChapter(id); picking.value = 0; },
      onDismiss: () => { picking.value = 0; },
    }) : null), { style: { display: 'contents' } }),
  ].filter(Boolean);
}

function MistakeRow(title, mistake, actions) {
  const types = Row({ class: 'scroll-x', css: { maxWidth: '100%' }, horizontalArrangement: { spacedBy: sp('sm') } },
    ...MistakeType.entries.map((type) => FilterChip({
      selected: type === mistake.type, onClick: () => actions.onMistakeType(mistake.chapterId, type), label: Text(mistakeTypeLabel(type)),
    })),
  );
  types.setAttribute('data-scroll-key', `mistake-types-${mistake.chapterId}`);
  return Column({ class: 'fill-w', css: { padding: `${sp('sm')} ${sp('lg')}` } },
    Row({ class: 'fill-w', verticalAlignment: 'CenterVertically' },
      Text({ text: title, style: 'titleSmall', color: C.text, class: 'weight', maxLines: 2 }),
      IconButton({ onClick: () => actions.onRemoveMistake(mistake.chapterId), children: Icon({ imageVector: 'Outlined.Close', contentDescription: R.string.mock_form_untag, tint: C.textMuted }) }),
    ),
    types,
  );
}

function TagChapterDialog({ seq, subjects, tagged, onPick, onDismiss }) {
  const subjectId = remember(`tagChapter:subjectId:${seq}`, subjects[0]?.id ?? null);
  return observe(subjectId, (sid) => {
    const chapters = (subjects.find((it) => it.id === sid)?.chapters ?? []).filter((it) => !tagged.has(it.id));
    const chips = Row({ class: 'scroll-x', css: { maxWidth: '100%' }, horizontalArrangement: { spacedBy: sp('sm') } },
      ...subjects.map((subject) => FilterChip({ selected: subject.id === sid, onClick: () => { subjectId.value = subject.id; }, label: Text(subject.name) })),
    );
    chips.setAttribute('data-scroll-key', 'tag-subjects');
    const list = LazyColumn({ class: 'fill-w', css: { maxHeight: sz('dialogListMaxHeight') }, scrollKey: `tag-chapters-${sid}` },
      ...chapters.map((chapter) => clickable(Text({
        text: chapter.title, style: 'bodyMedium', color: C.text, class: 'fill-w', css: { padding: `${sp('md')} 0` },
      }), { onClick: () => onPick(chapter.id) })),
    );
    noShrink(list);
    return AlertDialog({
      key: 'tagChapter',
      onDismissRequest: onDismiss,
      title: Text(R.string.mock_form_tag_chapter),
      text: Column({ class: 'fill-w' }, chips, list),
      confirmButton: TextButton({ onClick: onDismiss, children: Text(R.string.log_session_cancel) }),
      containerColor: C.surface,
    });
  }, { style: { display: 'contents' } });
}

function NumberField({ key, value, onValue, label, signed = false, enabled = true }) {
  return OutlinedTextField({
    key, value, onValueChange: onValue, singleLine: true, enabled,
    label: Text({ text: label, maxLines: 1 }),
    keyboardOptions: { keyboardType: signed ? 'Text' : 'Number' },
    class: 'weight',
  });
}

function FormLabel(text) {
  return Text({ text, style: 'titleSmall', color: C.textSecondary, css: { paddingTop: sp('md') } });
}

function FormMessage(text) {
  return Box({ class: 'weight fill-w', css: { padding: sp('screen') }, contentAlignment: 'Center' },
    Text({ text, style: 'bodyLarge', color: C.textMuted }));
}

function mistakeTypeLabel(type) {
  switch (type) {
    case MistakeType.CONCEPT: return R.string.mistake_concept;
    case MistakeType.SILLY: return R.string.mistake_silly;
    case MistakeType.TIME: return R.string.mistake_time;
    default: return R.string.mistake_guess;
  }
}
