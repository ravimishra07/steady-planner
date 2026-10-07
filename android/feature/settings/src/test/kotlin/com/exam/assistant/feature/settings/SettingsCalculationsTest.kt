package com.exam.assistant.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCalculationsTest {
    @Test
    fun hoursAreRoundedToHalfHourSteps() {
        assertEquals(4.5f, steppedHours(4.26f), 0f)
        assertEquals(4f, steppedHours(4.24f), 0f)
    }
}
