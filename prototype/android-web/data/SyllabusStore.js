// Port of core/data/SyllabusStore.kt. DataStore "syllabus_ui" -> prefs('syllabus_ui'), same keys.
import { prefs } from '../platform/store.js';
import { SyllabusUiState } from './SyllabusUiState.js';

const doneKey = 'done_leaves';
const openKey = 'open_nodes';
const sectionKey = 'section_index';
const excludedSectionKey = 'excluded_sections';

export class SyllabusStore {
  constructor(_context, _dispatchers) { this._ds = prefs('syllabus_ui'); }

  async load() {
    const p = this._ds.all();
    return SyllabusUiState({
      doneLeaves: new Set(p[doneKey] ?? []),
      openNodes: new Set(p[openKey] ?? []),
      sectionIndex: p[sectionKey] ?? 0,
      excludedSectionKeys: new Set(p[excludedSectionKey] ?? []),
    });
  }

  async save(state) {
    this._ds.edit((it) => {
      it[doneKey] = [...state.doneLeaves];
      it[openKey] = [...state.openNodes];
      it[sectionKey] = state.sectionIndex;
      it[excludedSectionKey] = [...state.excludedSectionKeys];
    });
  }

  async clear() { this._ds.clear(); }
}
