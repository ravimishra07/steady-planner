// Port of feature/onboarding/model/ExamCatalog.kt
import { NEET_EXAM_ID } from '../../../domain/ExamPack.js';
import { dataClass } from '../../../domain/kt.js';

export const ExamOption = dataClass({ id: undefined, labelRes: undefined, bundledSyllabus: undefined });

/** Native exam packs available to onboarding. NEET is the product default. */
export const ExamCatalog = Object.freeze({
  options: Object.freeze([
    ExamOption({ id: NEET_EXAM_ID, labelRes: 'onboarding_exam_neet', bundledSyllabus: true }),
  ]),
  contains(examId) { return ExamCatalog.options.some((it) => it.id === examId); },
  hasBundledSyllabus(examId) { return ExamCatalog.options.find((it) => it.id === examId)?.bundledSyllabus === true; },
});
