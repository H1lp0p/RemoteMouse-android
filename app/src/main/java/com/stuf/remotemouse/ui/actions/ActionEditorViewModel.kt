package com.stuf.remotemouse.ui.actions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.local.repo.ActionRepository
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StepDraft(
    val name: String,
    val code: String,
)

data class ActionEditorUiState(
    val actionId: Long = -1L,
    val deviceId: String? = null,
    val name: String = "",
    val steps: List<StepDraft> = emptyList(),
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
                _ui.update {
                    it.copy(
                        actionId = loaded.action.id,
                        deviceId = loaded.action.deviceId,
                        name = loaded.action.name,
                        steps = loaded.steps.map { step ->
                            StepDraft(step.name, step.stepStringCode)
                        },
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = _ui.update { it.copy(name = value) }

    fun addStep(name: String, code: String) {
        if (code.isBlank()) return
        _ui.update {
            it.copy(steps = it.steps + StepDraft(name.ifBlank { code }, code.trim()))
        }
    }

    fun removeStep(index: Int) {
        _ui.update {
            it.copy(steps = it.steps.filterIndexed { i, _ -> i != index })
        }
    }

    fun moveStep(from: Int, to: Int) {
        _ui.update { state ->
            if (from !in state.steps.indices || to !in state.steps.indices) return@update state
            val list = state.steps.toMutableList()
            val item = list.removeAt(from)
            list.add(to, item)
            state.copy(steps = list)
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _ui.value
            val deviceId = state.deviceId
            if (deviceId == null) {
                _ui.update { it.copy(error = "No connected device") }
                return@launch
            }
            if (state.name.isBlank()) {
                _ui.update { it.copy(error = "Name required") }
                return@launch
            }
            if (state.steps.isEmpty()) {
                _ui.update { it.copy(error = "Add at least one step") }
                return@launch
            }
            val id = actionRepository.saveAction(
                actionId = state.actionId,
                deviceId = deviceId,
                name = state.name.trim(),
                steps = state.steps.map { it.name to it.code },
            )
            _ui.update { it.copy(actionId = id, saved = true, error = null) }
        }
    }
}
