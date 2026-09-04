@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.steadyline.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun CommitmentsScreen(state: OnboardingUiState, actions: OnboardingActions) {
    Text("Class, school, lectures — the hours you can't move. Everything else becomes study time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    state.commitments.forEach { item ->
        Surface(shape = Shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text("${item.start}–${item.end}")
                    TextButton(onClick = { actions.removeCommitment(item.id) }) { Text("Remove") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(item.start, { actions.updateCommitmentTime(item.id, start = it) }, Modifier.weight(1f), label = { Text("Starts") }, singleLine = true)
                    OutlinedTextField(item.end, { actions.updateCommitmentTime(item.id, end = it) }, Modifier.weight(1f), label = { Text("Ends") }, singleLine = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { "SMTWTFS".forEachIndexed { day, label -> FilterChip(selected = day in item.days, onClick = { actions.toggleCommitmentDay(item.id, day) }, label = { Text(label.toString()) }) } }
            }
        }
    }
    SectionLabel("Add")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("School / college", "Coaching class", "Online lectures", "Tuition", "Job", "Meal break").forEach { AssistChip(onClick = {}, label = { Text(it) }) }
    }
    SectionLabel("Awake between")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField("06:00", {}, Modifier.weight(1f), label = { Text("Up at") })
        OutlinedTextField("23:00", {}, Modifier.weight(1f), label = { Text("Lights out") })
    }
    ChoiceRow(Choice("free", "12h free on a weekday", "15h free on Sunday"), true) {}
}

@Composable
fun DateScreen(state: OnboardingUiState, actions: OnboardingActions) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.targetEpochMillis, initialDisplayedMonthMillis = state.targetEpochMillis)
    LaunchedEffect(pickerState.selectedDateMillis) { pickerState.selectedDateMillis?.let(actions::setTargetDate) }
    val dateLabel = SimpleDateFormat("EEEE, d MMM yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(state.targetEpochMillis))
    Surface(Modifier.fillMaxWidth(), shape = Shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(dateLabel, style = MaterialTheme.typography.titleLarge); Text("Exam date", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SuggestionChip(onClick = {}, label = { Text("243 days") })
        }
    }
    DatePicker(state = pickerState, modifier = Modifier.fillMaxWidth(), title = null, headline = null, showModeToggle = false)
}

@Composable
fun HoursScreen(state: OnboardingUiState, actions: OnboardingActions) {
    SectionLabel("Start with a schedule")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("ft", "Full-time", "8h weekdays · 8h weekends"), Triple("job", "Working", "3h weekdays · 8h weekends"), Triple("col", "College", "4h weekdays · 7h weekends")).forEach { shape ->
            Surface(onClick = { actions.applySchedule(shape.first) }, modifier = Modifier.weight(1f), shape = Shapes.large, color = if (state.shapeId == shape.first) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, border = if (state.shapeId == shape.first) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(12.dp)) { Text(shape.second, style = MaterialTheme.typography.labelLarge); Text(shape.third, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    HourSlider("Weekdays", state.weekdayHours, 14f, actions::setWeekdayHours)
    HourSlider("Weekends", state.weekendHours, 16f, actions::setWeekendHours)
    val weekly = state.weekdayHours * 5 + state.weekendHours * 2
    Surface(Modifier.fillMaxWidth(), shape = Shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) { Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("Each week", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); SuggestionChip({}, { Text("${weekly.clean()} hours") }) } }
    SectionLabel("Study spot", "Optional, saved for future reminders")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Home desk", "Library", "Coaching", "Hostel room", "Terrace", "Cafe", "Other").forEach { FilterChip(state.studyPlace == it, { actions.setStudyPlace(it) }, { Text(it) }) } }
}

@Composable private fun HourSlider(label: String, value: Float, max: Float, onChange: (Float) -> Unit) { Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontWeight = FontWeight.SemiBold); Text("${value.clean()} hrs", color = MaterialTheme.colorScheme.primary) }; Slider(value, onChange, valueRange = 1f..max, steps = ((max - 1f) * 2).toInt() - 1) } }

private fun Float.clean() = if (this == toInt().toFloat()) toInt().toString() else String.format(Locale.ENGLISH, "%.1f", this)

@Composable
fun SyllabusScreen(state: OnboardingUiState, actions: OnboardingActions) {
    ChoiceRow(Choice("provided", "Use the NEET UG syllabus", "79 chapters from the NCERT contents"), state.useProvidedSyllabus) { actions.setSyllabusChoice(true) }
    ChoiceRow(Choice("empty", "Start empty", "Enter your coaching's own chapters instead"), !state.useProvidedSyllabus) { actions.setSyllabusChoice(false) }
    Surface(Modifier.fillMaxWidth(), shape = Shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) { Column(Modifier.padding(16.dp)) { Text("Start with the full syllabus", fontWeight = FontWeight.SemiBold); Text("You can mark earlier work and organise chapters after setup.") } }
    OutlinedButton({}, Modifier.fillMaxWidth().height(56.dp), shape = Shapes.medium) { Text("Optional: mark chapters already covered") }
}

@Composable
fun PlanScreen(state: OnboardingUiState) {
    Text("You finish the syllabus with 569 hours spare before 2 May.", style = MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth().height(16.dp)) { Surface(Modifier.weight(125f).fillMaxHeight(), color = MaterialTheme.colorScheme.primary) {}; Surface(Modifier.weight(71f).fillMaxHeight(), color = MaterialTheme.colorScheme.secondary) {}; Surface(Modifier.weight(47f).fillMaxHeight(), color = MaterialTheme.colorScheme.tertiary) {} }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Learn 125d"); Text("Revise 71d"); Text("Slack 47d") }
    ChoiceRow(Choice("weekday", "Every weekday", "4h"), false) {}; ChoiceRow(Choice("weekend", "Every weekend day", "7h"), false) {}; ChoiceRow(Choice("dates", "Starts 1 Sep, ends 2 May", "243 days"), false) {}
    OutlinedButton({}, Modifier.fillMaxWidth(), shape = Shapes.full) { Text("Edit plan") }
    Text("You can edit your plan any time in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
