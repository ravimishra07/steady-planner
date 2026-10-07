package com.exam.assistant.domain

/**
 * How the student wants the plan shaped (PRODUCTION_V1_PLAN §3.1).
 *
 * - [DEADLINE]: a finish-by date exists. Work is spread up to it; the days from
 *   finish-by to the exam are a revision phase (no new Learn, passes + mocks).
 * - [STEADY]: no finish-by date. Learn, Practice and Revise interleave with no
 *   phase switch, and there is never a "behind" or "short" state.
 */
enum class PlanMode {
    DEADLINE,
    STEADY,
}
