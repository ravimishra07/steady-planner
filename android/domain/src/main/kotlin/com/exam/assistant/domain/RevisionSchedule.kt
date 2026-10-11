package com.exam.assistant.domain

import java.time.LocalDate

/**
 * The one revision-interval table (PRODUCTION_V1_PLAN §7.1). Nothing else in
 * the app may hard-code a revision interval.
 *
 * - After a Learn session: Struggled 1 day, Okay 3, Strong 7.
 * - After a revision: Struggled back to 1 day; Okay previous x2 (cap 30);
 *   Strong previous x3 (cap 60).
 *
 * A session with no rating yet is scheduled as [StudyOutcome.OKAY]; the rating
 * later *replaces* that provisional schedule (see [recordStudyOutcome]) — it is
 * never applied on top of it.
 */
object RevisionSchedule {
    /** What a missing rating means. */
    val DEFAULT_OUTCOME: StudyOutcome = StudyOutcome.OKAY

    /** First revision interval, in days after a Learn session. */
    val AFTER_LEARN_DAYS: Map<StudyOutcome, Int> = mapOf(
        StudyOutcome.STRUGGLED to 1,
        StudyOutcome.OKAY to 3,
        StudyOutcome.STRONG to 7,
    )

    /** [resetDays] wins when set; otherwise previous interval x [multiplier], capped at [capDays]. */
    data class RevisionRule(val multiplier: Int, val capDays: Int, val resetDays: Int? = null)

    val AFTER_REVISION: Map<StudyOutcome, RevisionRule> = mapOf(
        StudyOutcome.STRUGGLED to RevisionRule(multiplier = 1, capDays = 1, resetDays = 1),
        StudyOutcome.OKAY to RevisionRule(multiplier = 2, capDays = 30),
        StudyOutcome.STRONG to RevisionRule(multiplier = 3, capDays = 60),
    )

    fun intervalAfterLearn(outcome: StudyOutcome?): Int =
        AFTER_LEARN_DAYS.getValue(outcome ?: DEFAULT_OUTCOME)

    /** [previousDays] is the interval that was in force when this revision was done. */
    fun intervalAfterRevision(previousDays: Int?, outcome: StudyOutcome?): Int {
        val rule = AFTER_REVISION.getValue(outcome ?: DEFAULT_OUTCOME)
        rule.resetDays?.let { return it }
        val previous = previousDays ?: intervalAfterLearn(null)
        return (previous * rule.multiplier).coerceAtMost(rule.capDays)
    }
}

/**
 * When the topic's next revision will be due if a session of [activityType] is
 * completed on [today] and left unrated (so scheduled as Okay). Used to show the
 * student the date before they start; the rating, when given, moves it.
 */
fun nextRevisionDueIfCompleted(current: RevisionState?, activityType: StudyActivityType, today: LocalDate): LocalDate =
    if (activityType == StudyActivityType.REVISION) {
        today.plusDays(RevisionSchedule.intervalAfterRevision(current?.intervalDays, null).toLong())
    } else {
        current?.nextDueDate ?: today.plusDays(RevisionSchedule.intervalAfterLearn(null).toLong())
    }

/** After a completed LEARN session on a topic: schedule its first revision. An existing schedule is kept. */
fun revisionStateAfterLearn(
    current: RevisionState?,
    attemptId: String,
    nodeId: String,
    today: LocalDate,
    nowMs: Long,
    outcome: StudyOutcome?,
): RevisionState {
    if (current != null) return current
    val interval = RevisionSchedule.intervalAfterLearn(outcome)
    return RevisionState(
        attemptId = attemptId,
        nodeId = nodeId,
        enabled = true,
        lastReviewedAtEpochMs = null,
        nextDueDate = today.plusDays(interval.toLong()),
        revisionCount = 0,
        intervalDays = interval,
        updatedAtEpochMs = nowMs,
    )
}

/** After a completed REVISION session: bump the count and push the next due date out. */
fun revisionStateAfterReview(
    current: RevisionState?,
    attemptId: String,
    nodeId: String,
    today: LocalDate,
    nowMs: Long,
    outcome: StudyOutcome?,
): RevisionState {
    val interval = RevisionSchedule.intervalAfterRevision(current?.intervalDays, outcome)
    return RevisionState(
        attemptId = attemptId,
        nodeId = nodeId,
        enabled = true,
        lastReviewedAtEpochMs = nowMs,
        nextDueDate = today.plusDays(interval.toLong()),
        revisionCount = (current?.revisionCount ?: 0) + 1,
        intervalDays = interval,
        updatedAtEpochMs = nowMs,
    )
}

/** Result of closing a study session: the session as stored, plus the revision schedule it produced. */
data class CompletedStudy(
    val session: StudySession,
    val revision: RevisionState?,
)

/**
 * The pure core of "complete a Focus session". Records on the session what the
 * revision looked like *before* it ([StudySession.priorIntervalDays],
 * [StudySession.dueDateAtCompletion]) so a later rating can replace — not
 * stack on — the provisional schedule, and so "revision on time" is knowable.
 */
fun completeStudySession(
    session: StudySession,
    currentRevision: RevisionState?,
    today: LocalDate,
    nowMs: Long,
): CompletedStudy {
    val isRevision = session.activityType == StudyActivityType.REVISION
    val completed = session.copy(
        status = StudySessionStatus.COMPLETED,
        endedAtEpochMs = nowMs,
        updatedAtEpochMs = nowMs,
        priorIntervalDays = currentRevision?.intervalDays,
        dueDateAtCompletion = if (isRevision) currentRevision?.nextDueDate else null,
    )
    val nodeId = session.nodeId ?: return CompletedStudy(completed, null)
    val revision = if (isRevision) {
        revisionStateAfterReview(currentRevision, session.attemptId, nodeId, today, nowMs, session.outcome)
    } else {
        revisionStateAfterLearn(currentRevision, session.attemptId, nodeId, today, nowMs, session.outcome)
    }
    return CompletedStudy(completed, revision)
}

/**
 * The student rated a session after it was completed. Recomputes the schedule
 * from the interval recorded at completion, so the rating is applied exactly
 * once and repeating the call gives the same result.
 *
 * A Learn session on a topic that already had a schedule leaves that schedule
 * alone (completion did not create one, so there is nothing to recalibrate).
 */
fun recordStudyOutcome(
    session: StudySession,
    currentRevision: RevisionState?,
    outcome: StudyOutcome,
    nowMs: Long,
): CompletedStudy {
    val rated = session.copy(outcome = outcome, updatedAtEpochMs = nowMs)
    if (currentRevision == null) return CompletedStudy(rated, null)
    val interval = when (session.activityType) {
        StudyActivityType.REVISION -> RevisionSchedule.intervalAfterRevision(session.priorIntervalDays, outcome)
        else -> {
            if (session.priorIntervalDays != null) return CompletedStudy(rated, currentRevision)
            RevisionSchedule.intervalAfterLearn(outcome)
        }
    }
    return CompletedStudy(
        rated,
        currentRevision.copy(
            nextDueDate = session.studyDate.plusDays(interval.toLong()),
            intervalDays = interval,
            updatedAtEpochMs = nowMs,
        ),
    )
}
