import { FocusTarget } from '../../domain/focus/models';
import { Recall } from '../../domain/study/models';

export interface FocusRepository {
  start(target: FocusTarget, minutes: number): void;
  pause(): void;
  resume(): void;
  extend(minutes: number): void;
  stop(): void;
  finish(recall: Recall, attempted?: number, correct?: number): number;
  discard(): void;
}
