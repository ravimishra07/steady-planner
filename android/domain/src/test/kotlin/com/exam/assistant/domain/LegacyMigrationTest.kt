package com.exam.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class LegacyMigrationTest {

    private fun samplePack(): ExamPack {
        val subject = ExamSubject(
            id = "quant",
            name = "Quant",
            order = 0,
            nodes = listOf(
                SyllabusNode(
                    id = "quant.number_system",
                    title = "Number System",
                    kind = SyllabusNodeKind.CHAPTER,
                    order = 0,
                    children = listOf(
                        SyllabusNode("quant.number_system.divisibility", "Divisibility", SyllabusNodeKind.SUBTOPIC, order = 0),
                    ),
                ),
            ),
        )
        return ExamPack(1, "ssc_cgl", "SSC CGL", "2026.v1", listOf(subject))
    }

    private fun legacySections(): List<SyllabusSection> = listOf(
        SyllabusSection(
            name = "Quant",
            questions = 25,
            topics = listOf(
                SyllabusTopicNode(
                    name = "Number System",
                    hours = 14.0,
                    children = listOf(SyllabusTopicNode(name = "Divisibility", hours = null)),
                ),
            ),
        ),
    )

    @Test
    fun `old positional nodeKey maps to the new stable id`() {
        val map = buildLegacyNodeIdMap(samplePack(), legacySections())
        assertEquals("quant.number_system", map["t1_0_0"])
        assertEquals("quant.number_system.divisibility", map["t1_0_0_0"])
    }

    @Test
    fun `section key maps to the subject's stable id`() {
        val map = buildLegacySectionIdMap(samplePack())
        assertEquals("quant", map["section_0"])
    }

    @Test
    fun `doneLeaves migrate to COVERED topic progress, unmapped keys are dropped`() {
        val nodeIdMap = buildLegacyNodeIdMap(samplePack(), legacySections())
        val migrated = migrateTopicProgress("attempt1", setOf("t1_0_0_0", "unknown_key"), nodeIdMap, nowMs = 1000L)
        assertEquals(1, migrated.size)
        assertEquals("quant.number_system.divisibility", migrated.single().nodeId)
        assertEquals(TopicProgressStatus.COVERED, migrated.single().status)
    }

    @Test
    fun `excludedSectionKeys migrate to EXCLUDED target overrides`() {
        val sectionIdMap = buildLegacySectionIdMap(samplePack())
        val overrides = migrateTargetOverrides("attempt1", setOf("section_0"), sectionIdMap, nowMs = 1000L)
        assertEquals(TargetNodeState.EXCLUDED, overrides.single().state)
        assertEquals("quant", overrides.single().nodeId)
    }

    @Test
    fun `a completed legacy record creates a plan block and a completed session`() {
        val nodeIdMap = buildLegacyNodeIdMap(samplePack(), legacySections())
        val record = StudySessionRecord(
            id = UUID.randomUUID().toString(),
            date = LocalDate.of(2026, 1, 1),
            startMinuteOfDay = 360,
            durationMinutes = 50,
            nodeKey = "t1_0_0_0",
            title = "Divisibility",
            sectionName = "Number System",
            subjectId = "quant",
            completed = true,
        )
        val result = migrateStudySessionRecord(record, "attempt1", nodeIdMap, nowMs = 1000L)
        assertEquals(PlanBlockStatus.COMPLETED, result.planBlock.status)
        assertTrue(result.session != null)
        assertEquals(StudySessionStatus.COMPLETED, result.session!!.status)
        assertEquals(50 * 60, result.session.focusedSeconds)
        assertEquals("quant.number_system.divisibility", result.session.nodeId)
    }

    @Test
    fun `a scheduled-only legacy record creates a plan block but no session`() {
        val record = StudySessionRecord(
            id = UUID.randomUUID().toString(),
            date = LocalDate.of(2026, 1, 1),
            startMinuteOfDay = 360,
            durationMinutes = 50,
            nodeKey = "t1_0_0_0",
            title = "Divisibility",
            sectionName = "Number System",
            subjectId = "quant",
            completed = false,
        )
        val result = migrateStudySessionRecord(record, "attempt1", emptyMap(), nowMs = 1000L)
        assertEquals(PlanBlockStatus.PLANNED, result.planBlock.status)
        assertNull(result.session)
    }

    @Test
    fun `a currently-running legacy record creates a running session`() {
        val record = StudySessionRecord(
            id = UUID.randomUUID().toString(),
            date = LocalDate.of(2026, 1, 1),
            startMinuteOfDay = 360,
            durationMinutes = 50,
            nodeKey = "t1_0_0_0",
            title = "Divisibility",
            sectionName = "Number System",
            subjectId = "quant",
            completed = false,
            runningEndsAtMs = 999_999L,
        )
        val result = migrateStudySessionRecord(record, "attempt1", emptyMap(), nowMs = 1000L)
        assertEquals(StudySessionStatus.RUNNING, result.session?.status)
        assertNull(result.session?.endedAtEpochMs)
    }

    @Test
    fun `migration is idempotent - same record produces the same deterministic ids every time`() {
        val record = StudySessionRecord(
            id = "fixed-id",
            date = LocalDate.of(2026, 1, 1),
            startMinuteOfDay = 360,
            durationMinutes = 50,
            nodeKey = "t1_0_0_0",
            title = "Divisibility",
            sectionName = "Number System",
            subjectId = "quant",
            completed = true,
        )
        val first = migrateStudySessionRecord(record, "attempt1", emptyMap(), nowMs = 1000L)
        val second = migrateStudySessionRecord(record, "attempt1", emptyMap(), nowMs = 2000L)
        assertEquals(first.planBlock.id, second.planBlock.id)
        assertEquals(first.session?.id, second.session?.id)
    }
}
