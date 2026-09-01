import { Task } from '../study/models';

export type FocusStatus = 'idle' | 'running' | 'paused' | 'done';

export interface FocusTarget {
  chapterId: string;
  subtopicId?: string;
  title: string;
  context: string;
  task: Task;
  minutes: number;
}
