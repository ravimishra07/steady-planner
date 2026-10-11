package com.steadyline.onboarding

import android.content.SharedPreferences

interface OnboardingPersistence {
    fun load(): OnboardingUiState
    fun save(state: OnboardingUiState)
    fun clear()
}

class PreferencesOnboardingPersistence(
    private val preferences: SharedPreferences,
) : OnboardingPersistence {
    override fun save(state: OnboardingUiState) {
        preferences.edit()
            .putString("step", state.step.name)
            .putString("exam", state.examId)
            .putString("coaching", state.coachingId)
            .putString("institute", state.instituteId)
            .putStringSet("commitments", state.commitments.map(::encodeCommitment).toSet())
            .putLong("targetDate", state.targetEpochMillis)
            .putString("shape", state.shapeId)
            .putFloat("weekday", state.weekdayHours)
            .putFloat("weekend", state.weekendHours)
            .putString("place", state.studyPlace)
            .putBoolean("provided", state.useProvidedSyllabus)
            .putString("accent", state.accentId)
            .putString("appearance", state.appearanceId)
            .putBoolean("completed", state.completed)
            .apply()
    }

    override fun load(): OnboardingUiState = OnboardingUiState(
        step = savedStep(),
        examId = preferences.string("exam", "neet"),
        coachingId = preferences.string("coaching", "coaching"),
        instituteId = preferences.string("institute", "allen"),
        commitments = savedCommitments(),
        targetEpochMillis = preferences.getLong("targetDate", 1_809_216_000_000L),
        shapeId = preferences.string("shape", "col"),
        weekdayHours = preferences.getFloat("weekday", 4f),
        weekendHours = preferences.getFloat("weekend", 7f),
        studyPlace = preferences.string("place", "Home desk"),
        useProvidedSyllabus = preferences.getBoolean("provided", true),
        accentId = preferences.string("accent", "purple"),
        appearanceId = preferences.string("appearance", "dark"),
        completed = preferences.getBoolean("completed", false),
    )

    override fun clear() = preferences.edit().clear().apply()

    private fun savedStep() = runCatching {
        Step.valueOf(preferences.string("step", Step.APPEARANCE.name))
    }.getOrDefault(Step.APPEARANCE)

    private fun savedCommitments(): List<Commitment> {
        if (!preferences.contains("commitments")) return commitmentPresets
        return preferences.getStringSet("commitments", emptySet()).orEmpty().mapNotNull(::decodeCommitment)
    }

    private fun SharedPreferences.string(key: String, fallback: String) = getString(key, fallback).orEmpty()
}

private fun encodeCommitment(item: Commitment) = listOf(
    item.id, item.title, item.start, item.end, item.days.sorted().joinToString(","),
).joinToString("|")

private fun decodeCommitment(raw: String): Commitment? {
    val fields = raw.split('|')
    if (fields.size != 5) return null
    return Commitment(fields[0], fields[1], fields[2], fields[3], fields[4].split(',').mapNotNull(String::toIntOrNull).toSet())
}
