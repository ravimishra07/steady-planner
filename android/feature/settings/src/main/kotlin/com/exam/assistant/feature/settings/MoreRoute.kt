package com.exam.assistant.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SavedPlan

@Composable
fun MoreRoute(
    planStore: PlanStore,
    onOpenSettings: () -> Unit,
    onRedoOnboarding: () -> Unit,
    onOpenPolicy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var plan by remember { mutableStateOf<SavedPlan?>(null) }
    LaunchedEffect(Unit) {
        plan = planStore.load()
    }
    MoreScreen(
        planExamLabel = plan?.let { examLabel(it.examId) },
        daysLeft = plan?.daysUntilExam,
        onOpenSettings = onOpenSettings,
        onRedoOnboarding = onRedoOnboarding,
        onOpenPolicy = onOpenPolicy,
        modifier = modifier,
    )
}

private fun examLabel(examId: String): String = when (examId) {
    "cgl" -> "SSC CGL"
    "chsl" -> "SSC CHSL"
    else -> examId.uppercase()
}
