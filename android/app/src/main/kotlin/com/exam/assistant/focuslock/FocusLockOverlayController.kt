package com.exam.assistant.focuslock

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.exam.assistant.R
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.SteadylineTheme

/**
 * Owns the actual system overlay used by Focus Lock.
 *
 * A foreground service cannot reliably bring an Activity over another app on modern
 * Android because background Activity launches are restricted. This controller uses
 * the overlay capability the student explicitly granted instead.
 */
internal class FocusLockOverlayController(
    private val context: Context,
    private val onBackToStudy: () -> Unit,
    private val onAllowTemporarily: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val lifecycleOwner = OverlayLifecycleOwner()
    private var overlayView: ComposeView? = null
    private var content by mutableStateOf(OverlayContent())

    fun show(blockedPackage: String, topicTitle: String, remainingSec: Int) {
        if (!Settings.canDrawOverlays(context)) return
        runOnMain {
            content = OverlayContent(
                blockedPackage = blockedPackage,
                blockedAppLabel = resolveAppLabel(blockedPackage),
                topicTitle = topicTitle,
                remainingSec = remainingSec,
            )
            if (overlayView == null) addOverlay()
        }
    }

    fun hide() {
        runOnMain { removeOverlay() }
    }

    fun destroy() {
        runOnMain {
            removeOverlay()
            lifecycleOwner.destroy()
        }
    }

    private fun addOverlay() {
        if (overlayView != null) return
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SteadylineTheme(
                    background = BackgroundAppearance.Dark,
                    palette = AccentPalette.Default,
                ) {
                    val current = content
                    FocusLockOverlayScreen(
                        blockedAppLabel = current.blockedAppLabel,
                        topicTitle = current.topicTitle,
                        remainingSec = current.remainingSec,
                        onBackToStudy = {
                            removeOverlay()
                            onBackToStudy()
                        },
                        onAllowTemporarily = {
                            val blockedPackage = content.blockedPackage
                            removeOverlay()
                            onAllowTemporarily(blockedPackage)
                        },
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.FILL
        }
        runCatching {
            windowManager.addView(view, params)
            overlayView = view
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun resolveAppLabel(blockedPackage: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(blockedPackage, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(blockedPackage)

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    @Suppress("DEPRECATION")
    private fun overlayWindowType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        WindowManager.LayoutParams.TYPE_PHONE
    }
}

private data class OverlayContent(
    val blockedPackage: String = "",
    val blockedAppLabel: String = "",
    val topicTitle: String = "",
    val remainingSec: Int = 0,
)

@Composable
private fun FocusLockOverlayScreen(
    blockedAppLabel: String,
    topicTitle: String,
    remainingSec: Int,
    onBackToStudy: () -> Unit,
    onAllowTemporarily: () -> Unit,
) {
    val colors = AppTheme.colors
    var showConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .safeDrawingPadding()
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.focus_lock_blocking_label),
                style = MaterialTheme.typography.labelLarge,
                color = colors.brandSoft,
            )
            Text(
                text = stringResource(R.string.focus_lock_blocking_headline),
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
                    text = stringResource(R.string.focus_lock_blocking_min_left, remainingSec / 60),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                )
            }
            Text(
                text = stringResource(R.string.focus_lock_blocking_app_paused, blockedAppLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.lg),
            )
            Button(
                onClick = onBackToStudy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandDeep,
                    contentColor = colors.onBrand,
                ),
            ) {
                Text(stringResource(R.string.focus_lock_blocking_back_to_study))
            }
            TextButton(onClick = { showConfirm = true }) {
                Text(stringResource(R.string.focus_lock_blocking_need_access), color = colors.textMuted)
            }
        }

        if (showConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = 0.65f))
                    .clickable { showConfirm = false },
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                color = colors.elevated,
                contentColor = colors.text,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.focus_lock_blocking_need_access),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.focus_lock_blocking_need_access_body, blockedAppLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showConfirm = false }) {
                            Text(stringResource(R.string.focus_lock_blocking_cancel))
                        }
                        TextButton(onClick = {
                            showConfirm = false
                            onAllowTemporarily()
                        }) {
                            Text(stringResource(R.string.focus_lock_blocking_allow))
                        }
                    }
                }
            }
        }
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
