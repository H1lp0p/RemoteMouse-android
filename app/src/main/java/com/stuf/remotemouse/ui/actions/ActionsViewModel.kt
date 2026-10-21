package com.stuf.remotemouse.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.local.repo.ActionRepository
import com.stuf.remotemouse.data.local.repo.DeviceRepository
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.data.settings.CursorSensitivity
import com.stuf.remotemouse.domain.VkCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActionEditItem(
    val id: Long,
    val name: String,
    val keyLabels: List<String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val actionRepository: ActionRepository,
    private val deviceRepository: DeviceRepository,
    private val orchestrator: ConnectionOrchestrator,
    private val cursorSensitivity: CursorSensitivity,
) : ViewModel() {
    val connected: StateFlow<Boolean> = orchestrator.session
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val deviceName: StateFlow<String> = orchestrator.session
        .map { it?.deviceName.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val deviceId: StateFlow<String?> = orchestrator.session
        .map { it?.deviceId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isSaved: StateFlow<Boolean> = deviceId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(false)
            } else {
                deviceRepository.observeDevices().map { list -> list.any { it.deviceId == id } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val sensitivity: StateFlow<Float> = cursorSensitivity.coefficient

    private val _saveBusy = MutableStateFlow(false)
    val saveBusy: StateFlow<Boolean> = _saveBusy.asStateFlow()

    val actions: StateFlow<List<ActionEditItem>> = deviceId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                actionRepository.observeActionsWithSteps(id).map { list ->
                    list.map { item ->
                        ActionEditItem(
                            id = item.action.id,
                            name = item.action.name,
                            keyLabels = item.steps.flatMap { step ->
                                VkCodes.labels(step.stepStringCode)
                            },
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            deviceId.collect { id ->
                if (id != null) {
                    actionRepository.ensureDefaultActions(id)
                }
            }
        }
    }

    fun deleteAction(actionId: Long) {
        viewModelScope.launch { actionRepository.deleteAction(actionId) }
    }

    fun setSensitivity(value: Float) {
        cursorSensitivity.setLocal(value)
    }

    suspend fun persistSensitivity() {
        if (!isSaved.value) return
        val id = deviceId.value ?: return
        deviceRepository.setCursorSensitivity(id, cursorSensitivity.coefficient.value)
    }

    fun toggleSaved() {
        val active = orchestrator.session.value ?: return
        val remove = isSaved.value
        viewModelScope.launch {
            _saveBusy.value = true
            try {
                if (remove) {
                    deviceRepository.delete(active.deviceId)
                } else {
                    orchestrator.saveCurrentDevice()
                }
            } catch (_: Exception) {
            } finally {
                _saveBusy.value = false
            }
        }
    }

    fun onMove(dx: Float, dy: Float) {
        val (sx, sy) = cursorSensitivity.scale(dx, dy)
        if (sx == 0f && sy == 0f) return
        orchestrator.socket().mouseMove(sx, sy)
    }

    fun onScroll(deltaY: Float) {
        val dy = (-deltaY).toInt().coerceIn(-240, 240)
        if (dy == 0) return
        orchestrator.socket().mouseScroll(deltaY = dy)
    }

    fun leftClick() = orchestrator.socket().mouseClick("left", "click")

    fun rightClick() = orchestrator.socket().mouseClick("right", "click")

    fun disconnect() {
        orchestrator.socket().mouseClick("left", "up")
        orchestrator.socket().mouseClick("right", "up")
        orchestrator.socket().setMouseMoveVelocity(0f, 0f)
        orchestrator.disconnect()
    }
}
