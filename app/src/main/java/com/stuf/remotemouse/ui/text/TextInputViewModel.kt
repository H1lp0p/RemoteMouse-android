package com.stuf.remotemouse.ui.text

import androidx.lifecycle.ViewModel
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TextInputViewModel @Inject constructor(
    private val orchestrator: ConnectionOrchestrator,
) : ViewModel() {
    fun sendText(text: String) {
        if (text.isBlank()) return
        orchestrator.socket().textInput(text)
    }

    fun sendKey(vk: Int) {
        orchestrator.socket().keySequence(listOf(vk))
    }
}
