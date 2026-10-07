package com.exam.assistant.domain

/**
 * Per-chapter stages (PRODUCTION_V1_PLAN §5.1). Only [LEARNED] changes coverage, and it is
 * not stored as a stage row: it is the chapter's existing covered state in [TopicProgress],
 * so Metrics stays the one coverage source. Every other stage is depth, stored per chapter.
 */
enum class ChapterStage {
    LEARNED,
    NCERT,
    PRACTICE,
    PYQ,
    REVISED,
    CUSTOM,
}

/** One stored stage mark. [count] matters only for [ChapterStage.REVISED]; ticks are count 1. */
data class ChapterStageMark(
    val attemptId: String,
    val chapterId: String,
    val stage: ChapterStage,
    val count: Int,
    val updatedAtEpochMs: Long?,
)

/** The student's own stage labels: renames, hidden stages, and the one custom stage's name. */
data class StageLabels(
    val renamed: Map<ChapterStage, String> = emptyMap(),
    val hidden: Set<ChapterStage> = emptySet(),
    val customLabel: String? = null,
) {
    /** The stages a chapter row shows, in order. The custom stage needs a name before it appears. */
    fun visibleStages(): List<ChapterStage> = ChapterStage.entries.filter { stage ->
        stage !in hidden && (stage != ChapterStage.CUSTOM || !customLabel.isNullOrBlank())
    }

    /** The student's label for [stage], or null to use the default. */
    fun labelFor(stage: ChapterStage): String? =
        if (stage == ChapterStage.CUSTOM) customLabel?.takeIf { it.isNotBlank() }
        else renamed[stage]?.takeIf { it.isNotBlank() }
}

/**
 * How many times a chapter has been revised: the most revisions any node in it has had through
 * Focus ([RevisionState], the schedule's counter) plus revisions the student logged by hand.
 */
fun chapterRevisionCount(
    chapter: SyllabusNode,
    revisionStates: Map<String, RevisionState>,
    manualCount: Int,
): Int {
    val focusCount = chapter.flatten().maxOfOrNull { revisionStates[it.id]?.revisionCount ?: 0 } ?: 0
    return focusCount + manualCount
}
