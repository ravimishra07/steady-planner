// Port of feature/home SubjectCatalog.kt. labelRes/shortRes are the string templates from R.
import { dataClass } from '../../domain/kt.js';
import { BlockTag } from '../../domain/TodaySchedule.js';
import { R } from './_local.js';

export const SubjectOption = dataClass({ id: undefined, labelRes: undefined, shortRes: undefined });

export const SubjectCatalog = Object.freeze({
  options: Object.freeze([
    SubjectOption({ id: 'physics', labelRes: R.string.home_subject_physics, shortRes: R.string.home_subject_physics_short }),
    SubjectOption({ id: 'chemistry', labelRes: R.string.home_subject_chemistry, shortRes: R.string.home_subject_chemistry_short }),
    SubjectOption({ id: 'botany', labelRes: R.string.home_subject_botany, shortRes: R.string.home_subject_botany_short }),
    SubjectOption({ id: 'zoology', labelRes: R.string.home_subject_zoology, shortRes: R.string.home_subject_zoology_short }),
  ]),
  allIds() { return new Set(this.options.map((it) => it.id)); },
  find(id) { return this.options.find((it) => it.id === id) ?? null; },
});

/** BlockTag.labelRes() */
export function labelRes(tag) {
  switch (tag) {
    case BlockTag.READ: return R.string.home_tag_read;
    case BlockTag.PRACTICE: return R.string.home_tag_practice;
    default: return R.string.home_tag_revise;
  }
}
