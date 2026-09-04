package com.steadyline.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnboardingContractTest {
    @Test
    fun `appearance is first but retains prototype progress position`() {
        val state = OnboardingUiState()
        assertEquals(Step.APPEARANCE, state.step)
        assertEquals(6, state.progressIndex)
        assertNull(state.copy(step = Step.PLAN).progressIndex)
    }

    @Test
    fun `accent hashes exactly match prototype contract`() {
        assertEquals(
            listOf(0xFF2563EB, 0xFF7C3AED, 0xFF059669, 0xFFEA580C, 0xFFE11D48),
            accentOptions.map(ColorOption::argb),
        )
    }

    @Test
    fun `background hashes exactly match prototype contract`() {
        assertEquals(
            listOf(0xFFD8D7DD, 0xFFFCFCFF, 0xFF0A0A0F, 0xFFF3F4F6, 0xFF111820),
            appearanceOptions.map(ColorOption::argb),
        )
    }
}
