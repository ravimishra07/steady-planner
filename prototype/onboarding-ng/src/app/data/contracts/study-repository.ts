import { ChapterStat, ExtraBlock, LoggedSession, Task } from '../../domain/study/models';

/** Framework-free operations used to persist and query completed study work. */
export interface StudyRepository {
  addExtra(extra: Omit<ExtraBlock, 'id'>): void;
  removeExtra(id: string): void;
  extrasOn(dateKey: string): ExtraBlock[];
  isLogged(dateKey: string, chapterId: string, task: Task, subtopicId?: string): boolean;
  stat(chapterId: string): ChapterStat;
  log(session: Omit<LoggedSession, 'id'>): void;
  minutesOn(dateKey: string): number;
  sessionsOn(dateKey: string): LoggedSession[];
}
