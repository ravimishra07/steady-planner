package com.exam.assistant.domain

/**
 * The student's own name for a chapter (PRODUCTION_V1_PLAN §5.2), so a coaching module's name
 * can map onto an NCERT chapter. The bundled title is never changed; progress keys on the
 * chapter's stable id, so a rename never moves or loses progress.
 */
data class ChapterTitleOverride(
    val attemptId: String,
    val chapterId: String,
    val customTitle: String,
)

/** Longest custom chapter name accepted. */
const val MAX_CHAPTER_TITLE_LENGTH = 80

/**
 * The one display-title resolver. Every screen reads chapter titles from the pack, so applying
 * the student's renames here is what makes Syllabus, Organise, Today, Focus and Insights agree.
 */
fun ExamPack.withChapterTitles(titles: Map<String, String>): ExamPack {
    if (titles.isEmpty()) return this
    return copy(
        subjects = subjects.map { subject ->
            subject.copy(
                nodes = subject.nodes.map { node ->
                    titles[node.id]?.trim()?.takeIf { it.isNotEmpty() }?.let { node.copy(title = it) } ?: node
                },
            )
        },
    )
}

/** A rename input cleaned for storage; null means "back to the original name". */
fun cleanChapterTitle(input: String?, originalTitle: String): String? =
    input?.trim()?.take(MAX_CHAPTER_TITLE_LENGTH)?.takeIf { it.isNotEmpty() && it != originalTitle }
