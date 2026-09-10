package com.stuf.remotemouse.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.identity.IdentityRepository
import com.stuf.remotemouse.data.local.entity.PcDeviceEntity
import com.stuf.remotemouse.data.local.repo.DeviceRepository
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.data.remote.DiscoveryClient
import com.stuf.remotemouse.data.remote.ServerEvent
import com.stuf.remotemouse.data.remote.WsConnectionState
import com.stuf.remotemouse.data.remote.dto.DiscoveryResponseDto
import com.stuf.remotemouse.domain.PairingException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebugConnectUiState(
    val isSearching: Boolean = false,
    val discovered: List<DiscoveryResponseDto> = emptyList(),
    val savedDevices: List<PcDeviceEntity> = emptyList(),
    val selected: DiscoveryResponseDto? = null,
    val code: String = "",
    val isPairing: Boolean = false,
    val wsState: WsConnectionState = WsConnectionState.Disconnected,
    val sessionLabel: String? = null,
    /** Last measured WebSocket RTT from heartbeat → heartbeat_ack, in milliseconds. */
    val pingMs: Long? = null,
    val log: List<String> = emptyList(),
)

@HiltViewModel
class DebugConnectViewModel @Inject constructor(
    private val discoveryClient: DiscoveryClient,
    private val orchestrator: ConnectionOrchestrator,
    private val identityRepository: IdentityRepository,
    deviceRepository: DeviceRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(DebugConnectUiState())
    val ui: StateFlow<DebugConnectUiState> = _ui.asStateFlow()

    val savedDevices = deviceRepository.observeDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            identityRepository.ensureMe()
        }
        viewModelScope.launch {
            savedDevices.collect { list ->
                _ui.update { it.copy(savedDevices = list) }
            }
        }
        viewModelScope.launch {
            orchestrator.wsState.collect { state ->
                _ui.update {
                    it.copy(
                        wsState = state,
                        pingMs = if (state == WsConnectionState.Connected) it.pingMs else null,
                    )
                }
            }
        }
        viewModelScope.launch {
            orchestrator.session.collect { session ->
                _ui.update {
                    it.copy(
                        sessionLabel = session?.let { s -> "${s.deviceName} (${s.ip})" },
                    )
                }
            }
        }
        viewModelScope.launch {
            orchestrator.events.collect { event ->
                when (event) {
                    is ServerEvent.HeartbeatAck -> {
                        val rtt = event.timestamp?.let { sentAt ->
                            (System.currentTimeMillis() - sentAt).coerceAtLeast(0L)
                        }
                        if (rtt != null) {
                            _ui.update { it.copy(pingMs = rtt) }
                            appendLog("heartbeat_ack ping=${rtt}ms")
                        } else {
                            appendLog("heartbeat_ack (no timestamp)")
                        }
                    }
                    is ServerEvent.SaveSuccess -> appendLog("save-success: ${event.deviceName}")
                    is ServerEvent.Error -> appendLog("error: ${event.message}")
                    is ServerEvent.ContextChange -> appendLog("context: ${event.context}")
                    is ServerEvent.MediaUpdate -> appendLog("media_update")
                    is ServerEvent.VolumeUpdate -> appendLog("volume_update")
                    is ServerEvent.Raw -> appendLog("raw: ${event.type}")
                }
            }
        }
    }

    fun onCodeChange(value: String) {
        _ui.update { it.copy(code = value.filter { ch -> ch.isDigit() }.take(6)) }
    }

    fun selectDiscovered(device: DiscoveryResponseDto) {
        _ui.update { it.copy(selected = device) }
        appendLog("selected ${device.deviceName} @ ${device.ip}")
    }

    fun search() {
        viewModelScope.launch {
            _ui.update { it.copy(isSearching = true, discovered = emptyList()) }
            appendLog("UDP discovery…")
            try {
                val me = identityRepository.ensureMe()
                val found = discoveryClient.discover(me.clientId)
                _ui.update { it.copy(discovered = found, isSearching = false) }
                appendLog("found ${found.size} device(s)")
            } catch (e: Exception) {
                _ui.update { it.copy(isSearching = false) }
                appendLog("discovery error: ${e.message}")
            }
        }
    }

    fun pairWithCode(onSuccess: () -> Unit = {}) {
        val selected = _ui.value.selected
        val code = _ui.value.code
        if (selected == null) {
            appendLog("select a discovered PC first")
            return
        }
        if (code.length != 6) {
            appendLog("enter 6-digit code")
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isPairing = true) }
            try {
                orchestrator.pairNew(
                    deviceId = selected.deviceId,
                    deviceName = selected.deviceName,
                    ip = selected.ip,
                    port = selected.port,
                    verificationCode = code,
                )
                appendLog("paired + ws connecting")
                onSuccess()
            } catch (e: PairingException) {
                appendLog("pair failed: ${e.message}")
            } catch (e: Exception) {
                appendLog("pair failed: ${e.message}")
            } finally {
                _ui.update { it.copy(isPairing = false) }
            }
        }
    }

    fun pairSaved(deviceId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _ui.update { it.copy(isPairing = true) }
            try {
                orchestrator.pairSaved(deviceId)
                appendLog("saved pair ok")
                onSuccess()
            } catch (e: PairingException) {
                appendLog("saved pair failed: ${e.message}")
            } catch (e: Exception) {
                appendLog("saved pair failed: ${e.message}")
            } finally {
                _ui.update { it.copy(isPairing = false) }
            }
        }
    }

    fun sendHeartbeat() {
        orchestrator.socket().heartbeat()
        appendLog("heartbeat sent")
    }

    fun saveDevice() {
        viewModelScope.launch {
            try {
                orchestrator.saveCurrentDevice()
                appendLog("device saved locally")
            } catch (e: Exception) {
                appendLog("save failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        orchestrator.disconnect()
        _ui.update { it.copy(pingMs = null) }
        appendLog("disconnected")
    }

    private fun appendLog(line: String) {
        _ui.update { state ->
            state.copy(log = (listOf(line) + state.log).take(40))
        }
    }
}
