// Port of feature/focus/FocusUiState.kt
import { dataClass, lazyDefault } from '../../domain/kt.js';
import { FocusStatus } from '../../domain/FocusSession.js';

/** tag: BlockTag */
export const FocusQueueItem = dataClass({
  id: undefined, title: undefined, subtitle: undefined, tag: undefined, durationMinutes: undefined, completed: false,
});

export const FocusUiState = dataClass({
  loading: true,
  status: lazyDefault(() => FocusStatus.IDLE),
  durationMinutes: 50,
  blockTitle: '',
  blockSubtitle: '',
  /** BlockTag? */
  blockTag: null,
  hasBlock: false,
  /** List<FocusQueueItem> */
  queue: [],
  showStopDialog: false,
  /** StudyOutcome? */
  outcome: null,
  /** Set after a Learn session on a chapter not yet learned: "Mark chapter learned?" (§5.1). LearnPrompt? */
  learnPrompt: null,
  /** True until the notification permission has been asked for once, at a Focus start. */
  askNotificationPermission: false,
});

export const LearnPrompt = dataClass({ chapterId: undefined, chapterTitle: undefined });
