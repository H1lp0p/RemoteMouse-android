package com.stuf.remotemouse.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.local.repo.ActionRepository
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.data.remote.ServerEvent
import com.stuf.remotemouse.data.remote.dto.MediaUpdateDto
import com.stuf.remotemouse.data.remote.dto.VolumeUpdateDto
import com.stuf.remotemouse.data.settings.CursorSensitivity
import com.stuf.remotemouse.domain.VkCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val showActionsSheet: Boolean = false,
    val volume: VolumeUpdateDto = VolumeUpdateDto(level = 50, muted = false),
    val media: MediaUpdateDto? = null,
    val sharedText: String = "",
    val pingMs: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ControlViewModel @Inject constructor(
    private val orchestrator: ConnectionOrchestrator,
    private val actionRepository: ActionRepository,
    private val cursorSensitivity: CursorSensitivity,
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
                            subtitle = steps.joinToString(", ") { step ->
                                VkCodes.formatSequence(step.stepStringCode)
                            },
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sensitivity = cursorSensitivity.coefficient

    @Volatile private var stickOx = 0f
    @Volatile private var stickOy = 0f
    @Volatile private var stickScrollMode = false
    private var stickJob: Job? = null
    private var mediaProgressJob: Job? = null
    private var mediaSubscribed = false
    private var textSubscribed = false
    private var textSyncJob: Job? = null
    private var lastSentText: String? = null

    init {
        viewModelScope.launch {
            session.collect { active ->
                cursorSensitivity.attach(active?.deviceId)
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
                    is ServerEvent.TextUpdate -> {
                        if (event.text != _ui.value.sharedText && event.text != lastSentText) {
                            _ui.update { it.copy(sharedText = event.text) }
                        }
                    }
                    is ServerEvent.HeartbeatAck -> {
                        val rtt = event.timestamp?.let { sentAt ->
                            (System.currentTimeMillis() - sentAt).coerceAtLeast(0L)
                        }
                        if (rtt != null) {
                            _ui.update { it.copy(pingMs = rtt) }
                        }
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
        if (mode == ControlMode.Text && previous != ControlMode.Text) {
            subscribeText()
        } else if (previous == ControlMode.Text && mode != ControlMode.Text) {
            unsubscribeText()
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

    private fun subscribeText() {
        if (textSubscribed) return
        textSubscribed = true
        orchestrator.socket().textSubscribe()
    }

    private fun unsubscribeText() {
        if (!textSubscribed) return
        textSubscribed = false
        textSyncJob?.cancel()
        textSyncJob = null
        flushSharedText()
        orchestrator.socket().textUnsubscribe()
    }

    fun onSharedTextChange(text: String) {
        _ui.update { it.copy(sharedText = text) }
        textSyncJob?.cancel()
        textSyncJob = viewModelScope.launch {
            delay(120)
            lastSentText = text
            orchestrator.socket().textSet(text)
        }
    }

    private fun flushSharedText() {
        textSyncJob?.cancel()
        textSyncJob = null
        val text = _ui.value.sharedText
        if (lastSentText != text) {
            lastSentText = text
            orchestrator.socket().textSet(text)
        }
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

    fun setActionsSheetVisible(visible: Boolean) {
        _ui.update { it.copy(showActionsSheet = visible) }
    }

    fun onMove(dx: Float, dy: Float) {
        val (sx, sy) = cursorSensitivity.scale(dx, dy)
        if (sx == 0f && sy == 0f) return
        orchestrator.socket().mouseMove(sx, sy)
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
        stickScrollMode = false
        ensureStickLoop()
    }

    /**
     * Stick scroll: continuous scroll while deflected (same loop idea as move).
     */
    fun onScrollDeflection(ox: Float, oy: Float) {
        stickOx = ox
        stickOy = oy
        stickScrollMode = true
        ensureStickLoop()
    }

    fun onStickReleased() {
        stickOx = 0f
        stickOy = 0f
        stickScrollMode = false
        stickJob?.cancel()
        stickJob = null
        orchestrator.socket().setMouseMoveVelocity(0f, 0f)
    }

    private fun ensureStickLoop() {
        if (stickJob?.isActive == true) return
        stickJob = viewModelScope.launch {
            while (isActive) {
                val ox = stickOx
                val oy = stickOy
                val scroll = stickScrollMode
                if (ox == 0f && oy == 0f) break
                if (scroll) {
                    orchestrator.socket().setMouseMoveVelocity(0f, 0f)
                    // Full deflection → steady scroll ticks (~same cadence as move).
                    val scale = 0.12f
                    val dx = (ox * scale).toInt().coerceIn(-240, 240)
                    val dy = (-oy * scale).toInt().coerceIn(-240, 240)
                    if (dx != 0 || dy != 0) {
                        orchestrator.socket().mouseScroll(deltaY = dy, deltaX = dx)
                    }
                } else {
                    // Full deflection (~120–150px) → ~15–25px cursor / 16ms tick.
                    // This tick is the integrator; each whole-pixel step is one UDP move.
                    val scale = 0.14f * sensitivity.value
                    orchestrator.socket().setMouseMoveVelocity(ox * scale, oy * scale)
                }
                delay(16)
            }
            orchestrator.socket().setMouseMoveVelocity(0f, 0f)
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
        var target = positionSeconds.coerceIn(0, 0xFFFF)
        if (duration > 0) {
            target = target.coerceAtMost(duration)
        }
        orchestrator.socket().seekAbsolute(target)
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

    fun ensureConnection() {
        orchestrator.ensureSocketConnected()
    }

    fun disconnect() {
        unsubscribeMedia()
        unsubscribeText()
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
        unsubscribeText()
        onStickReleased()
        releaseHeldButtons()
        super.onCleared()
    }
}
