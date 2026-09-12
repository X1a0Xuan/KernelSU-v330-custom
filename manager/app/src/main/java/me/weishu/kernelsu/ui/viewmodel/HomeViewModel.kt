package me.weishu.kernelsu.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.system.Os
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.getKernelVersion
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.screen.home.HomeUiState
import me.weishu.kernelsu.ui.screen.home.SystemInfo
import me.weishu.kernelsu.ui.screen.home.getManagerVersion
import me.weishu.kernelsu.ui.util.checkNewVersion
import me.weishu.kernelsu.ui.util.getSELinuxStatusRaw
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import me.weishu.kernelsu.ui.util.resolveDeviceName
import me.weishu.kernelsu.ui.util.rootAvailable

class HomeViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    private val prefs by lazy {
        ksuApp.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Keep the Home item visibility in sync while it is toggled in the theme settings screen. */
    private val homeItemsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in homeItemKeys) {
            _uiState.update {
                it.copy(
                    homeShowManagerVersion = settingsRepo.homeShowManagerVersion,
                    homeShowKernelVersion = settingsRepo.homeShowKernelVersion,
                    homeShowDeviceModel = settingsRepo.homeShowDeviceModel,
                    homeShowFingerprint = settingsRepo.homeShowFingerprint,
                    homeShowSelinuxStatus = settingsRepo.homeShowSelinuxStatus,
                    homeShowSeccompStatus = settingsRepo.homeShowSeccompStatus,
                    homeShowSupportUs = settingsRepo.homeShowSupportUs,
                    homeShowLearnKernelsu = settingsRepo.homeShowLearnKernelsu,
                )
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(homeItemsListener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(homeItemsListener)
    }

    fun refresh() {
        viewModelScope.launch {
            val baseState = withContext(Dispatchers.IO) { buildState() }
            _uiState.update { baseState }
            if (baseState.checkUpdateEnabled) {
                val latestVersionInfo = withContext(Dispatchers.IO) { checkNewVersion() }
                _uiState.update { it.copy(latestVersionInfo = latestVersionInfo) }
            }
        }
    }

    private fun buildState(): HomeUiState {
        val kernelVersion = getKernelVersion()
        val isManager = Natives.isManager
        val ksuVersion = if (isManager) Natives.version else null
        val kernelUAPIVersion = if (isManager) Natives.kernelUAPIVersion else null
        val managerUAPIVersion = Natives.managerUAPIVersion
        val lkmMode = ksuVersion?.let { if (kernelVersion.isGKI()) Natives.isLkmMode else null }
        val isRootAvailable = rootAvailable()
        val managerVersion = getManagerVersion(ksuApp)

        return HomeUiState(
            kernelVersion = kernelVersion,
            ksuVersion = ksuVersion,
            lkmMode = lkmMode,
            isManager = isManager,
            isManagerPrBuild = BuildConfig.IS_PR_BUILD,
            isKernelPrBuild = Natives.isPrBuild,
            requiresNewKernel = isManager && Natives.requireNewKernel(),
            uapiMismatch = isManager && Natives.checkUAPIMismatch(),
            kernelUAPIVersion = kernelUAPIVersion,
            managerUAPIVersion = managerUAPIVersion,
            isRootAvailable = isRootAvailable,
            isSafeMode = Natives.isSafeMode,
            isLateLoadMode = Natives.isLateLoadMode,
            checkUpdateEnabled = settingsRepo.checkUpdate,
            latestVersionInfo = LatestVersionInfo(),
            currentManagerVersionCode = managerVersion.versionCode,
            systemInfo = SystemInfo(
                kernelVersion = Os.uname().release,
                managerVersion = "${managerVersion.versionName} (${managerVersion.versionCode}-${managerUAPIVersion})",
                deviceModel = resolveDeviceName(),
                fingerprint = Build.FINGERPRINT,
                selinuxStatus = getSELinuxStatusRaw(),
                seccompStatus = runCatching {
                    Os.prctl(21 /* PR_GET_SECCOMP */, 0, 0, 0, 0)
                }.getOrDefault(-1),
            ),
            homeShowManagerVersion = settingsRepo.homeShowManagerVersion,
            homeShowKernelVersion = settingsRepo.homeShowKernelVersion,
            homeShowDeviceModel = settingsRepo.homeShowDeviceModel,
            homeShowFingerprint = settingsRepo.homeShowFingerprint,
            homeShowSelinuxStatus = settingsRepo.homeShowSelinuxStatus,
            homeShowSeccompStatus = settingsRepo.homeShowSeccompStatus,
            homeShowSupportUs = settingsRepo.homeShowSupportUs,
            homeShowLearnKernelsu = settingsRepo.homeShowLearnKernelsu,
        )
    }

    private companion object {
        const val SETTINGS_PREFS = "settings"

        val homeItemKeys = setOf(
            "home_show_manager_version",
            "home_show_kernel_version",
            "home_show_device_model",
            "home_show_fingerprint",
            "home_show_selinux_status",
            "home_show_seccomp_status",
            "home_show_support_us",
            "home_show_learn_kernelsu",
        )
    }
}
