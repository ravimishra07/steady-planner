package com.exam.assistant.feature.progress

import com.exam.assistant.domain.MANUAL_SESSION_DEFAULT_MINUTES
import com.exam.assistant.domain.SessionEntry
import com.exam.assistant.domain.StudyActivityType
import java.time.LocalDate

/** "Log study I did offline" and "Edit session" (PRODUCTION_V1_PLAN §6.3) share this form. */
data class LogSessionUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val subjects: List<SubjectChoice> = emptyList(),
    val subjectId: String? = null,
    val chapterId: String? = null,
    val activityType: StudyActivityType = StudyActivityType.LEARN,
    val durationMinutes: Int = MANUAL_SESSION_DEFAULT_MINUTES,
    val date: LocalDate = LocalDate.now(),
    val startMinuteOfDay: Int = 0,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** The session is gone or there is no plan; the form cannot be used. */
    val unavailable: Boolean = false,
    /** Saved or deleted: the screen closes. */
    val finished: Boolean = false,
) {
    val entry: SessionEntry
        get() = SessionEntry(chapterId, subjectId, activityType, durationMinutes, date, startMinuteOfDay)

    val canSave: Boolean get() = !loading && !saving && !unavailable && entry.isValid

    val chapters: List<ChapterChoice> get() = subjects.firstOrNull { it.id == subjectId }?.chapters.orEmpty()

    val chapterTitle: String? get() = chapters.firstOrNull { it.id == chapterId }?.title
}

data class SubjectChoice(val id: String, val name: String, val chapters: List<ChapterChoice>)

data class ChapterChoice(val id: String, val title: String)
