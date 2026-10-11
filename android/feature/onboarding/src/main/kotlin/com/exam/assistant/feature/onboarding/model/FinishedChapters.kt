package com.exam.assistant.feature.onboarding.model

import com.exam.assistant.domain.StartingPoint

/**
 * Rules for "Mark chapters you've already finished" (PRODUCTION_V1_PLAN §3.3). Pure, so the
 * tap-count promise (a dropper marks about 80% done in five taps or fewer) can be tested.
 */

/** Whole-subject toggle: everything finished clears it, otherwise it all becomes finished. */
internal fun toggleSubjectFinished(
    covered: Set<String>,
    subject: OnboardingSyllabusSubject,
): Set<String> = toggleAll(covered, subject.chapters.map { it.id })

/** Whole-class toggle across every subject (all of Class 11, say). */
internal fun toggleClassFinished(
    covered: Set<String>,
    subjects: List<OnboardingSyllabusSubject>,
    classNumber: Int,
): Set<String> = toggleAll(covered, subjects.flatMap { it.chapters }.filter { it.classNumber == classNumber }.map { it.id })

internal fun toggleChapterFinished(covered: Set<String>, chapterId: String): Set<String> =
    if (chapterId in covered) covered - chapterId else covered + chapterId

private fun toggleAll(covered: Set<String>, ids: List<String>): Set<String> {
    if (ids.isEmpty()) return covered
    return if (ids.all { it in covered }) covered - ids.toSet() else covered + ids
}

/**
 * What "finished" becomes when the starting point changes. A dropper starts with every chapter
 * finished once. Moving off dropper clears that default, but never real progress: with a saved plan
 * ([keepExisting]) or anything the student ticked themselves the set is left alone.
 */
internal fun finishedAfterStartingPointChange(
    point: StartingPoint,
    current: Set<String>,
    autoFilled: Boolean,
    keepExisting: Boolean,
    allChapterIds: Set<String>,
): Pair<Set<String>, Boolean> = when {
    keepExisting && current.isNotEmpty() -> current to false
    point == StartingPoint.DROPPER && (current.isEmpty() || autoFilled) -> allChapterIds to true
    point != StartingPoint.DROPPER && autoFilled -> emptySet<String>() to false
    else -> current to autoFilled
}

/** Share of chapters marked finished, rounded down. */
internal fun finishedPercent(covered: Set<String>, allChapterIds: Set<String>): Int =
    if (allChapterIds.isEmpty()) 0 else allChapterIds.count { it in covered } * 100 / allChapterIds.size
