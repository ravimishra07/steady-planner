package com.exam.assistant.feature.onboarding

import com.exam.assistant.domain.NEET_EXAM_ID
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRulesTest {

    @Test
    fun `defaults are valid and do not invent a school or coaching schedule`() {
        val state = OnboardingUiState()

        assertEquals(NEET_EXAM_ID, state.examId)
        assertTrue(commitmentIssuesFor(state).isEmpty())
        assertFalse(state.commitments.any { it.kind == "school" || it.kind == "coaching" })
        assertEquals(15 * 60, freeMinutesOnDay(day = 3, state))
        assertEquals(15 * 60, freeMinutesOnDay(day = 0, state))
    }

    @Test
    fun `study mode replaces only the seeded study schedule`() {
        val selfStudy = commitmentsForStudyMode(defaultCommitments(), "self")
        val school = commitmentsForStudyMode(selfStudy, "school")
        val coaching = commitmentsForStudyMode(school, "coaching")

        assertFalse(selfStudy.any { it.kind == "school" || it.kind == "coaching" })
        assertEquals(listOf("school"), school.filter { it.id.startsWith("seed-") }.map { it.kind }.filter { it == "school" || it == "coaching" })
        assertEquals(listOf("coaching"), coaching.filter { it.id.startsWith("seed-") }.map { it.kind }.filter { it == "school" || it == "coaching" })
        assertTrue(coaching.any { it.kind == "meal" })
    }

    @Test
    fun `school and lunch overlap is valid and counted once`() {
        val state = OnboardingUiState(
            commitments = commitmentsForStudyMode(defaultCommitments(), "school"),
        )

        assertTrue(commitmentIssuesFor(state).isEmpty())
        assertEquals(10 * 60, freeMinutesOnDay(day = 1, state))
    }

    @Test
    fun `capacity uses the tightest day instead of Wednesday and Sunday only`() {
        val tightMonday = OnboardingCommitment(
            id = "monday",
            kind = "school",
            labelRes = R.string.onboarding_commitment_school,
            startMinute = 6 * 60,
            endMinute = 20 * 60,
            days = setOf(1),
        )
        val tightSaturday = tightMonday.copy(id = "saturday", days = setOf(6))
        val issues = capacityIssuesFor(
            OnboardingUiState(
                commitments = listOf(tightMonday, tightSaturday),
                weekdayHours = 4f,
                weekendHours = 4f,
            ),
        )

        assertTrue(issues.any { it is CapacityIssue.Weekday && it.freeHours == 3f })
        assertTrue(issues.any { it is CapacityIssue.Weekend && it.freeHours == 3f })
    }

    @Test
    fun `dedicated NEET onboarding skips the exam chooser`() {
        assertEquals(OnboardingStep.Coaching, OnboardingStep.Appearance.next())
        assertEquals(OnboardingStep.Appearance, OnboardingStep.Coaching.previous())
        assertEquals(null, OnboardingStep.Exam.progressIndex)
        assertEquals(PROGRESS_SEGMENTS - 1, OnboardingStep.Syllabus.progressIndex)
        assertEquals(PROGRESS_SEGMENTS - 1, OnboardingStep.Plan.progressIndex)
    }

    @Test
    fun `fixed commitments split Monday into real free windows`() {
        val monday = freeWindowsFor("attempt", OnboardingUiState())
            .filter { it.dayOfWeek == DayOfWeek.MONDAY }
            .map { it.startMinuteOfDay to it.endMinuteOfDay }

        assertEquals(
            listOf(
                6 * 60 to 13 * 60,
                14 * 60 to 20 * 60 + 30,
                21 * 60 + 30 to 23 * 60,
            ),
            monday,
        )
    }

    @Test
    fun `empty days and waking-window violations block continue`() {
        val invalid = OnboardingUiState(
            commitments = listOf(
                OnboardingCommitment("a", "school", R.string.onboarding_commitment_school, 5 * 60, 9 * 60, setOf(1)),
                OnboardingCommitment("b", "coaching", R.string.onboarding_commitment_coaching, 8 * 60, 10 * 60, setOf(1)),
                OnboardingCommitment("c", "meal", R.string.onboarding_commitment_meal, 13 * 60, 14 * 60, emptySet()),
            ),
        )

        val issues = commitmentIssuesFor(invalid)
        assertTrue(issues.any { it is CommitmentIssue.OutsideWakingHours })
        assertTrue(issues.any { it is CommitmentIssue.NoDays })
    }
}
