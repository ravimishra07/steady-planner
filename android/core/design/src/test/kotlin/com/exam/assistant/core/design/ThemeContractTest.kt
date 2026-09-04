package com.exam.assistant.core.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeContractTest {
    @Test
    fun purpleDark_matchesAngularMaterialRoles() {
        val colors = resolveAppColors(BackgroundAppearance.Dark, AccentPalette.Purple)

        assertEquals(Color(0xFFD5BAFF), colors.primary)
        assertEquals(Color(0xFF42008A), colors.onPrimary)
        assertEquals(Color(0xFF5F00C0), colors.primaryContainer)
        assertEquals(Color(0xFFECDCFF), colors.onPrimaryContainer)
        assertEquals(Color(0xFF151316), colors.surface)
        assertEquals(Color(0xFFE6E1E6), colors.onSurface)
        assertEquals(Color(0xFF211F22), colors.surfaceContainer)
        assertEquals(Color(0xFF4B4357), colors.selectionContainer)
        assertEquals(Color(0xFFEADEF7), colors.onSelectionContainer)
        assertEquals(Color(0xFF958E99), colors.outline)
        assertEquals(Color(0xFFFFB4AB), colors.error)
    }

    @Test
    fun systemAppearance_resolvesBothPlatformModes() {
        val light = resolveAppColors(BackgroundAppearance.System, AccentPalette.Blue, systemDark = false)
        val dark = resolveAppColors(BackgroundAppearance.System, AccentPalette.Blue, systemDark = true)

        assertEquals(Color(0xFF005CBB), light.primary)
        assertEquals(Color(0xFFFEF8FC), light.surface)
        assertEquals(Color(0xFFABC7FF), dark.primary)
        assertEquals(Color(0xFF151316), dark.surface)
    }

    @Test
    fun greyAndSlate_onlyShiftTheAngularSurfaceFamily() {
        val grey = resolveAppColors(BackgroundAppearance.Grey, AccentPalette.Green)
        val slate = resolveAppColors(BackgroundAppearance.Slate, AccentPalette.Green)

        assertEquals(Color(0xFFF3F4F6), grey.surface)
        assertEquals(Color(0xFFE9EAEE), grey.surfaceContainer)
        assertEquals(Color(0xFF111820), slate.surface)
        assertEquals(Color(0xFF1A232D), slate.surfaceContainer)
        assertEquals(Color(0xFF02E600), slate.primary)
    }
}
