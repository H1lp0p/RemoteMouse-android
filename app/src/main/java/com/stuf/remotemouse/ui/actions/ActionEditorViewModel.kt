package com.stuf.remotemouse.ui.actions

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.local.repo.ActionRepository
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.domain.KeyToken
import com.stuf.remotemouse.domain.VkCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActionEditorUiState(
    val actionId: Long = -1L,
    val deviceId: String? = null,
    val name: String = "",
    val keys: List<KeyToken> = emptyList(),
    val draft: TextFieldValue = TextFieldValue(),
    val showDangerDialog: Boolean = false,
    val dangerComboLabel: String = "",
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ActionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val actionRepository: ActionRepository,
    orchestrator: ConnectionOrchestrator,
) : ViewModel() {
    private val initialId: Long = savedStateHandle["actionId"] ?: -1L

    private val _ui = MutableStateFlow(
        ActionEditorUiState(
            actionId = initialId,
            deviceId = orchestrator.session.value?.deviceId,
        ),
    )
    val ui: StateFlow<ActionEditorUiState> = _ui.asStateFlow()

    init {
        if (initialId > 0) {
            viewModelScope.launch {
                val loaded = actionRepository.getActionWithSteps(initialId) ?: return@launch
                val codes = loaded.steps.flatMap { VkCodes.parse(it.stepStringCode) }
                _ui.update {
                    it.copy(
                        actionId = loaded.action.id,
                        deviceId = loaded.action.deviceId,
                        name = loaded.action.name,
                        keys = VkCodes.tokensFromCodes(codes),
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = _ui.update { it.copy(name = value, error = null) }

    fun onDraftChange(value: TextFieldValue) {
        val previous = _ui.value.draft.text
        val next = value.text
        // Space commits the token before it.
        if (next.length > previous.length && next.lastOrNull() == ' ') {
            val tokenText = next.dropLast(1)
            val resolved = VkCodes.resolveToken(tokenText)
            if (resolved != null) {
                _ui.update {
                    it.copy(
                        keys = it.keys + resolved,
                        draft = TextFieldValue(),
                        error = null,
                    )
                }
            } else if (tokenText.isBlank()) {
                _ui.update { it.copy(draft = TextFieldValue()) }
            } else {
                // Keep typed text without consuming an unmatched token.
                _ui.update {
                    it.copy(
                        draft = TextFieldValue(
                            text = tokenText,
                            selection = TextRange(tokenText.length),
                        ),
                        error = null,
                    )
                }
            }
            return
        }
        _ui.update { it.copy(draft = value, error = null) }
    }

    /** Called when Backspace is pressed while the draft field is empty. */
    fun onBackspaceOnEmptyDraft(): Boolean {
        val keys = _ui.value.keys
        if (keys.isEmpty()) return false
        val last = keys.last()
        val restored = last.label.dropLast(1)
        _ui.update {
            it.copy(
                keys = it.keys.dropLast(1),
                draft = TextFieldValue(
                    text = restored,
                    selection = TextRange(restored.length),
                ),
                error = null,
            )
        }
        return true
    }

    fun removeKey(index: Int) {
        _ui.update {
            it.copy(keys = it.keys.filterIndexed { i, _ -> i != index }, error = null)
        }
    }

    fun addKey(token: KeyToken) {
        _ui.update {
            it.copy(keys = it.keys + token, draft = TextFieldValue(), error = null)
        }
    }

    fun dismissDangerDialog() {
        _ui.update { it.copy(showDangerDialog = false) }
    }

    fun confirmDangerAndSave() {
        _ui.update { it.copy(showDangerDialog = false) }
        persist()
    }

    fun save() {
        val committed = commitDraftIfPossible()
        if (!committed) return

        val state = _ui.value
        if (state.deviceId == null) {
            _ui.update { it.copy(error = "No connected device") }
            return
        }
        if (state.name.isBlank()) {
            _ui.update { it.copy(error = "Name required") }
            return
        }
        if (state.keys.isEmpty()) {
            _ui.update { it.copy(error = "Add at least one key") }
            return
        }
        if (state.draft.text.isNotBlank()) {
            _ui.update { it.copy(error = "Unknown key: ${state.draft.text.trim()}") }
            return
        }
        if (VkCodes.isDangerous(state.keys)) {
            _ui.update {
                it.copy(
                    showDangerDialog = true,
                    dangerComboLabel = VkCodes.dangerMessageCombo(state.keys),
                    error = null,
                )
            }
            return
        }
        persist()
    }

    private fun commitDraftIfPossible(): Boolean {
        val draft = _ui.value.draft.text.trim()
        if (draft.isEmpty()) return true
        val resolved = VkCodes.resolveToken(draft) ?: return true
        _ui.update {
            it.copy(keys = it.keys + resolved, draft = TextFieldValue(), error = null)
        }
        return true
    }

    private fun persist() {
        viewModelScope.launch {
            val state = _ui.value
            val deviceId = state.deviceId ?: return@launch
            val code = VkCodes.encode(state.keys)
            val stepName = VkCodes.formatSequence(state.keys.map { it.vkCode })
            val id = actionRepository.saveAction(
                actionId = state.actionId,
                deviceId = deviceId,
                name = state.name.trim(),
                steps = listOf(stepName to code),
            )
            _ui.update { it.copy(actionId = id, saved = true, error = null) }
        }
    }
}
