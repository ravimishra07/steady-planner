// Port of domain/PlanMode.kt
import { enumClass } from './kt.js';

/**
 * How the student wants the plan shaped (PRODUCTION_V1_PLAN §3.1).
 * - DEADLINE: a finish-by date exists; finish-by..exam is a revision phase.
 * - STEADY: no finish-by date; Learn/Practice/Revise interleave, never "behind".
 */
export const PlanMode = enumClass(['DEADLINE', 'STEADY']);
