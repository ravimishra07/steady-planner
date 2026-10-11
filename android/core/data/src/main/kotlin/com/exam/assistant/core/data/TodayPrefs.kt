package com.exam.assistant.core.data

data class TodayPrefs(
    val activeSubjects: Set<String> = emptySet(),
    val blocksDone: Set<String> = emptySet(),
)
