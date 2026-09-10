package com.stuf.remotemouse.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.local.repo.ActionRepository
import com.stuf.remotemouse.data.local.repo.DeviceRepository
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.data.remote.ServerEvent
import com.stuf.remotemouse.data.remote.dto.MediaUpdateDto
import com.stuf.remotemouse.data.remote.dto.VolumeUpdateDto
import com.stuf.remotemouse.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ControlUiState(
    val mode: ControlMode = ControlMode.Mouse,
    val leftHeld: Boolean = false,
    val rightHeld: Boolean = false,
    val isFavorite: Boolean = false,
    val showDeviceMenu: Boolean = false,
    val showActionsSheet: Boolean = false,
    val volume: VolumeUpdateDto = VolumeUpdateDto(level = 50, muted = false),
    val media: MediaUpdateDto? = null,
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

    val actions: StateFlow<List<ActionListItem>> = session
        .map { it?.deviceId }
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                actionRepository.observeActions(id).mapLatest { list ->
                    list.map { action ->
                        val steps = actionRepository.getActionWithSteps(action.id)?.steps.orEmpty()
                        ActionListItem(
                            id = action.id,
                            name = action.name,
                            subtitle = steps.joinToString(" + ") { step ->
                                step.name.ifBlank { step.stepStringCode }
                            },
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sensitivity = appSettings.touchpadSensitivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.2f)

    @Volatile private var stickOx = 0f
    @Volatile private var stickOy = 0f
    private var stickJob: Job? = null
    private var mediaProgressJob: Job? = null
    private var mediaSubscribed = false

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
                    is ServerEvent.MediaUpdate -> {
                        _ui.update { it.copy(media = event.data) }
                        syncMediaProgressTicker()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun selectMode(mode: ControlMode) {
        if (mode != ControlMode.Stick) {
            onStickReleased()
        }
        val previous = _ui.value.mode
        _ui.update { it.copy(mode = mode) }
        if (mode == ControlMode.Player && previous != ControlMode.Player) {
            subscribeMedia()
        } else if (previous == ControlMode.Player && mode != ControlMode.Player) {
            unsubscribeMedia()
        }
    }

    private fun subscribeMedia() {
        if (mediaSubscribed) return
        mediaSubscribed = true
        orchestrator.socket().mediaSubscribe()
        syncMediaProgressTicker()
    }

    private fun unsubscribeMedia() {
        if (!mediaSubscribed) return
        mediaSubscribed = false
        mediaProgressJob?.cancel()
        mediaProgressJob = null
        orchestrator.socket().mediaUnsubscribe()
    }

    /** Advances position locally while Player is open — no 1s media_update from server. */
    private fun syncMediaProgressTicker() {
        mediaProgressJob?.cancel()
        mediaProgressJob = null
        if (!mediaSubscribed || _ui.value.mode != ControlMode.Player) return
        val media = _ui.value.media ?: return
        if (!media.isPlaying) return
        mediaProgressJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val current = _ui.value.media ?: break
                if (!current.isPlaying) break
                val duration = current.durationSeconds.coerceAtLeast(0)
                if (current.positionSeconds >= duration) {
                    _ui.update {
                        it.copy(media = current.copy(isPlaying = false, positionSeconds = duration))
                    }
                    break
                }
                _ui.update {
                    it.copy(media = current.copy(positionSeconds = current.positionSeconds + 1))
                }
            }
            mediaProgressJob = null
        }
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
        onScroll(deltaX = 0f, deltaY = deltaY)
    }

    fun onScroll(deltaX: Float, deltaY: Float) {
        val dx = deltaX.toInt().coerceIn(-240, 240)
        val dy = (-deltaY).toInt().coerceIn(-240, 240)
        if (dx == 0 && dy == 0) return
        orchestrator.socket().mouseScroll(deltaY = dy, deltaX = dx)
    }

    /**
     * Stick move: cursor velocity comes from offset vs pad center, not finger deltas.
     */
    fun onStickDeflection(ox: Float, oy: Float) {
        stickOx = ox
        stickOy = oy
        ensureStickLoop()
    }

    fun onStickReleased() {
        stickOx = 0f
        stickOy = 0f
        stickJob?.cancel()
        stickJob = null
        orchestrator.socket().setMouseMoveVelocity(0, 0)
    }

    private fun ensureStickLoop() {
        if (stickJob?.isActive == true) return
        stickJob = viewModelScope.launch {
            while (isActive) {
                val ox = stickOx
                val oy = stickOy
                if (ox == 0f && oy == 0f) break
                val sens = sensitivity.value
                // Full deflection (~120–150px) → ~15–25px cursor / 16ms tick
                val scale = 0.14f * sens
                val ix = (ox * scale).toInt()
                val iy = (oy * scale).toInt()
                orchestrator.socket().setMouseMoveVelocity(ix, iy)
                delay(16)
            }
            orchestrator.socket().setMouseMoveVelocity(0, 0)
            stickJob = null
        }
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

    fun mediaPlayPause() {
        orchestrator.socket().mediaPlayPause()
        val current = _ui.value.media ?: return
        _ui.update { it.copy(media = current.copy(isPlaying = !current.isPlaying)) }
        syncMediaProgressTicker()
    }

    fun mediaPrev() = orchestrator.socket().mediaPrev()

    fun mediaNext() = orchestrator.socket().mediaNext()

    fun mediaSeekRelative(seconds: Int) {
        if (seconds == 0) return
        orchestrator.socket().mediaSeek(seconds)
        val current = _ui.value.media ?: return
        val duration = current.durationSeconds.coerceAtLeast(0)
        val nextPos = (current.positionSeconds + seconds).coerceIn(0, duration)
        _ui.update { it.copy(media = current.copy(positionSeconds = nextPos)) }
    }

    fun mediaSeekAbsolute(positionSeconds: Int) {
        val current = _ui.value.media
        val duration = current?.durationSeconds?.coerceAtLeast(0) ?: 0
        val target = positionSeconds.coerceIn(0, duration.coerceAtLeast(0))
        val delta = target - (current?.positionSeconds ?: 0)
        if (delta != 0) {
            orchestrator.socket().mediaSeek(delta)
        }
        if (current != null) {
            _ui.update { it.copy(media = current.copy(positionSeconds = target)) }
        }
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
        unsubscribeMedia()
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
        unsubscribeMedia()
        onStickReleased()
        releaseHeldButtons()
        super.onCleared()
    }
}
