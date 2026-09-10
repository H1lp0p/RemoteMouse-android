package com.stuf.remotemouse.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.identity.IdentityRepository
import com.stuf.remotemouse.data.local.entity.MeEntity
import com.stuf.remotemouse.data.local.entity.PcDeviceEntity
import com.stuf.remotemouse.data.local.repo.DeviceRepository
import com.stuf.remotemouse.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val deviceRepository: DeviceRepository,
    private val appSettings: AppSettings,
) : ViewModel() {
    val me: StateFlow<MeEntity?> = identityRepository.observeMe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val devices: StateFlow<List<PcDeviceEntity>> = deviceRepository.observeDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sensitivity: StateFlow<Float> = appSettings.touchpadSensitivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.2f)

    init {
        viewModelScope.launch { identityRepository.ensureMe() }
    }

    fun updateClientName(name: String) {
        viewModelScope.launch { identityRepository.updateClientName(name) }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch { deviceRepository.delete(deviceId) }
    }

    fun setSensitivity(value: Float) {
        appSettings.setTouchpadSensitivity(value)
    }
}
