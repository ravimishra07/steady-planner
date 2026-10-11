package com.exam.assistant.core.data

import com.exam.assistant.domain.DEFAULT_NEET_EXAM_DATE
import com.exam.assistant.domain.resolveExamDate
import java.time.LocalDate

/**
 * Config with compile-time defaults, so a real backend can be added later
 * without rewiring call sites.
 *
 * Rules: every key has a default; the app is correct offline and on first
 * launch with no fetch; [refresh] never runs on the critical path and its
 * values apply on the next launch, not mid-session.
 */
interface RemoteConfig {
    fun bool(key: String, default: Boolean): Boolean
    fun int(key: String, default: Int): Int
    fun string(key: String, default: String): String
    suspend fun refresh()
}

/** The current implementation: defaults only. */
class LocalRemoteConfig : RemoteConfig {
    override fun bool(key: String, default: Boolean) = default
    override fun int(key: String, default: Int) = default
    override fun string(key: String, default: String) = default
    override suspend fun refresh() = Unit
}

/** Keys live here so they cannot be typo'd at call sites. */
object ConfigKeys {
    const val PAYWALL_ENABLED = "paywall_enabled"
    const val TIER2_ENABLED = "tier2_enabled"
    const val DEFAULT_ACCENT_THEME = "defaultAccentTheme"
    const val DEFAULT_BACKGROUND_THEME = "defaultBackgroundTheme"
    const val THEME_CUSTOMIZATION_ENABLED = "themeCustomizationEnabled"

    /** ISO date (yyyy-MM-dd) of the NEET exam. Default: [DEFAULT_NEET_EXAM_DATE]. */
    const val NEET_EXAM_DATE = "neet_exam_date"

    /** True once NTA has announced the date. While false the UI says "expected, not yet announced". */
    const val NEET_EXAM_DATE_OFFICIAL = "neet_exam_date_official"
}

/** The NEET exam date from config, with the compile-time default. */
fun RemoteConfig.neetExamDate(): LocalDate =
    resolveExamDate(string(ConfigKeys.NEET_EXAM_DATE, DEFAULT_NEET_EXAM_DATE))

/** Whether [neetExamDate] has been officially announced. Default false. */
fun RemoteConfig.neetExamDateOfficial(): Boolean = bool(ConfigKeys.NEET_EXAM_DATE_OFFICIAL, false)
