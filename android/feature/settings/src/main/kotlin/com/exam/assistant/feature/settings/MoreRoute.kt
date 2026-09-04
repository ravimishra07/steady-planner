package com.exam.assistant.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SavedPlan
import com.exam.assistant.core.data.decodedCommitments
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.domain.NEET_EXAM_ID
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MoreRoute(
    planStore: PlanStore,
    background: BackgroundAppearance,
    onOpenAppearance: () -> Unit,
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
        planExamLabel = plan?.takeIf { it.examId == NEET_EXAM_ID }?.let { stringResource(R.string.settings_exam_neet) },
        daysLeft = plan?.daysUntilExam,
        examDateLabel = plan?.targetDateEpochDay?.let { epochDay ->
            LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        },
        weekdayHours = plan?.weekdayHours,
        weekendHours = plan?.weekendHours,
        fixedBlockCount = plan?.decodedCommitments()?.size ?: 0,
        background = background,
        onOpenAppearance = onOpenAppearance,
        onOpenSettings = onOpenSettings,
        onRedoOnboarding = onRedoOnboarding,
        onOpenPolicy = onOpenPolicy,
        modifier = modifier,
    )
}
