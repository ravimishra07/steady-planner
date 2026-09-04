package com.exam.assistant.feature.settings

import com.exam.assistant.domain.TopicProgress
import com.exam.assistant.domain.TopicProgressStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsCalculationsTest {
    @Test
    fun coverageCountsOnlyDistinctCoveredLeaves() {
        val progress = listOf(
            progress("leaf-1", TopicProgressStatus.COVERED),
            progress("leaf-1", TopicProgressStatus.COVERED),
            progress("leaf-2", TopicProgressStatus.IN_PROGRESS),
            progress("parent", TopicProgressStatus.COVERED),
        )

        assertEquals(33, coveredPercent(listOf("leaf-1", "leaf-2", "leaf-3"), progress))
    }

    @Test
    fun coverageReturnsZeroWhenNothingIsCovered() {
        assertEquals(
            0,
            coveredPercent(
                listOf("leaf-1"),
                listOf(progress("leaf-1", TopicProgressStatus.NOT_STARTED)),
            ),
        )
    }

    @Test
    fun coverageIsUnavailableWithoutSyllabusLeaves() {
        assertNull(coveredPercent(emptyList(), emptyList()))
    }

    @Test
    fun hoursAreRoundedToHalfHourSteps() {
        assertEquals(4.5f, steppedHours(4.26f), 0f)
        assertEquals(4f, steppedHours(4.24f), 0f)
    }

    private fun progress(nodeId: String, status: TopicProgressStatus) = TopicProgress(
        attemptId = "attempt",
        nodeId = nodeId,
        status = status,
        coveredAtEpochMs = null,
        updatedAtEpochMs = 1L,
    )
}
