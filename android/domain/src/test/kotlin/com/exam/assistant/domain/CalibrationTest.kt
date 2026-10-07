package com.exam.assistant.domain

import com.exam.assistant.domain.PlannerFixture.pack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {
    private val pack = pack(physicsChapters = 3, chemistryChapters = 2) // every chapter: two 60 minute sections
    private fun covered(vararg chapters: String) = chapters.flatMap { listOf("${it}a", "${it}b") }.toSet()

    @Test
    fun `needs at least three finished chapters`() {
        val two = personalCalibration(pack, mapOf("P1a" to 300, "P2a" to 300), covered("P1", "P2"))
        assertEquals(1.0, two.factor, 0.0)
        assertFalse(two.active)
        assertEquals(1.0, personalCalibrationFactor(pack, mapOf("P1a" to 300, "P2a" to 300), covered("P1", "P2")), 0.0)
    }

    @Test
    fun `is the median of actual over estimate`() {
        // Estimates are 120 each. Ratios 1.0, 1.5, 3.0 -> unclamped median 1.5.
        val minutes = mapOf("P1a" to 60, "P1b" to 60, "P2a" to 180, "P3a" to 360)
        val calibration = personalCalibration(pack, minutes, covered("P1", "P2", "P3"))
        assertEquals(1.5, calibration.factor, 1e-9)
        assertEquals(3, calibration.chapters)
        assertTrue(calibration.active)
        assertEquals(1.5, calibration.rounded, 0.0)
    }

    @Test
    fun `an even number of chapters averages the two middle ratios`() {
        val minutes = mapOf("P1a" to 120, "P2a" to 180, "P3a" to 240, "C1a" to 600) // 1, 1.5, 2, 5
        val calibration = personalCalibration(pack, minutes, covered("P1", "P2", "P3", "C1"))
        assertEquals(1.75, calibration.factor, 1e-9)
    }

    @Test
    fun `is clamped to 0_5 and 2_5`() {
        val slow = personalCalibration(
            pack, mapOf("P1a" to 600, "P2a" to 600, "P3a" to 600), covered("P1", "P2", "P3"),
        )
        val fast = personalCalibration(
            pack, mapOf("P1a" to 10, "P2a" to 10, "P3a" to 10), covered("P1", "P2", "P3"),
        )
        assertEquals(2.5, slow.factor, 0.0)
        assertEquals(0.5, fast.factor, 0.0)
        assertTrue(slow.active && fast.active)
    }

    @Test
    fun `only chapters with every section covered and some learn time count`() {
        val minutes = mapOf("P1a" to 120, "P2a" to 120, "P3a" to 120, "C1a" to 999, "C2a" to 0)
        // C1 is not fully covered (only section a); C2 has no learn minutes.
        val covered = covered("P1", "P2", "P3") + "C1a" + covered("C2")
        val calibration = personalCalibration(pack, minutes, covered)
        assertEquals(3, calibration.chapters)
        assertEquals(1.0, calibration.factor, 1e-9)
    }

    @Test
    fun `time on a chapter node itself counts for that chapter`() {
        val minutes = mapOf("P1" to 240, "P2" to 240, "P3" to 240)
        val calibration = personalCalibration(pack, minutes, covered("P1", "P2", "P3"))
        assertEquals(2.0, calibration.factor, 1e-9)
    }

    @Test
    fun `feeds the canonical remaining work`() {
        val target = pack.leafIds().toSet()
        assertEquals(600, remainingMinutes(pack, target, emptySet(), calibrationFactor = 1.0))
        assertEquals(780, remainingMinutes(pack, target, emptySet(), calibrationFactor = 1.3))
        assertEquals(600, remainingMinutes(pack, target, emptySet(), Calibration.NONE.factor))
    }

    @Test
    fun `no sessions means no calibration`() {
        assertEquals(Calibration.NONE, personalCalibration(pack, emptyMap(), emptySet()))
        assertFalse(Calibration.NONE.active)
    }
}
