package com.exam.assistant.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.exam.assistant.R
import com.exam.assistant.SteadylineApp
import com.exam.assistant.core.design.FontSize
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.today.TodayBlock
import com.exam.assistant.today.TodaySnapshot
import com.exam.assistant.today.loadTodaySnapshot
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** Small widget: days to exam and syllabus coverage, the same numbers Today shows. Tap opens Today. */
class StatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = (context.applicationContext as SteadylineApp).container.loadTodaySnapshot()
        provideContent { GlanceTheme { StatusContent(snapshot) } }
    }
}

/** Medium widget: the next planned block today and a Start button that opens Focus running it. */
class NextBlockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as SteadylineApp).container
        val unlocked = runCatching { container.proRepository.unlockedOnce() }.getOrDefault(false)
        val snapshot = container.loadTodaySnapshot()
        val now = LocalTime.now()
        val next = snapshot.nextBlock(now.hour * 60 + now.minute)
        provideContent { GlanceTheme { if (unlocked) NextBlockContent(snapshot.hasPlan, next) else NextBlockLocked() } }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        armTick(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        armTick(context)
    }
}

class NextBlockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextBlockWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        armTick(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        armTick(context)
    }
}

/** Arms the refresh tick while any widget is placed, cancels it once none is. */
private fun armTick(context: Context) {
    val container = (context.applicationContext as SteadylineApp).container
    container.appScope.launch {
        if (AppWidgets.hasAny(context)) {
            AppWidgets.armTick(context, container.loadTodaySnapshot())
        } else {
            AppWidgets.cancelTick(context)
        }
    }
}

@Composable
private fun WidgetSurface(onClickToday: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var modifier = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(Radius.large)
        .padding(Spacing.md)
    if (onClickToday) modifier = modifier.clickable(actionStartActivity(AppWidgets.openTodayIntent(context)))
    Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun StatusContent(snapshot: TodaySnapshot) {
    val context = LocalContext.current
    WidgetSurface {
        if (!snapshot.hasPlan) {
            Text(
                text = context.getString(R.string.widget_no_plan),
                style = TextStyle(fontSize = FontSize.md, color = GlanceTheme.colors.onSurface),
            )
            return@WidgetSurface
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = snapshot.daysToExam?.toString() ?: context.getString(R.string.widget_no_value),
                style = TextStyle(fontSize = FontSize.title, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
            )
            Spacer(GlanceModifier.width(Spacing.xs))
            Text(
                text = context.getString(R.string.widget_days_to_exam),
                style = TextStyle(fontSize = FontSize.sm, color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
        Text(
            text = context.getString(R.string.widget_coverage, snapshot.coveragePercent),
            style = TextStyle(fontSize = FontSize.md, color = GlanceTheme.colors.onSurface),
        )
    }
}

/** Free tier: the medium widget is Pro (§11.2). Tapping opens the app on the paywall. */
@Composable
private fun NextBlockLocked() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(Radius.large)
            .padding(Spacing.md)
            .clickable(actionStartActivity(AppWidgets.openPaywallIntent(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_unlock_pro),
            style = TextStyle(fontSize = FontSize.lg, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
        )
        Text(
            text = context.getString(R.string.widget_unlock_pro_detail),
            style = TextStyle(fontSize = FontSize.sm, color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = 2,
        )
    }
}

@Composable
private fun NextBlockContent(hasPlan: Boolean, next: TodayBlock?) {
    val context = LocalContext.current
    WidgetSurface(onClickToday = next == null) {
        when {
            !hasPlan -> Text(
                text = context.getString(R.string.widget_no_plan),
                style = TextStyle(fontSize = FontSize.md, color = GlanceTheme.colors.onSurface),
            )
            next == null -> Text(
                text = context.getString(R.string.widget_nothing_left),
                style = TextStyle(fontSize = FontSize.md, color = GlanceTheme.colors.onSurface),
            )
            else -> Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxSize()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = context.getString(R.string.widget_next_at, minuteLabel(next.startMinuteOfDay)),
                        style = TextStyle(fontSize = FontSize.sm, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                    Text(
                        text = next.title.ifBlank { context.getString(R.string.focus_notif_default_title) },
                        style = TextStyle(fontSize = FontSize.lg, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                        maxLines = 2,
                    )
                    if (next.subjectName.isNotBlank()) {
                        Text(
                            text = if (next.isRevision) {
                                context.getString(R.string.focus_revision_title, next.subjectName)
                            } else {
                                next.subjectName
                            },
                            style = TextStyle(fontSize = FontSize.sm, color = GlanceTheme.colors.onSurfaceVariant),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(GlanceModifier.width(Spacing.sm))
                Button(
                    text = context.getString(R.string.widget_start),
                    onClick = actionStartActivity(AppWidgets.startBlockIntent(context, next.id)),
                )
            }
        }
    }
}

private fun minuteLabel(minuteOfDay: Int): String =
    LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
