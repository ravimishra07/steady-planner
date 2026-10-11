// Port of core/data/FocusStore.kt. DataStore "focus" -> prefs('focus'), same keys.
// This is the Focus clock: `ends_at` (epoch ms) survives a page reload exactly like process death
// on Android, and FocusController.reconcile() completes or resumes from it.
import { prefs } from '../platform/store.js';
import { FocusSession, FocusStatus, FocusBlockRef, DEFAULT_FOCUS_DURATION_SEC } from '../domain/FocusSession.js';
import { BlockTag } from '../domain/TodaySchedule.js';

const statusKey = 'status';
const durationKey = 'duration_sec';
const remainingKey = 'remaining_sec';
const endsAtKey = 'ends_at';
const blockIdKey = 'block_id';
const blockTitleKey = 'block_title';
const blockSubtitleKey = 'block_subtitle';
const blockTagKey = 'block_tag';
const blockSessionIdKey = 'block_session_id';
const blockNodeKeyKey = 'block_node_key';
const blockIsRevisionKey = 'block_is_revision';
const completedTodayKey = 'completed_today';

export class FocusStore {
  constructor(_context, _dispatchers) { this._ds = prefs('focus'); }

  /** Returns the persisted state unchanged. Expiry is reconciled by the session lifecycle owner. */
  async load() {
    const p = this._ds.all();
    const status = FocusStatus.valueOf(p[statusKey] ?? FocusStatus.IDLE.name);
    return FocusSession({
      status,
      durationSec: p[durationKey] ?? DEFAULT_FOCUS_DURATION_SEC,
      remainingSec: p[remainingKey] ?? (p[durationKey] ?? DEFAULT_FOCUS_DURATION_SEC),
      endsAtMs: p[endsAtKey] ?? null,
      block: p[blockIdKey] != null ? FocusBlockRef({
        id: p[blockIdKey],
        title: p[blockTitleKey] ?? '',
        subtitle: p[blockSubtitleKey] ?? '',
        tag: p[blockTagKey] != null ? BlockTag.valueOfOrNull(p[blockTagKey]) : null,
        sessionId: p[blockSessionIdKey] ?? '',
        nodeKey: p[blockNodeKeyKey] ?? '',
        isRevision: p[blockIsRevisionKey] ?? false,
      }) : null,
      completedToday: p[completedTodayKey] ?? 0,
    });
  }

  async save(session) {
    this._ds.edit((p) => {
      p[statusKey] = session.status.name;
      p[durationKey] = session.durationSec;
      p[remainingKey] = session.remainingSec;
      if (session.endsAtMs != null) p[endsAtKey] = session.endsAtMs; else delete p[endsAtKey];
      const block = session.block;
      if (block != null) {
        p[blockIdKey] = block.id;
        p[blockTitleKey] = block.title;
        p[blockSubtitleKey] = block.subtitle;
        const tagName = block.tag?.name;
        if (tagName != null) p[blockTagKey] = tagName; else delete p[blockTagKey];
        p[blockSessionIdKey] = block.sessionId;
        p[blockNodeKeyKey] = block.nodeKey;
        p[blockIsRevisionKey] = block.isRevision;
      } else {
        for (const k of [blockIdKey, blockTitleKey, blockSubtitleKey, blockTagKey, blockSessionIdKey, blockNodeKeyKey, blockIsRevisionKey]) delete p[k];
      }
      p[completedTodayKey] = session.completedToday;
    });
  }

  async clear() { this._ds.clear(); }
}
