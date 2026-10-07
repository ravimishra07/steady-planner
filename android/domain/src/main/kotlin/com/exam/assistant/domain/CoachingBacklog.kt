package com.exam.assistant.domain

/**
 * Coaching backlog tracker (PRODUCTION_V1_PLAN §5.4). Coached students count backlog in their
 * batch's lectures and DPPs, not in NCERT chapters. Everything here is optional: a student with
 * no counts and no batch position has no backlog and the planner is unchanged.
 */

/** Default length of a backlog lecture block, minutes. */
const val BACKLOG_LECTURE_MINUTES = 90

/** Default length of a backlog DPP block, minutes. */
const val BACKLOG_DPP_MINUTES = 30

/** Default share of each day for live work (today's class, its DPP, same-day revision); the rest is backlog. */
const val DEFAULT_LIVE_BACKLOG_SPLIT = 60

/** Upper bound on lectures or DPPs in one chapter, so a stuck + button cannot run away. */
const val MAX_COACHING_COUNT = 99

/**
 * One chapter's coaching counts. "Done" is the highest ticked index ("done up to lecture 9"),
 * so lectures 1..[lecturesDone] are done.
 */
data class ChapterCoaching(
    val chapterId: String,
    val lectures: Int,
    val dpps: Int,
    val lecturesDone: Int,
    val dppsDone: Int,
) {
    /** The same counts with every number in range: done never exceeds the total. */
    fun normalized(): ChapterCoaching {
        val l = lectures.coerceIn(0, MAX_COACHING_COUNT)
        val d = dpps.coerceIn(0, MAX_COACHING_COUNT)
        return copy(lectures = l, dpps = d, lecturesDone = lecturesDone.coerceIn(0, l), dppsDone = dppsDone.coerceIn(0, d))
    }

    val isEmpty: Boolean get() = lectures == 0 && dpps == 0
}

/** "My batch is currently at: <chapter>, lecture k". */
data class BatchPosition(
    val chapterId: String,
    val lecture: Int,
    val updatedAtEpochMs: Long,
)

/** Lectures and DPPs behind the batch, for one subject (or all, summed). */
data class CoachingBacklog(val lectures: Int, val dpps: Int) {
    val isEmpty: Boolean get() = lectures <= 0 && dpps <= 0

    operator fun plus(other: CoachingBacklog) = CoachingBacklog(lectures + other.lectures, dpps + other.dpps)

    companion object {
        val NONE = CoachingBacklog(0, 0)
    }
}

enum class BacklogKind { LECTURE, DPP }

/** One lecture or DPP the student is behind on. [number] is 1-based within its chapter. */
data class BacklogItem(
    val chapterId: String,
    val subjectId: String,
    val kind: BacklogKind,
    val number: Int,
)

/**
 * How far the batch has taught each chapter with counts: the number of lectures and DPPs that
 * are "taught" and so count towards backlog.
 *
 * - The batch chapter counts up to its batch lecture (and as many DPPs).
 * - Chapters before it in the same subject, in [chapterOrder], count in full.
 * - Chapters after it in that subject are not taught yet and do not count.
 * - In other subjects, and with no batch position at all, a chapter with counts entered counts in
 *   full: the student enters counts for what their batch has covered.
 */
private fun taughtCounts(
    coaching: Collection<ChapterCoaching>,
    position: BatchPosition?,
    chapterOrder: List<String>,
    subjectOf: Map<String, String>,
): Map<String, Pair<Int, Int>> {
    val index = chapterOrder.withIndex().associate { it.value to it.index }
    val batchSubject = position?.let { subjectOf[it.chapterId] }
    val batchIndex = position?.let { index[it.chapterId] }
    return coaching.map { it.normalized() }.associate { chapter ->
        val taught = when {
            position == null || batchSubject == null || batchIndex == null -> chapter.lectures to chapter.dpps
            chapter.chapterId == position.chapterId -> {
                val upTo = position.lecture.coerceIn(0, chapter.lectures)
                upTo to minOf(upTo, chapter.dpps)
            }
            subjectOf[chapter.chapterId] != batchSubject -> chapter.lectures to chapter.dpps
            (index[chapter.chapterId] ?: Int.MAX_VALUE) < batchIndex -> chapter.lectures to chapter.dpps
            else -> 0 to 0
        }
        chapter.chapterId to taught
    }
}

