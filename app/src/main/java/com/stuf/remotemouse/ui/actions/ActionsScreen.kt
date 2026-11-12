package com.stuf.remotemouse.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stuf.remotemouse.R
import com.stuf.remotemouse.data.settings.CursorSensitivity
import com.stuf.remotemouse.ui.control.mouse.TouchpadSurface
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ActionsScreen(
    onBack: () -> Unit,
    onEditAction: (Long) -> Unit,
    onCreateAction: () -> Unit,
    viewModel: ActionsViewModel = hiltViewModel(),
) {
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val saveBusy by viewModel.saveBusy.collectAsStateWithLifecycle()
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    fun leave(disconnect: Boolean) {
        scope.launch {
            viewModel.persistSensitivity()
            if (disconnect) {
                viewModel.disconnect()
            } else {
                onBack()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            DeviceSettingsHeader(
                deviceName = deviceName,
                isSaved = isSaved,
                saveBusy = saveBusy,
                onBack = { leave(disconnect = false) },
                onToggleSaved = viewModel::toggleSaved,
                onDisconnect = { leave(disconnect = true) },
            )

            if (!connected) {
                Text(
                    text = stringResource(R.string.actions_connect_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                )
            } else {
                SensitivityBlock(
                    sensitivity = sensitivity,
                    onSensitivityChange = viewModel::setSensitivity,
                    onSensitivityChangeFinished = {
                        scope.launch { viewModel.persistSensitivity() }
                    },
                    onMove = viewModel::onMove,
                    onScroll = viewModel::onScroll,
                    onLeftClick = viewModel::leftClick,
                )
                Text(
                    text = stringResource(R.string.edit_actions),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ReorderableActionCards(
                        actions = actions,
                        onDelete = viewModel::deleteAction,
                        onEdit = onEditAction,
                        onReorder = viewModel::reorder,
                    )
                    TextButton(
                        onClick = onCreateAction,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.add_new),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableActionCards(
    actions: List<ActionEditItem>,
    onDelete: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val spacingPx = with(LocalDensity.current) { 16.dp.toPx() }
    val state = remember(spacingPx) {
        ActionDragState(spacingPx).also { it.items = actions }
    }
    LaunchedEffect(actions) { state.sync(actions) }
    var pendingDelete by remember { mutableStateOf<ActionEditItem?>(null) }
    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_action)) },
            text = { Text(stringResource(R.string.delete_action_message, pending.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(pending.id)
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    state.items.forEach { action ->
        key(action.id) {
            val dragging = state.draggingId == action.id
            ActionEditCard(
                name = action.name,
                keyLabels = action.keyLabels,
                dragging = dragging,
                dragOffsetY = if (dragging) state.dragOffset else 0f,
                onHeightChanged = { state.heights[action.id] = it },
                onDragStart = { state.start(action.id) },
                onDrag = { state.drag(it) },
                onDragEnd = { state.end(onReorder, actions.map { it.id }) },
                onDelete = { pendingDelete = action },
                onEdit = { onEdit(action.id) },
            )
        }
    }
}

private class ActionDragState(private val spacingPx: Float) {
    var items by mutableStateOf<List<ActionEditItem>>(emptyList())
    var draggingId by mutableStateOf<Long?>(null)
    var dragOffset by mutableFloatStateOf(0f)
    val heights = mutableMapOf<Long, Int>()
    private var pending: List<Long>? = null

    fun sync(actions: List<ActionEditItem>) {
        if (draggingId != null) {
            val byId = actions.associateBy { it.id }
            val refreshed = items.mapNotNull { byId[it.id] }
            if (refreshed.size == items.size) items = refreshed
            return
        }
        val pendingIds = pending
        if (pendingIds == null || actions.map { it.id } == pendingIds) {
            pending = null
            items = actions
        }
    }

    fun start(id: Long) {
        draggingId = id
        dragOffset = 0f
    }

    fun drag(dy: Float) {
        val id = draggingId ?: return
        val current = items
        val index = current.indexOfFirst { it.id == id }
        val height = heights[id]
        if (index < 0 || height == null || current.any { heights[it.id] == null }) {
            dragOffset += dy
            return
        }
        dragOffset += dy
        val topsBefore = tops(current)
        val target = slot(topsBefore[index] + dragOffset + height / 2f, current)
        if (target == index) return
        val moved = current.toMutableList()
        val item = moved.removeAt(index)
        moved.add(target, item)
        dragOffset += topsBefore[index] - tops(moved)[target]
        items = moved
    }

    fun end(onReorder: (List<Long>) -> Unit, saved: List<Long>) {
        val ids = items.map { it.id }
        draggingId = null
        dragOffset = 0f
        if (ids != saved) {
            pending = ids
            onReorder(ids)
        }
    }

    private fun tops(list: List<ActionEditItem>): List<Float> {
        var y = 0f
        return list.map { item ->
            val top = y
            y += (heights[item.id] ?: 0) + spacingPx
            top
        }
    }

    private fun slot(center: Float, list: List<ActionEditItem>): Int {
        var y = 0f
        list.forEachIndexed { index, item ->
            val height = heights[item.id]?.toFloat() ?: return index
            if (center < y + height + spacingPx / 2f) return index
            y += height + spacingPx
        }
        return list.lastIndex.coerceAtLeast(0)
    }
}

@Composable
private fun DeviceSettingsHeader(
    deviceName: String,
    isSaved: Boolean,
    saveBusy: Boolean,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = deviceName.ifBlank { stringResource(R.string.device_name_fallback) },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onToggleSaved,
            enabled = !saveBusy,
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = stringResource(
                    if (isSaved) R.string.remove_from_favorite else R.string.add_to_favorite,
                ),
                tint = if (isSaved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        TextButton(onClick = onDisconnect) {
            Text(stringResource(R.string.disconnect))
        }
    }
}

@Composable
private fun SensitivityBlock(
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    onSensitivityChangeFinished: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onScroll: (Float) -> Unit,
    onLeftClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.sensitivity),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = String.format(Locale.US, "%.2f", sensitivity),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondary,
        )
    }
    Slider(
        value = sensitivity.coerceIn(CursorSensitivity.MIN, CursorSensitivity.MAX),
        onValueChange = onSensitivityChange,
        onValueChangeFinished = onSensitivityChangeFinished,
        valueRange = CursorSensitivity.MIN..CursorSensitivity.MAX,
        modifier = Modifier.fillMaxWidth(),
    )
    TouchpadSurface(
        onMove = onMove,
        onScroll = onScroll,
        onLeftClick = onLeftClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    )
}
