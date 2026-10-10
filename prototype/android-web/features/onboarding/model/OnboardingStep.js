// Port of feature/onboarding/model/OnboardingStep.kt
import { enumClass } from '../../../domain/kt.js';
import { PROGRESS_SEGMENTS } from './OnboardingUiState.js';

const NEXT = { Appearance: 'Coaching', Coaching: 'Commitments', Commitments: 'Date', Date: 'Hours', Hours: 'Start', Start: 'Syllabus', Syllabus: 'Plan', Plan: null };
const PREV = { Appearance: null, Coaching: 'Appearance', Commitments: 'Coaching', Date: 'Commitments', Hours: 'Date', Start: 'Hours', Syllabus: 'Start', Plan: 'Syllabus' };
const PROGRESS = { Appearance: 0, Coaching: 1, Commitments: 2, Date: 3, Hours: 4, Start: 5 };

export const OnboardingStep = enumClass(
  ['Appearance', 'Coaching', 'Commitments', 'Date', 'Hours', 'Start', 'Syllabus', 'Plan'],
  () => ({}),
  {
    get progressIndex() { return this.name in PROGRESS ? PROGRESS[this.name] : PROGRESS_SEGMENTS - 1; },
    next() { return NEXT[this.name] ? OnboardingStep[NEXT[this.name]] : null; },
    previous() { return PREV[this.name] ? OnboardingStep[PREV[this.name]] : null; },
  },
);
