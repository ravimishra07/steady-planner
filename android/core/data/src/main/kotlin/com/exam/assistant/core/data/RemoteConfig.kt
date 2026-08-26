package com.exam.assistant.core.data

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
}
