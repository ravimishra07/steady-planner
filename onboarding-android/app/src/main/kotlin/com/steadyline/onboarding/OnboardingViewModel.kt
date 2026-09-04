package com.steadyline.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OnboardingViewModel(application: Application) : AndroidViewModel(application), OnboardingActions {
    private val persistence: OnboardingPersistence = PreferencesOnboardingPersistence(
        application.getSharedPreferences("onboarding", 0),
    )
    private val _state = MutableStateFlow(persistence.load())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    override fun selectExam(id: String) = edit { copy(examId = id) }
    override fun selectCoaching(id: String) = edit { copy(coachingId = id) }
    override fun selectInstitute(id: String) = edit { copy(instituteId = id) }
    override fun setTargetDate(epochMillis: Long) = edit { copy(targetEpochMillis = epochMillis) }
    override fun applySchedule(id: String) = edit {
        when (id) {
            "ft" -> copy(shapeId = id, weekdayHours = 8f, weekendHours = 8f)
            "job" -> copy(shapeId = id, weekdayHours = 3f, weekendHours = 8f)
            else -> copy(shapeId = "col", weekdayHours = 4f, weekendHours = 7f)
        }
    }
    override fun updateCommitmentTime(id: String, start: String?, end: String?) = edit {
        copy(commitments = commitments.map { item -> if (item.id == id) item.copy(start = start ?: item.start, end = end ?: item.end) else item })
    }
    override fun toggleCommitmentDay(id: String, day: Int) = edit {
        copy(commitments = commitments.map { item -> if (item.id != id) item else item.copy(days = if (day in item.days) item.days - day else item.days + day) })
    }
    override fun removeCommitment(id: String) = edit { copy(commitments = commitments.filterNot { it.id == id }) }
    override fun setWeekdayHours(hours: Float) = edit { copy(weekdayHours = hours) }
    override fun setWeekendHours(hours: Float) = edit { copy(weekendHours = hours) }
    override fun setStudyPlace(place: String) = edit { copy(studyPlace = place) }
    override fun setSyllabusChoice(provided: Boolean) = edit { copy(useProvidedSyllabus = provided) }
    override fun setAccent(id: String) = edit { copy(accentId = id) }
    override fun setAppearance(id: String) = edit { copy(appearanceId = id) }

    fun toggleCommitment(preset: Commitment) = edit {
        val exists = commitments.any { it.id == preset.id }
        copy(commitments = if (exists) commitments.filterNot { it.id == preset.id } else commitments + preset)
    }

    override fun back() = edit {
        val previous = Step.entries.getOrNull(index - 1) ?: return@edit this
        copy(step = previous)
    }

    override fun continueFlow() = edit {
        if (!canContinue) return@edit this
        val next = Step.entries.getOrNull(index + 1)
        if (next == null) copy(completed = true) else copy(step = next)
    }

    fun restart() {
        persistence.clear()
        _state.value = OnboardingUiState()
    }

    private fun edit(change: OnboardingUiState.() -> OnboardingUiState) {
        _state.update { it.change().also(persistence::save) }
    }
}
