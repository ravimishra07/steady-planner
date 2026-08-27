package com.exam.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLockTest {

    private val caps = FocusLockCapabilities(usageAccessGranted = true, overlayGranted = true)

    @Test
    fun `off when not enabled`() {
        val state = focusLockDisplayState(FocusLockSettings(enabled = false), caps, null)
        assertEquals(FocusLockDisplayState.Off, state)
    }

    @Test
    fun `needs setup when enabled but no apps chosen`() {
        val state = focusLockDisplayState(FocusLockSettings(enabled = true), caps, null)
        assertEquals(FocusLockDisplayState.NeedsSetup, state)
    }

    @Test
    fun `needs attention when configured but capability missing`() {
        val settings = FocusLockSettings(enabled = true, blockedPackages = setOf("com.example.a"))
        val state = focusLockDisplayState(settings, FocusLockCapabilities(true, false), null)
        assertEquals(FocusLockDisplayState.NeedsAttention, state)
    }

    @Test
    fun `ready when configured and granted with no session`() {
        val settings = FocusLockSettings(enabled = true, blockedPackages = setOf("com.example.a", "com.example.b"))
        val state = focusLockDisplayState(settings, caps, null)
        assertEquals(FocusLockDisplayState.Ready(2), state)
    }

    @Test
    fun `active when a session is running`() {
        val settings = FocusLockSettings(enabled = true, blockedPackages = setOf("com.example.a"))
        val session = ActiveStudySessionInfo("Percentage", 900)
        val state = focusLockDisplayState(settings, caps, session)
        assertEquals(FocusLockDisplayState.Active(1, session), state)
    }

    @Test
    fun `own package is never blocked`() {
        val settings = FocusLockSettings(enabled = true, blockedPackages = setOf("com.exam.assistant"))
        assertFalse(shouldBlockPackage(settings, caps, "com.exam.assistant", "com.exam.assistant", 0L))
    }

    @Test
    fun `unlisted package is not blocked`() {
        val settings = FocusLockSettings(enabled = true, blockedPackages = setOf("com.example.a"))
        assertFalse(shouldBlockPackage(settings, caps, "com.example.other", "com.exam.assistant", 0L))
    }

    @Test
    fun `listed package is blocked when capabilities granted`() {
        val settings = FocusLockSettings(enabled = true, blockedPackages = setOf("com.example.a"))
        assertTrue(shouldBlockPackage(settings, caps, "com.example.a", "com.exam.assistant", 0L))
    }

    @Test
    fun `disabled focus lock blocks nothing even if configured`() {
        val settings = FocusLockSettings(enabled = false, blockedPackages = setOf("com.example.a"))
        assertFalse(shouldBlockPackage(settings, caps, "com.example.a", "com.exam.assistant", 0L))
    }

    @Test
    fun `missing capability blocks nothing`() {
        val settings = FocusLockSettings(enabled = true, blockedPackages = setOf("com.example.a"))
        val partialCaps = FocusLockCapabilities(usageAccessGranted = true, overlayGranted = false)
        assertFalse(shouldBlockPackage(settings, partialCaps, "com.example.a", "com.exam.assistant", 0L))
    }

    @Test
    fun `temporary allowance suppresses blocking until it expires`() {
        val settings = withTemporaryAllowance(
            FocusLockSettings(enabled = true, blockedPackages = setOf("com.example.a")),
            "com.example.a",
            nowMs = 1_000L,
        )
        assertFalse(shouldBlockPackage(settings, caps, "com.example.a", "com.exam.assistant", nowMs = 1_000L + 60_000L))
        assertTrue(shouldBlockPackage(settings, caps, "com.example.a", "com.exam.assistant", nowMs = 1_000L + 6 * 60_000L))
    }

    @Test
    fun `expired allowances are cleared`() {
        val settings = FocusLockSettings(
            enabled = true,
            temporaryAllowances = mapOf("com.example.a" to 1_000L, "com.example.b" to 5_000L),
        )
        val cleared = clearExpiredAllowances(settings, nowMs = 2_000L)
        assertEquals(setOf("com.example.b"), cleared.temporaryAllowances.keys)
    }

    @Test
    fun `protected packages are never blockable`() {
        assertTrue(isProtectedPackage("com.android.systemui"))
        assertTrue(isProtectedPackage("com.android.settings"))
        assertTrue(isProtectedPackage("com.android.phone"))
        assertFalse(isProtectedPackage("com.instagram.android"))
    }
}
