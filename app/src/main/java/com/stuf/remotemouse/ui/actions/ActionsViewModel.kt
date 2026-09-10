package com.stuf.remotemouse.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.local.entity.ActionEntity
import com.stuf.remotemouse.data.local.repo.ActionRepository
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val actionRepository: ActionRepository,
    private val orchestrator: ConnectionOrchestrator,
) : ViewModel() {
    val deviceId: StateFlow<String?> = orchestrator.session
        .map { it?.deviceId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val actions: StateFlow<List<ActionEntity>> = deviceId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else actionRepository.observeActions(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun deleteAction(actionId: Long) {
        viewModelScope.launch { actionRepository.deleteAction(actionId) }
    }
}
