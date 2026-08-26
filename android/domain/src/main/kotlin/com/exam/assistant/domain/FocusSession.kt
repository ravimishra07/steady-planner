package com.exam.assistant.domain

enum class FocusStatus {
    IDLE,
    RUNNING,
    PAUSED,
    DONE,
}

data class FocusBlockRef(
    val id: String,
    val title: String,
    val subtitle: String,
    val tag: BlockTag?,
    /** Non-blank when this block is a real [StudySessionRecord]; blank for the demo fallback. */
    val sessionId: String = "",
    val nodeKey: String = "",
    val isRevision: Boolean = false,
)

data class FocusSession(
    val status: FocusStatus = FocusStatus.IDLE,
    val durationSec: Int = DEFAULT_FOCUS_DURATION_SEC,
    val remainingSec: Int = DEFAULT_FOCUS_DURATION_SEC,
    val endsAtMs: Long? = null,
    val block: FocusBlockRef? = null,
    val completedToday: Int = 0,
) {
    fun withClockNow(): FocusSession {
        if (status != FocusStatus.RUNNING || endsAtMs == null) return this
        val left = ((endsAtMs - System.currentTimeMillis()) / 1000).toInt()
        return if (left <= 0) {
            copy(status = FocusStatus.DONE, remainingSec = 0, endsAtMs = null)
        } else {
            copy(remainingSec = left)
        }
    }
}

const val DEFAULT_FOCUS_DURATION_SEC = 50 * 60

fun formatFocusClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val secs = safe % 60
    return "%02d:%02d".format(minutes, secs)
}
