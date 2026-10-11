// Port of core/data/TodayPrefs.kt.
import { dataClass } from '../domain/kt.js';

export const TodayPrefs = dataClass({ activeSubjects: new Set(), blocksDone: new Set() });
