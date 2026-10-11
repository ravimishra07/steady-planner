package com.steadyline.onboarding

enum class Step { APPEARANCE, EXAM, COACHING, COMMITMENTS, DATE, HOURS, SYLLABUS, PLAN }

data class Choice(val id: String, val title: String, val subtitle: String)

data class ColorOption(val id: String, val label: String, val argb: Long, val inkArgb: Long? = null)

val accentOptions = listOf(
    ColorOption("blue", "Blue", 0xFF2563EB),
    ColorOption("purple", "Purple", 0xFF7C3AED),
    ColorOption("green", "Green", 0xFF059669),
    ColorOption("amber", "Amber", 0xFFEA580C),
    ColorOption("rose", "Rose", 0xFFE11D48),
)

val appearanceOptions = listOf(
    ColorOption("system", "System", 0xFFD8D7DD, 0xFF343239),
    ColorOption("light", "Light", 0xFFFCFCFF, 0xFF191C25),
    ColorOption("dark", "Dark", 0xFF0A0A0F, 0xFFF4F3F8),
    ColorOption("grey", "Grey", 0xFFF3F4F6, 0xFF1B1C20),
    ColorOption("slate", "Slate", 0xFF111820, 0xFFF0F4F8),
)

data class Commitment(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val days: Set<Int>,
)

data class OnboardingUiState(
    val step: Step = Step.APPEARANCE,
    val examId: String = "neet",
    val coachingId: String = "coaching",
    val instituteId: String = "allen",
    val commitments: List<Commitment> = commitmentPresets,
    val targetEpochMillis: Long = 1_809_216_000_000L,
    val shapeId: String = "col",
    val weekdayHours: Float = 4f,
    val weekendHours: Float = 7f,
    val studyPlace: String = "Home desk",
    val useProvidedSyllabus: Boolean = true,
    val accentId: String = "purple",
    val appearanceId: String = "dark",
    val completed: Boolean = false,
) {
    val index: Int get() = Step.entries.indexOf(step)
    val progressIndex: Int? get() = when (step) {
        Step.EXAM -> 0
        Step.COACHING -> 1
        Step.COMMITMENTS -> 2
        Step.DATE -> 3
        Step.HOURS -> 4
        Step.SYLLABUS -> 5
        Step.APPEARANCE -> 6
        Step.PLAN -> null
    }
    val canContinue: Boolean get() = when (step) {
        Step.EXAM -> examId.isNotBlank()
        Step.COACHING -> coachingId.isNotBlank()
        else -> true
    }
}

val exams = listOf(
    Choice("neet", "NEET UG", "Full syllabus included"),
    Choice("jee", "JEE Main", "Add your own chapters"),
    Choice("cgl", "SSC CGL", "Add your own chapters"),
)

val coachings = listOf(
    Choice("self", "Self-study", "I set my own pace"),
    Choice("school", "School or college", "Classes set the pace"),
    Choice("coaching", "Coaching", "Online or classroom"),
)

val institutes = listOf(
    Choice("allen", "Allen Career Institute", "Classroom · Kota"),
    Choice("aakash", "Aakash Institute", "Classroom · Pan-India"),
    Choice("pw", "Physics Wallah", "Online"),
    Choice("narayana", "Narayana", "Classroom · Hyderabad"),
    Choice("chaitanya", "Sri Chaitanya", "Classroom · Hyderabad"),
    Choice("resonance", "Resonance", "Classroom · Kota"),
    Choice("unacademy", "Unacademy", "Online"),
)

val commitmentPresets = listOf(
    Commitment("coaching", "Coaching class", "16:00", "19:00", setOf(1, 2, 3, 4, 5, 6)),
    Commitment("lunch", "Lunch", "13:00", "14:00", (0..6).toSet()),
    Commitment("dinner", "Dinner", "20:30", "21:30", (0..6).toSet()),
)
