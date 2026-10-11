// Port of feature/onboarding/steps/OnboardingExamStep.kt (not wired into the current flow, ported for parity).
import { Column, Text, C } from '../../../design/components.js';
import { stringResource } from '../res/values/strings.js';
import { OnboardingSelectableCard } from '../components/OnboardingComponents.js';
import { ExamCatalog } from '../model/ExamCatalog.js';

/** OnboardingExamStep({ selectedExamId, onSelectExam, modifier? }) */
export function OnboardingExamStep(p) {
  const m = p.modifier || {};
  const col = Column({ class: m.class, css: m.css, verticalArrangement: { spacedBy: 'var(--space-sm)' } },
    ExamCatalog.options.map((exam) => OnboardingSelectableCard({
      title: stringResource(exam.labelRes),
      selected: exam.id === p.selectedExamId,
      onClick: () => p.onSelectExam(exam.id),
      modifier: { css: { minHeight: 'var(--size-ctaHeight)' } },
      titleStyle: 'titleMedium',
      trailing: Text({
        text: stringResource(exam.bundledSyllabus ? 'onboarding_exam_full_syllabus' : 'onboarding_exam_add_chapters'),
        style: 'bodySmall',
        color: C.textSecondary,
      }),
    })));
  col.setAttribute('role', 'radiogroup');
  return col;
}
