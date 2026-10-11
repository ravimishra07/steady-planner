// Port of core/data/StageLabelStore.kt. DataStore "stage_labels" -> prefs('stage_labels'), same keys.
// The student's chapter-stage labels (§5.1): renames, hidden stages, one custom stage.
import { prefs } from '../platform/store.js';
import { ChapterStage, StageLabels } from '../domain/ChapterStage.js';

/** Kotlin: internal Context.stageLabelDataStore (BackupRepository exports it). */
export const stageLabelDataStore = () => prefs('stage_labels');

const hiddenKey = 'hidden';
const customKey = 'custom_label';
const renameKey = (stage) => `rename_${stage.name}`;

export class StageLabelStore {
  constructor(_context, _dispatchers) { this._ds = stageLabelDataStore(); }

  async load() { return toLabels(this._ds.all()); }

  async save(labels) {
    this._ds.edit((p) => {
      for (const stage of ChapterStage.entries) {
        const name = labels.renamed.get(stage)?.trim();
        if (!name) delete p[renameKey(stage)]; else p[renameKey(stage)] = name;
      }
      p[hiddenKey] = [...new Set([...labels.hidden].map((s) => s.name))];
      const custom = labels.customLabel?.trim();
      if (!custom) delete p[customKey]; else p[customKey] = custom;
    });
  }
}

/** Preferences.toLabels() */
function toLabels(p) {
  const renamed = new Map();
  for (const stage of ChapterStage.entries) {
    const v = p[renameKey(stage)];
    if (v != null) renamed.set(stage, v);
  }
  const hidden = new Set((p[hiddenKey] ?? []).map((n) => ChapterStage.entries.find((s) => s.name === n)).filter(Boolean));
  return StageLabels({ renamed, hidden, customLabel: p[customKey] ?? null });
}
