package com.stuf.remotemouse.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.local.entity.ActionEntity
import com.stuf.remotemouse.data.local.repo.ActionRepository
import com.stuf.remotemouse.data.local.repo.DeviceRepository
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.data.remote.ServerEvent
import com.stuf.remotemouse.data.remote.dto.VolumeUpdateDto
import com.stuf.remotemouse.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ControlUiState(
    val mode: ControlMode = ControlMode.Mouse,
    val leftHeld: Boolean = false,
    val rightHeld: Boolean = false,
    val isFavorite: Boolean = false,
    val showDeviceMenu: Boolean = false,
    val showActionsSheet: Boolean = false,
    val volume: VolumeUpdateDto = VolumeUpdateDto(level = 50, muted = false),
    val favoriteBusy: Boolean = false,
    val favoriteError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ControlViewModel @Inject constructor(
    private val orchestrator: ConnectionOrchestrator,
    private val deviceRepository: DeviceRepository,
    private val actionRepository: ActionRepository,
    private val appSettings: AppSettings,
) : ViewModel() {
    val session = orchestrator.session

    private val _ui = MutableStateFlow(ControlUiState())
    val ui: StateFlow<ControlUiState> = _ui.asStateFlow()

    val actions: StateFlow<List<ActionEntity>> = session
        .map { it?.deviceId }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else actionRepository.observeActions(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sensitivity = appSettings.touchpadSensitivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.2f)

    init {
        viewModelScope.launch {
            combine(session, deviceRepository.observeDevices()) { active, devices ->
                active?.deviceId != null && devices.any { it.deviceId == active.deviceId }
            }.collect { favorite ->
                _ui.update { it.copy(isFavorite = favorite) }
            }
        }
        viewModelScope.launch {
            orchestrator.events.collect { event ->
                when (event) {
                    is ServerEvent.VolumeUpdate -> {
                        _ui.update { it.copy(volume = event.data) }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun selectMode(mode: ControlMode) {
        _ui.update { it.copy(mode = mode) }
    }

    fun setDeviceMenuVisible(visible: Boolean) {
        _ui.update { it.copy(showDeviceMenu = visible) }
    }

    fun setActionsSheetVisible(visible: Boolean) {
        _ui.update { it.copy(showActionsSheet = visible, showDeviceMenu = false) }
    }

    fun onMove(dx: Float, dy: Float) {
        val sens = sensitivity.value
        val ix = (dx * sens).toInt()
        val iy = (dy * sens).toInt()
        if (ix == 0 && iy == 0) return
        orchestrator.socket().mouseMove(ix, iy)
    }

    fun onScroll(deltaY: Float) {
        val dy = (-deltaY).toInt().coerceIn(-240, 240)
        if (dy == 0) return
        orchestrator.socket().mouseScroll(dy)
    }

    fun leftClick() = orchestrator.socket().mouseClick("left", "click")
    fun rightClick() = orchestrator.socket().mouseClick("right", "click")

    fun toggleLeftButton() {
        val held = _ui.value.leftHeld
        if (held) {
            orchestrator.socket().mouseClick("left", "up")
        } else {
            orchestrator.socket().mouseClick("left", "down")
        }
        _ui.update { it.copy(leftHeld = !held) }
    }

    fun toggleRightButton() {
        val held = _ui.value.rightHeld
        if (held) {
            orchestrator.socket().mouseClick("right", "up")
        } else {
            orchestrator.socket().mouseClick("right", "down")
        }
        _ui.update { it.copy(rightHeld = !held) }
    }

    fun setVolume(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _ui.update { it.copy(volume = it.volume.copy(level = clamped, muted = false)) }
        orchestrator.socket().volumeSet(clamped)
    }

    fun runAction(actionId: Long) {
        viewModelScope.launch {
            val withSteps = actionRepository.getActionWithSteps(actionId) ?: return@launch
            withSteps.steps.forEach { step ->
                val keys = actionRepository.parseVkCodes(step.stepStringCode)
                if (keys.isNotEmpty()) {
                    orchestrator.socket().keySequence(keys)
                }
            }
        }
    }

    fun toggleFavorite() {
        val active = session.value ?: return
        viewModelScope.launch {
            _ui.update { it.copy(favoriteBusy = true, favoriteError = null, showDeviceMenu = false) }
            try {
                if (_ui.value.isFavorite) {
                    deviceRepository.delete(active.deviceId)
                } else {
                    orchestrator.saveCurrentDevice()
                }
            } catch (e: Exception) {
                _ui.update { it.copy(favoriteError = e.message) }
            } finally {
                _ui.update { it.copy(favoriteBusy = false) }
            }
        }
    }

    fun disconnect() {
        releaseHeldButtons()
        orchestrator.disconnect()
    }

    private fun releaseHeldButtons() {
        if (_ui.value.leftHeld) {
            orchestrator.socket().mouseClick("left", "up")
        }
        if (_ui.value.rightHeld) {
            orchestrator.socket().mouseClick("right", "up")
        }
        _ui.update { it.copy(leftHeld = false, rightHeld = false) }
    }

    override fun onCleared() {
        releaseHeldButtons()
        super.onCleared()
    }
}
