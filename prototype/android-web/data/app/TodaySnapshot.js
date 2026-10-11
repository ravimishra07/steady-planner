// Port of app/today/TodaySnapshot.kt. What the reminder and widgets show, read from the same
// repositories and Metrics functions as Today.
import { dataClass, sortedBy } from '../../domain/kt.js';
import { today as localToday } from '../../domain/time.js';
import { PlanBlockStatus, StudyActivityType } from '../../domain/StudyPlan.js';
import { chapterOf, findNode, findSubjectOf } from '../../domain/ExamPack.js';
import { daysToExam } from '../../domain/ExamDates.js';
import { syllabusMetrics, targetStates } from '../../domain/Metrics.js';

/** One planned block of today, named the way Today names it. */
export const TodayBlock = dataClass({
  id: undefined, nodeId: undefined,
  /** Blank when the block has no chapter; callers fall back to a generic label. */
  title: undefined,
  subjectName: undefined, startMinuteOfDay: undefined, plannedMinutes: undefined, isRevision: undefined,
}, {
  get endMinuteOfDay() { return this.startMinuteOfDay + this.plannedMinutes; },
});

export const TodaySnapshot = dataClass({
  hasPlan: undefined, daysToExam: null, coveragePercent: undefined,
  /** Today's blocks still planned, in start order. */
  blocks: undefined,
}, {
  /** The first planned block that has not ended yet. */
  nextBlock(nowMinuteOfDay) { return this.blocks.find((it) => it.endMinuteOfDay > nowMinuteOfDay) ?? null; },
});
TodaySnapshot.Empty = TodaySnapshot({ hasPlan: false, daysToExam: null, coveragePercent: 0, blocks: [] });

/** AppContainer.loadTodaySnapshot(today). Never throws — a widget or a reminder must not crash. */
export async function loadTodaySnapshot(container, today = localToday()) {
  try {
    const attempt = await container.attemptRepository.activeAttempt();
    if (!attempt) return TodaySnapshot.Empty;
    const pack = await container.examPackRepository.examPackFor(attempt.examId, attempt.id);
    const metrics = syllabusMetrics(
      pack,
      targetStates(await container.targetSyllabusRepository.overridesFor(attempt.id)),
      await container.topicProgressRepository.allOnce(attempt.id),
    );
    const planned = (await container.planRepository.planForDate(attempt.id, today))
      .filter((it) => it.status === PlanBlockStatus.PLANNED);
    const blocks = sortedBy(planned, (it) => it.startMinuteOfDay).map((block) => {
      const isRevision = block.activityType === StudyActivityType.REVISION;
      const node = block.nodeId != null ? findNode(pack, block.nodeId) : null;
      const chapter = isRevision && block.nodeId != null ? chapterOf(pack, block.nodeId) : null;
      return TodayBlock({
        id: block.id,
        nodeId: block.nodeId ?? '',
        title: block.customTitle ?? chapter?.title ?? node?.title ?? '',
        subjectName: (block.nodeId != null ? findSubjectOf(pack, block.nodeId)?.name : null) ?? '',
        startMinuteOfDay: block.startMinuteOfDay,
        plannedMinutes: block.plannedMinutes,
        isRevision,
      });
    });
    return TodaySnapshot({
      hasPlan: true,
      daysToExam: daysToExam(attempt, today),
      coveragePercent: metrics.overall.percent,
      blocks,
    });
  } catch (e) {
    console.error(e);
    return TodaySnapshot.Empty;
  }
}
