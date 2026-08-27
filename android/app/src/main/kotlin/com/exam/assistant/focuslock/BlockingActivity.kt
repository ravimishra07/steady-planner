package com.exam.assistant.focuslock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.exam.assistant.MainActivity
import com.exam.assistant.SteadylineApp
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.SteadylineTheme
import com.exam.assistant.core.design.ThemeChoice
import com.exam.assistant.domain.withTemporaryAllowance
import kotlinx.coroutines.launch

/**
 * Shown in place of a blocked app. Its only job is to send the student back to
 * studying, with a narrow, intentional escape hatch — never a dead end.
 */
class BlockingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        val topicTitle = intent.getStringExtra(EXTRA_TOPIC_TITLE).orEmpty()
        val remainingSec = intent.getIntExtra(EXTRA_REMAINING_SEC, 0)
        val blockedAppLabel = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(blockedPackage, 0)).toString()
        }.getOrDefault(blockedPackage)

        setContent {
            SteadylineTheme(choice = ThemeChoice.Dark, palette = AccentPalette.Default) {
                BlockingScreen(
                    blockedAppLabel = blockedAppLabel,
                    topicTitle = topicTitle,
                    remainingSec = remainingSec,
                    onBackToStudy = { returnToStudy() },
                    onAllowTemporarily = { allowTemporarily(blockedPackage) },
                )
            }
        }
    }

    private fun returnToStudy() {
        val homeIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
        finish()
    }

    private fun allowTemporarily(blockedPackage: String) {
        val container = (application as SteadylineApp).container
        lifecycleScope.launch {
            val current = container.focusLockStore.load()
            container.focusLockStore.save(
                withTemporaryAllowance(current, blockedPackage, System.currentTimeMillis()),
            )
            finish()
        }
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_TOPIC_TITLE = "topic_title"
        const val EXTRA_REMAINING_SEC = "remaining_sec"
    }
}

@Composable
private fun BlockingScreen(
    blockedAppLabel: String,
    topicTitle: String,
    remainingSec: Int,
    onBackToStudy: () -> Unit,
    onAllowTemporarily: () -> Unit,
) {
    val colors = AppTheme.colors
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Need access?") },
            text = { Text("This allows $blockedAppLabel for 5 minutes, then Focus Lock resumes.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onAllowTemporarily()
                }) { Text("Allow for 5 min") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "Focus Lock",
                style = MaterialTheme.typography.labelLarge,
                color = colors.brandSoft,
            )
            Text(
                text = "You're studying",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            if (topicTitle.isNotBlank()) {
                Text(
                    text = topicTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (remainingSec > 0) {
                Text(
                    text = "${remainingSec / 60} min left",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                )
            }
            Text(
                text = "$blockedAppLabel is paused during this study session.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.lg),
            )
            Button(
                onClick = onBackToStudy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
            ) {
                Text("Back to study")
            }
            TextButton(onClick = { showConfirm = true }) {
                Text("Need access?", color = colors.textMuted)
            }
        }
    }
}
