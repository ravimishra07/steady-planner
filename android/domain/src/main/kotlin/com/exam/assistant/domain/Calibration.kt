package com.exam.assistant.domain

import kotlin.math.roundToInt

/** Chapters with a completed Learn session and every section covered needed before the factor means anything. */
const val CALIBRATION_MIN_CHAPTERS = 3
const val CALIBRATION_MIN_FACTOR = 0.5
const val CALIBRATION_MAX_FACTOR = 2.5

/**
 * The student's personal pace against the bundled estimates (PRODUCTION_V1_PLAN §4.1).
 * [chapters] is how many finished chapters it was measured on; below
 * [CALIBRATION_MIN_CHAPTERS] it is not [active] and the factor is 1.0.
 */
data class Calibration(val factor: Double, val chapters: Int) {
    val active: Boolean get() = chapters >= CALIBRATION_MIN_CHAPTERS

    /** The factor to the nearest tenth, as the student reads it ("about 1.3x"). */
    val rounded: Double get() = (factor * 10).roundToInt() / 10.0

    companion object {
        val NONE = Calibration(1.0, 0)
    }
}

/**
 * Median of (actual focused Learn minutes ÷ estimated minutes) over chapters that
 * have at least one completed Learn session and whose sections are all covered.
 * Needs at least [CALIBRATION_MIN_CHAPTERS] such chapters, else 1.0. Clamped to
 * [CALIBRATION_MIN_FACTOR]..[CALIBRATION_MAX_FACTOR].
 *
 * [focusedLearnMinutesByNode] is focused minutes of completed Learn sessions by
 * the node id each session was on (a section or the chapter itself). A chapter is a
 * top-level node of a subject.
 */
fun personalCalibration(
    pack: ExamPack,
    focusedLearnMinutesByNode: Map<String, Int>,
    coveredLeafIds: Set<String>,
): Calibration {
    if (focusedLearnMinutesByNode.isEmpty()) return Calibration.NONE
    val ratios = pack.subjects.flatMap { subject ->
        subject.nodes.mapNotNull { chapter ->
            val nodes = chapter.flatten()
            val actual = nodes.sumOf { focusedLearnMinutesByNode[it.id] ?: 0 }
            val leaves = chapter.leafIds()
            val estimate = nodes.filter { it.children.isEmpty() }.sumOf { it.estimatedMinutes ?: 0 }
            if (actual <= 0 || estimate <= 0 || leaves.isEmpty() || !coveredLeafIds.containsAll(leaves)) null
            else actual.toDouble() / estimate
        }
    }
    if (ratios.size < CALIBRATION_MIN_CHAPTERS) return Calibration.NONE
    val sorted = ratios.sorted()
    val mid = sorted.size / 2
    val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    return Calibration(median.coerceIn(CALIBRATION_MIN_FACTOR, CALIBRATION_MAX_FACTOR), ratios.size)
}

fun personalCalibrationFactor(
    pack: ExamPack,
    focusedLearnMinutesByNode: Map<String, Int>,
    coveredLeafIds: Set<String>,
): Double = personalCalibration(pack, focusedLearnMinutesByNode, coveredLeafIds).factor