/**
 * Lectures and DPPs behind the batch position, by subject id. Subjects with nothing behind are
 * left out. [chapterOrder] is the syllabus order the student uses; [subjectOf] maps chapter id to
 * subject id. A chapter unknown to [subjectOf] (removed from the syllabus) is ignored.
 */
fun coachingBacklogBySubject(
    coaching: Collection<ChapterCoaching>,
    position: BatchPosition?,
    chapterOrder: List<String>,
    subjectOf: Map<String, String>,
): Map<String, CoachingBacklog> {
    val taught = taughtCounts(coaching, position, chapterOrder, subjectOf)
    return coaching.map { it.normalized() }
        .mapNotNull { chapter ->
            val subject = subjectOf[chapter.chapterId] ?: return@mapNotNull null
            val (lectures, dpps) = taught[chapter.chapterId] ?: return@mapNotNull null
            subject to CoachingBacklog(
                lectures = (lectures - chapter.lecturesDone).coerceAtLeast(0),
                dpps = (dpps - chapter.dppsDone).coerceAtLeast(0),
            )
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, parts) -> parts.fold(CoachingBacklog.NONE) { total, part -> total + part } }
        .filterValues { !it.isEmpty }
}

/**
 * Every backlog lecture and DPP as a plan item, oldest first within a chapter (lecture k, then
 * DPP k), chapters in [chapterOrder], subjects taken in turn so one subject does not starve the rest.
 */
fun coachingBacklogItems(
    coaching: Collection<ChapterCoaching>,
    position: BatchPosition?,
    chapterOrder: List<String>,
    subjectOf: Map<String, String>,
): List<BacklogItem> {
    val taught = taughtCounts(coaching, position, chapterOrder, subjectOf)
    val index = chapterOrder.withIndex().associate { it.value to it.index }
    val bySubject = LinkedHashMap<String, ArrayDeque<BacklogItem>>()
    coaching.map { it.normalized() }
        .filter { subjectOf[it.chapterId] != null }
        .sortedBy { index[it.chapterId] ?: Int.MAX_VALUE }
        .forEach { chapter ->
            val subject = subjectOf.getValue(chapter.chapterId)
            val (lectures, dpps) = taught[chapter.chapterId] ?: return@forEach
            val queue = bySubject.getOrPut(subject) { ArrayDeque() }
            val last = maxOf(lectures, dpps)
            for (k in 1..last) {
                if (k in (chapter.lecturesDone + 1)..lectures) queue += BacklogItem(chapter.chapterId, subject, BacklogKind.LECTURE, k)
                if (k in (chapter.dppsDone + 1)..dpps) queue += BacklogItem(chapter.chapterId, subject, BacklogKind.DPP, k)
            }
        }
    val queues = bySubject.values.filter { it.isNotEmpty() }
    val result = ArrayList<BacklogItem>()
    while (queues.any { it.isNotEmpty() }) {
        queues.forEach { queue -> queue.removeFirstOrNull()?.let(result::add) }
    }
    return result
}

/** Minutes the backlog needs at the given block lengths. */
fun backlogMinutes(
    backlog: CoachingBacklog,
    lectureMin: Int = BACKLOG_LECTURE_MINUTES,
    dppMin: Int = BACKLOG_DPP_MINUTES,
): Int = backlog.lectures.coerceAtLeast(0) * lectureMin + backlog.dpps.coerceAtLeast(0) * dppMin

/** Minutes of a day of [dailyMinutes] that go to backlog when [liveSplitPct] percent goes to live work. */
fun backlogDailyMinutes(dailyMinutes: Int, liveSplitPct: Int): Int =
    (dailyMinutes.coerceAtLeast(0).toLong() * (100 - liveSplitPct.coerceIn(0, 100)) / 100).toInt()
