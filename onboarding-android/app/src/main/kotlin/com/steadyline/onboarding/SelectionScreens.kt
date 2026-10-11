@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.steadyline.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppearanceScreen(state: OnboardingUiState, actions: OnboardingActions) {
    SectionLabel("Accent")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accentOptions.forEach { option ->
            Surface(onClick = { actions.setAccent(option.id) }, modifier = Modifier.weight(1f), shape = Shapes.medium, color = if (state.accentId == option.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(modifier = Modifier.size(40.dp), shape = Shapes.full, color = Color(option.argb)) {
                        Box(contentAlignment = Alignment.Center) { if (state.accentId == option.id) Icon(Icons.Default.Check, "Selected", tint = Color.White) }
                    }
                    Text(option.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp)); SectionLabel("Background")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        appearanceOptions.forEach { option ->
            BackgroundPreview(option, state.appearanceId == option.id) { actions.setAppearance(option.id) }
        }
    }
}

@Composable
private fun BackgroundPreview(option: ColorOption, selected: Boolean, onClick: () -> Unit) {
    val bg = Color(option.argb)
    val ink = Color(requireNotNull(option.inkArgb))
    Surface(onClick = onClick, modifier = Modifier.width(112.dp), shape = Shapes.medium, color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = Shapes.small, color = bg, border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                Box {
                    Box(Modifier.padding(start = 8.dp, top = 10.dp).fillMaxWidth(.65f).height(4.dp).background(ink.copy(alpha = .85f), Shapes.full))
                    Box(Modifier.padding(start = 8.dp, top = 20.dp, end = 8.dp).fillMaxWidth().height(3.dp).background(ink.copy(alpha = .4f), Shapes.full))
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp).size(12.dp).background(MaterialTheme.colorScheme.primary, Shapes.full))
                }
            }
            Text(option.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ExamScreen(state: OnboardingUiState, actions: OnboardingActions) {
    exams.forEach { ChoiceRow(it, state.examId == it.id) { actions.selectExam(it.id) } }
}

@Composable
fun CoachingScreen(state: OnboardingUiState, actions: OnboardingActions) {
    val primary = listOf(Icons.Default.Person, Icons.Default.AccountBalance, Icons.Default.School)
    coachings.forEachIndexed { index, choice -> ChoiceRow(choice, state.coachingId == choice.id, minHeight = 72.dp, leadingIcon = primary[index]) { actions.selectCoaching(choice.id) } }
    if (state.coachingId == "coaching") {
        Spacer(Modifier.height(8.dp)); SectionLabel("Institute", "Optional detail, used to label fixed classes")
        institutes.forEach { ChoiceRow(it, state.instituteId == it.id, minHeight = 64.dp, leadingIcon = Icons.Default.School) { actions.selectInstitute(it.id) } }
    }
}
