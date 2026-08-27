package com.exam.assistant.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.FocusLockCapabilityChecker
import com.exam.assistant.core.data.FocusLockStore
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.InstalledAppProvider
import com.exam.assistant.domain.ActiveStudySessionInfo
import com.exam.assistant.domain.FocusLockCapabilities
import com.exam.assistant.domain.FocusLockSettings
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.domain.clearExpiredAllowances
import com.exam.assistant.domain.focusLockDisplayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FocusLockViewModel(
    private val focusLockStore: FocusLockStore,
    private val capabilityChecker: FocusLockCapabilityChecker,
    private val installedAppProvider: InstalledAppProvider,
    private val focusStore: FocusStore,
) : ViewModel() {

    private val _state = MutableStateFlow(FocusLockUiState())
    val state: StateFlow<FocusLockUiState> = _state.asStateFlow()

    private var settings: FocusLockSettings = FocusLockSettings()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            settings = clearExpiredAllowances(focusLockStore.load(), System.currentTimeMillis())
            focusLockStore.save(settings)
            recompute()
        }
    }

    /** Call when returning from a Settings screen or after a runtime permission result. */
    fun refreshCapabilities() {
        viewModelScope.launch { recompute() }
    }

    private suspend fun recompute() {
        val capabilities = capabilityChecker.current()
        val session = focusStore.load().withClockNow()
        val activeSession = if (session.status == FocusStatus.RUNNING || session.status == FocusStatus.PAUSED) {
            ActiveStudySessionInfo(session.block?.title.orEmpty(), session.remainingSec)
        } else {
            null
        }
        _state.update {
            it.copy(
                loading = false,
                display = focusLockDisplayState(settings, capabilities, activeSession),
                usageAccessGranted = capabilities.usageAccessGranted,
                overlayGranted = capabilities.overlayGranted,
                notificationsGranted = capabilityChecker.notificationsGranted(),
                selectedPackages = settings.blockedPackages,
            )
        }
    }

    /** The OFF -> ON toggle. Turning off is immediate; turning on (first time) opens setup. */
    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                settings = settings.copy(enabled = false)
                focusLockStore.save(settings)
                recompute()
            }
            return
        }
        if (settings.configured) {
            viewModelScope.launch {
                settings = settings.copy(enabled = true)
                focusLockStore.save(settings)
                recompute()
            }
        } else {
            _state.update { it.copy(setupStep = FocusLockSetupStep.Explain) }
        }
    }

    fun startSetup() {
        _state.update { it.copy(setupStep = FocusLockSetupStep.Explain) }
    }

    fun dismissSetup() {
        _state.update { it.copy(setupStep = FocusLockSetupStep.None) }
    }

    fun advanceToPermissions() {
        viewModelScope.launch { recompute() }
        _state.update { it.copy(setupStep = FocusLockSetupStep.Permissions) }
    }

    fun advanceToAppPicker() {
        _state.update { it.copy(setupStep = FocusLockSetupStep.AppPicker, appsLoading = true) }
        viewModelScope.launch {
            val apps = installedAppProvider.launchableApps()
            _state.update { it.copy(installedApps = apps, appsLoading = false) }
        }
    }

    fun openManageApps() {
        _state.update { it.copy(setupStep = FocusLockSetupStep.AppPicker, appsLoading = true, selectedPackages = settings.blockedPackages) }
        viewModelScope.launch {
            val apps = installedAppProvider.launchableApps()
            _state.update { it.copy(installedApps = apps, appsLoading = false) }
        }
    }

    fun toggleAppSelected(packageName: String) {
        _state.update {
            val next = if (packageName in it.selectedPackages) {
                it.selectedPackages - packageName
            } else {
                it.selectedPackages + packageName
            }
            it.copy(selectedPackages = next)
        }
    }

    fun selectAllApps() {
        _state.update { it.copy(selectedPackages = it.installedApps.map { app -> app.packageName }.toSet()) }
    }

    fun clearAppSelection() {
        _state.update { it.copy(selectedPackages = emptySet()) }
    }

    /** Saves the picked apps and — since this is reached only via the enable flow or Manage apps — turns Focus Lock on. */
    fun saveAndEnable() {
        viewModelScope.launch {
            val selected = _state.value.selectedPackages
            settings = settings.copy(enabled = true, blockedPackages = selected)
            focusLockStore.save(settings)
            _state.update { it.copy(setupStep = FocusLockSetupStep.None) }
            recompute()
        }
    }

    class Factory(
        private val focusLockStore: FocusLockStore,
        private val capabilityChecker: FocusLockCapabilityChecker,
        private val installedAppProvider: InstalledAppProvider,
        private val focusStore: FocusStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FocusLockViewModel(focusLockStore, capabilityChecker, installedAppProvider, focusStore) as T
    }
}
