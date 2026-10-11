package com.exam.assistant.core.common

/**
 * Product analytics (PRODUCTION_V1_PLAN §11.3). Events carry ids and enums
 * only: never names, free text, dates of birth, contact details or syllabus
 * titles. Param values are String, Long, Int, Double or Boolean.
 *
 * `:app` provides the Firebase implementation when Firebase is configured and
 * [NoOp] otherwise, so nothing here may assume a backend exists.
 */
interface Analytics {
    fun log(event: String, params: Map<String, Any> = emptyMap())

    /** Settings → "Share anonymous usage data". Also switches crash reporting. */
    fun setCollectionEnabled(enabled: Boolean)

    object NoOp : Analytics {
        override fun log(event: String, params: Map<String, Any>) = Unit
        override fun setCollectionEnabled(enabled: Boolean) = Unit
    }
}

/** Every event name, so call sites cannot drift from the list in §11.3. */
object AnalyticsEvent {
    const val ONBOARDING_STARTED = "onboarding_started"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val PLAN_GENERATED = "plan_generated"
    const val BLOCK_STARTED = "block_started"
    const val BLOCK_COMPLETED = "block_completed"
    const val BLOCK_MISSED = "block_missed"
    const val BLOCK_MOVED = "block_moved"
    const val BLOCK_DROPPED = "block_dropped"
    const val CHAPTER_LEARNED = "chapter_learned"
    const val REVISION_COMPLETED = "revision_completed"
    const val MOCK_LOGGED = "mock_logged"
    const val PAYWALL_VIEWED = "paywall_viewed"
    const val PURCHASE_STARTED = "purchase_started"
    const val PURCHASE_COMPLETED = "purchase_completed"
    const val RESTORE_COMPLETED = "restore_completed"
    const val WIDGET_ADDED = "widget_added"
    const val EXPORT_DONE = "export_done"
}

/** Param keys. Values are enums, counts or flags. */
object AnalyticsParam {
    const val MODE = "mode"
    const val STARTING_POINT = "starting_point"
    const val COUNT = "count"
    const val IS_REVISION = "is_revision"
    const val ACTIVITY = "activity"
    const val PLANNED = "planned"
    const val ACTION = "action"
    const val SOURCE = "source"
    const val MOCK_TYPE = "mock_type"
    const val IS_EDIT = "is_edit"
    const val FOUND = "found"
    const val WIDGET = "widget"
}
