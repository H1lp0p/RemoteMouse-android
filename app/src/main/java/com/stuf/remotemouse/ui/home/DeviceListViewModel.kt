package com.stuf.remotemouse.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.identity.IdentityRepository
import com.stuf.remotemouse.data.local.entity.PcDeviceEntity
import com.stuf.remotemouse.data.local.repo.DeviceRepository
import com.stuf.remotemouse.data.remote.DiscoveryClient
import com.stuf.remotemouse.data.remote.dto.DiscoveryResponseDto
import com.stuf.remotemouse.ui.connecting.ConnectingArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeviceSheetStep {
    Overview,
    EnterCode,
}

data class SelectedDevice(
    val deviceId: String,
    val name: String,
    val ip: String,
    val port: Int,
    val isSaved: Boolean,
    val isReachable: Boolean = true,
)

data class DeviceListUiState(
    val favoritesExpanded: Boolean = true,
    val networkExpanded: Boolean = true,
    val isSearching: Boolean = false,
    val discovered: List<DiscoveryResponseDto> = emptyList(),
    /** Device ids seen in the last UDP discovery (including favorites). */
    val reachableDeviceIds: Set<String> = emptySet(),
    val selection: SelectedDevice? = null,
    val sheetStep: DeviceSheetStep = DeviceSheetStep.Overview,
    val verificationCode: String = "",
    val sheetError: String? = null,
)

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val identityRepository: IdentityRepository,
    private val discoveryClient: DiscoveryClient,
) : ViewModel() {
    private val _ui = MutableStateFlow(DeviceListUiState())
    val ui: StateFlow<DeviceListUiState> = _ui.asStateFlow()

    val favorites: StateFlow<List<PcDeviceEntity>> = deviceRepository.observeDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { identityRepository.ensureMe() }
        refreshNetwork()
    }

    fun toggleFavorites() {
        _ui.update { it.copy(favoritesExpanded = !it.favoritesExpanded) }
    }

    fun toggleNetwork() {
        _ui.update { it.copy(networkExpanded = !it.networkExpanded) }
    }

    fun refreshNetwork() {
        viewModelScope.launch {
            _ui.update { it.copy(isSearching = true) }
            try {
                val me = identityRepository.ensureMe()
                val found = discoveryClient.discover(me.clientId)
                val favoriteIds = favorites.value.map { it.deviceId }.toSet()
                _ui.update {
                    it.copy(
                        isSearching = false,
                        discovered = found.filterNot { d -> d.deviceId in favoriteIds },
                        reachableDeviceIds = found.map { it.deviceId }.toSet(),
                    )
                }
            } catch (_: Exception) {
                _ui.update {
                    it.copy(
                        isSearching = false,
                        discovered = emptyList(),
                        reachableDeviceIds = emptySet(),
                    )
                }
            }
        }
    }

    fun selectFavorite(deviceId: String) {
        val saved = favorites.value.firstOrNull { it.deviceId == deviceId } ?: return
        openSheet(
            SelectedDevice(
                deviceId = saved.deviceId,
                name = saved.name,
                ip = saved.lastIp,
                port = saved.port,
                isSaved = true,
                isReachable = deviceId in _ui.value.reachableDeviceIds,
            ),
        )
    }

    fun selectDiscovered(device: DiscoveryResponseDto) {
        openSheet(
            SelectedDevice(
                deviceId = device.deviceId,
                name = device.deviceName,
                ip = device.ip,
                port = device.port,
                isSaved = false,
                isReachable = true,
            ),
        )
    }

    private fun openSheet(selection: SelectedDevice) {
        _ui.update {
            it.copy(
                selection = selection,
                sheetStep = DeviceSheetStep.Overview,
                verificationCode = "",
                sheetError = null,
            )
        }
    }

    fun dismissSheet() {
        _ui.update {
            it.copy(
                selection = null,
                sheetStep = DeviceSheetStep.Overview,
                verificationCode = "",
                sheetError = null,
            )
        }
    }

    fun onCodeChange(value: String) {
        _ui.update {
            it.copy(
                verificationCode = value.filter { ch -> ch.isDigit() }.take(6),
                sheetError = null,
            )
        }
    }

    fun onConnectClick(onStartConnecting: (ConnectingArgs) -> Unit) {
        val selection = _ui.value.selection ?: return
        when (_ui.value.sheetStep) {
            DeviceSheetStep.Overview -> {
                if (selection.isSaved) {
                    val args = ConnectingArgs.Saved(
                        deviceId = selection.deviceId,
                        deviceName = selection.name,
                    )
                    dismissSheet()
                    onStartConnecting(args)
                } else {
                    _ui.update {
                        it.copy(
                            sheetStep = DeviceSheetStep.EnterCode,
                            verificationCode = "",
                            sheetError = null,
                        )
                    }
                }
            }
            DeviceSheetStep.EnterCode -> {
                val code = _ui.value.verificationCode
                if (code.length != 6) {
                    _ui.update { it.copy(sheetError = "Enter 6-digit code") }
                    return
                }
                val args = ConnectingArgs.New(
                    deviceId = selection.deviceId,
                    deviceName = selection.name,
                    ip = selection.ip,
                    port = selection.port,
                    verificationCode = code,
                )
                dismissSheet()
                onStartConnecting(args)
            }
        }
    }
}
