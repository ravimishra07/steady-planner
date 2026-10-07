package com.exam.assistant.domain

import java.time.LocalDate

/*
 * Mock and test log (PRODUCTION_V1_PLAN §8.1) and the lightweight mistake log (§8.2).
 * NEET 2027 is computer-based: nothing here assumes an OMR sheet.
 */

enum class MockTestType { UNIT_TEST, PART_TEST, FULL_MOCK, PYQ_PAPER }

enum class MistakeType { CONCEPT, SILLY, TIME, GUESS }

/** The four NEET sections a score can be split into. */
enum class MockSubject { PHYSICS, CHEMISTRY, BOTANY, ZOOLOGY }

const val NEET_MAX_TOTAL = 720

data class MockTest(
    val id: String,
    val attemptId: String,
    val date: LocalDate,
    val name: String,
    val type: MockTestType,
    val physics: Int? = null,
    val chemistry: Int? = null,
    val botany: Int? = null,
    val zoology: Int? = null,
    val total: Int,
    val maxTotal: Int = NEET_MAX_TOTAL,
    val attempted: Int? = null,
    val correct: Int? = null,
    val wrong: Int? = null,
) {
    fun score(subject: MockSubject): Int? = when (subject) {
        MockSubject.PHYSICS -> physics
        MockSubject.CHEMISTRY -> chemistry
        MockSubject.BOTANY -> botany
        MockSubject.ZOOLOGY -> zoology
    }
}

/** A chapter where marks were lost in one mock, and why. One per (mock, chapter). */
data class MockMistake(
    val mockId: String,
    val chapterId: String,
    val type: MistakeType,
)

/**
 * The total to store: the sum of the subject scores when any is given, otherwise the typed total.
 * Null when neither is known, so the form cannot save a score out of nothing.
 */
fun mockTotal(subjectScores: List<Int?>, typedTotal: Int?): Int? {
    val given = subjectScores.filterNotNull()
    return if (given.isNotEmpty()) given.sum() else typedTotal
}

/**
 * Chapters tagged in a mock that should now get the Struggled outcome: the ones not already
 * tagged before this save. Re-saving an edited mock therefore never applies it twice.
 */
fun newlyTaggedChapters(before: Collection<MockMistake>, after: Collection<MockMistake>): Set<String> =
    after.mapTo(mutableSetOf()) { it.chapterId } - before.mapTo(mutableSetOf()) { it.chapterId }

/**
 * A chapter lost marks in a mock dated [mockDate] (§8.2): it is scheduled as a Struggled
 * outcome through [RevisionSchedule], so its next revision comes sooner. An earlier due date is
 * kept; the revision count is untouched because no revision was done.
 */
fun revisionStateAfterMistake(
    current: RevisionState?,
    attemptId: String,
    chapterId: String,
    mockDate: LocalDate,
    nowMs: Long,
): RevisionState {
    if (current == null) {
        return revisionStateAfterLearn(null, attemptId, chapterId, mockDate, nowMs, StudyOutcome.STRUGGLED)
    }
    val interval = RevisionSchedule.intervalAfterRevision(current.intervalDays, StudyOutcome.STRUGGLED)
    val due = mockDate.plusDays(interval.toLong())
    return current.copy(
        enabled = true,
        nextDueDate = current.nextDueDate?.let { minOf(it, due) } ?: due,
        intervalDays = interval,
        updatedAtEpochMs = nowMs,
    )
}
