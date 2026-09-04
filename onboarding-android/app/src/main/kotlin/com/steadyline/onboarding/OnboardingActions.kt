package com.steadyline.onboarding

interface OnboardingActions {
    fun back()
    fun continueFlow()
    fun selectExam(id: String)
    fun selectCoaching(id: String)
    fun selectInstitute(id: String)
    fun setTargetDate(epochMillis: Long)
    fun applySchedule(id: String)
    fun updateCommitmentTime(id: String, start: String? = null, end: String? = null)
    fun toggleCommitmentDay(id: String, day: Int)
    fun removeCommitment(id: String)
    fun setWeekdayHours(hours: Float)
    fun setWeekendHours(hours: Float)
    fun setStudyPlace(place: String)
    fun setSyllabusChoice(provided: Boolean)
    fun setAccent(id: String)
    fun setAppearance(id: String)
}
