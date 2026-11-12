package com.stuf.remotemouse.ui.control.pad

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stuf.remotemouse.R
import com.stuf.remotemouse.ui.control.ActionListItem
import com.stuf.remotemouse.ui.control.PadSlot
import kotlinx.coroutines.launch

@Composable
fun ActionPadPanel(
    slots: List<PadSlot>,
    actions: List<ActionListItem>,
    onAssign: (slot: Int, actionId: Long) -> Unit,
    onClear: (slot: Int) -> Unit,
    onRun: (actionId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosingSlot by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        slots.chunked(2).forEachIndexed { row, pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEachIndexed { column, slot ->
                    PadButton(
                        slot = slot,
                        onChoose = { choosingSlot = row * 2 + column },
                        onClear = { onClear(row * 2 + column) },
                        onRun = onRun,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }

    choosingSlot?.let { slot ->
        ChooseActionSheet(
            actions = actions,
            onDismiss = { choosingSlot = null },
            onChoose = { actionId ->
                choosingSlot = null
                onAssign(slot, actionId)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PadButton(
    slot: PadSlot,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    onRun: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val assigned = slot.actionId != null && slot.name != null
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .combinedClickable(
                onClick = {
                    val actionId = slot.actionId
                    if (assigned && actionId != null) onRun(actionId)
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (assigned) onClear() else onChoose()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = slot.name ?: stringResource(R.string.actions),
            style = MaterialTheme.typography.headlineSmall,
            color = if (assigned) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSecondary
            },
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseActionSheet(
    actions: List<ActionListItem>,
    onDismiss: () -> Unit,
    onChoose: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun hideThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { hideThen(onDismiss) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.choose_action),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (actions.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_actions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    items(actions, key = { it.id }) { action ->
                        ChooseActionRow(
                            item = action,
                            onClick = { hideThen { onChoose(action.id) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseActionRow(
    item: ActionListItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 24.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 24.dp),
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                ),
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 14.dp),
            )
        }
    }
}
