package com.stuf.remotemouse.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuf.remotemouse.data.local.repo.ActionRepository
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.domain.VkCodes
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

data class ActionEditItem(
    val id: Long,
    val name: String,
    val keyLabels: List<String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val actionRepository: ActionRepository,
    private val orchestrator: ConnectionOrchestrator,
) : ViewModel() {
    val connected: StateFlow<Boolean> = orchestrator.session
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val deviceId: StateFlow<String?> = orchestrator.session
        .map { it?.deviceId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
}
