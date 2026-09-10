package com.stuf.remotemouse.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.data.remote.ServerEvent
import com.stuf.remotemouse.data.remote.dto.MediaUpdateDto
import com.stuf.remotemouse.data.remote.dto.VolumeUpdateDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaUiState(
    val media: MediaUpdateDto? = null,
    val volume: VolumeUpdateDto = VolumeUpdateDto(level = 50, muted = false),
)

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val orchestrator: ConnectionOrchestrator,
) : ViewModel() {
    private val _ui = MutableStateFlow(MediaUiState())
    val ui: StateFlow<MediaUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            orchestrator.events.collect { event ->
                when (event) {
                    is ServerEvent.MediaUpdate ->
                        _ui.value = _ui.value.copy(media = event.data)
                    is ServerEvent.VolumeUpdate ->
                        _ui.value = _ui.value.copy(volume = event.data)
                    else -> Unit
                }
            }
        }
    }

    fun playPause() = orchestrator.socket().mediaPlayPause()
    fun next() = orchestrator.socket().mediaNext()
    fun prev() = orchestrator.socket().mediaPrev()
    fun seek(seconds: Int) = orchestrator.socket().mediaSeek(seconds)
    fun volumeStep(up: Boolean) =
        orchestrator.socket().volumeStep(if (up) "up" else "down")
    fun volumeSet(level: Int) = orchestrator.socket().volumeSet(level)
    fun mute() {
        val muted = !_ui.value.volume.muted
        orchestrator.socket().volumeMute(muted)
        _ui.value = _ui.value.copy(volume = _ui.value.volume.copy(muted = muted))
    }
}
