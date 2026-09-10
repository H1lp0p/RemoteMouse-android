package com.stuf.remotemouse.ui.connecting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.domain.PairingException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectingUiState {
    data object Connecting : ConnectingUiState
    data object Succeeded : ConnectingUiState
    data class Failed(val message: String) : ConnectingUiState
}

@HiltViewModel
class ConnectingViewModel @Inject constructor(
    private val orchestrator: ConnectionOrchestrator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mode: String = checkNotNull(savedStateHandle["mode"])
    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])
    val deviceName: String = checkNotNull(savedStateHandle["deviceName"])
    private val ip: String = savedStateHandle["ip"] ?: ""
    private val port: Int = savedStateHandle["port"] ?: 0
    private val code: String = savedStateHandle["code"] ?: ""

    private val _ui = MutableStateFlow<ConnectingUiState>(ConnectingUiState.Connecting)
    val ui: StateFlow<ConnectingUiState> = _ui.asStateFlow()

    init {
        connect()
    }

    fun retry() {
        _ui.value = ConnectingUiState.Connecting
        connect()
    }

    private fun connect() {
        viewModelScope.launch {
            try {
                when (mode) {
                    MODE_SAVED -> orchestrator.pairSaved(deviceId)
                    MODE_NEW -> orchestrator.pairNew(
                        deviceId = deviceId,
                        deviceName = deviceName,
                        ip = ip,
                        port = port,
                        verificationCode = code,
                    )
                    else -> error("Unknown connecting mode: $mode")
                }
                _ui.value = ConnectingUiState.Succeeded
            } catch (e: PairingException) {
                _ui.value = ConnectingUiState.Failed(e.message ?: "Connection failed")
            } catch (e: Exception) {
                _ui.value = ConnectingUiState.Failed(e.message ?: "Connection failed")
            }
        }
    }

    companion object {
        const val MODE_SAVED = "saved"
        const val MODE_NEW = "new"
    }
}
