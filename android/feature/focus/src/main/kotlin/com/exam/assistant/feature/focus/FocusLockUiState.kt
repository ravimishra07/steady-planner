package com.exam.assistant.feature.focus

import com.exam.assistant.core.data.InstalledAppInfo
import com.exam.assistant.domain.FocusLockDisplayState

enum class FocusLockSetupStep { None, Explain, Permissions, AppPicker }

data class FocusLockUiState(
    val loading: Boolean = true,
    val display: FocusLockDisplayState = FocusLockDisplayState.Off,
    val setupStep: FocusLockSetupStep = FocusLockSetupStep.None,
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val appsLoading: Boolean = false,
    val appSearchQuery: String = "",
) {
    val requiredPermissionsGranted: Boolean get() = usageAccessGranted && overlayGranted
}
