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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    onRightClick = viewModel::rightClick,
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
                    actions.forEach { action ->
                        ActionEditCard(
                            name = action.name,
                            keyLabels = action.keyLabels,
                            onDelete = { viewModel.deleteAction(action.id) },
                            onEdit = { onEditAction(action.id) },
                        )
                    }
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
    onRightClick: () -> Unit,
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
        onRightClick = onRightClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    )
}
