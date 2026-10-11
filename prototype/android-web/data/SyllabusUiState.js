// Port of core/data/SyllabusUiState.kt.
import { dataClass } from '../domain/kt.js';

export const SyllabusUiState = dataClass({
  doneLeaves: new Set(),
  openNodes: new Set(),
  sectionIndex: 0,
  /** Section keys excluded from the student's target syllabus, e.g. section_2. */
  excludedSectionKeys: new Set(),
});
