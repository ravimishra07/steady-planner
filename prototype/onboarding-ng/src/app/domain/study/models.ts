export type Task = 'Learn' | 'Practice' | 'Revise';
export type Recall = 'shaky' | 'okay' | 'solid';

/** One sitting the student actually completed. */
export interface LoggedSession {
  id: string;
  dateKey: string;
  chapterId: string;
  subtopicId?: string;
  title: string;
  task: Task;
  minutes: number;
  attempted?: number;
  correct?: number;
  recall?: Recall;
}
export interface ChapterStat {
  revisions: number;
  attempted: number;
  correct: number;
  lastTouched: string | null;
  recall: Recall | null;
  dueKey: string | null;
}

export interface ExtraBlock {
  id: string;
  dateKey: string;
  startMinute: number;
  minutes: number;
  task: Task;
  chapterId: string;
  subtopicId?: string;
}

export function dateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function parseKey(key: string): Date {
  const [year, month, day] = key.split('-').map(Number);
  return new Date(year, month - 1, day);
}
