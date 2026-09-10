package com.stuf.remotemouse.ui.control

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stuf.remotemouse.R
import com.stuf.remotemouse.ui.control.mouse.MouseControlPanel
import com.stuf.remotemouse.ui.control.stick.StickControlPanel

@Composable
fun ControlScreen(
    onDisconnect: () -> Unit,
    onEditActions: () -> Unit = {},
    viewModel: ControlViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session?.deviceName ?: stringResource(R.string.device_name_fallback),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    OutlinedIconButton(
                        onClick = { viewModel.setDeviceMenuVisible(true) },
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.device_menu),
                        )
                    }
                    DropdownMenu(
                        expanded = ui.showDeviceMenu,
                        onDismissRequest = { viewModel.setDeviceMenuVisible(false) },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (ui.isFavorite) {
                                        stringResource(R.string.remove_from_favorite)
                                    } else {
                                        stringResource(R.string.add_to_favorite)
                                    },
                                )
                            },
                            onClick = viewModel::toggleFavorite,
                            enabled = !ui.favoriteBusy,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.disconnect)) },
                            onClick = {
                                viewModel.setDeviceMenuVisible(false)
                                viewModel.disconnect()
                                onDisconnect()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_actions)) },
                            onClick = {
                                viewModel.setDeviceMenuVisible(false)
                                onEditActions()
                            },
                        )
                    }
                }
            }

            ControlModeChips(
                selected = ui.mode,
                onSelect = viewModel::selectMode,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (ui.mode) {
                    ControlMode.Mouse -> MouseControlPanel(
                        leftHeld = ui.leftHeld,
                        rightHeld = ui.rightHeld,
                        onToggleLeft = viewModel::toggleLeftButton,
                        onToggleRight = viewModel::toggleRightButton,
                        onMove = viewModel::onMove,
                        onScroll = viewModel::onScroll,
                        onLeftClick = viewModel::leftClick,
                        onRightClick = viewModel::rightClick,
                        onOpenActions = { viewModel.setActionsSheetVisible(true) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    ControlMode.Stick -> StickControlPanel(
                        leftHeld = ui.leftHeld,
                        rightHeld = ui.rightHeld,
                        onToggleLeft = viewModel::toggleLeftButton,
                        onToggleRight = viewModel::toggleRightButton,
                        onOpenActions = { viewModel.setActionsSheetVisible(true) },
                        onStickDeflection = viewModel::onStickDeflection,
                        onStickReleased = viewModel::onStickReleased,
                        onScroll = { dx, dy -> viewModel.onScroll(dx, dy) },
                        onLeftClick = viewModel::leftClick,
                        onRightClick = viewModel::rightClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ControlMode.Player,
                    ControlMode.Text -> ControlModePlaceholder(mode = ui.mode)
                }
            }
        }
    }

    if (ui.showActionsSheet) {
        ActionsBottomSheet(
            volumeLevel = ui.volume.level,
            actions = actions,
            onDismiss = { viewModel.setActionsSheetVisible(false) },
            onVolumeChange = viewModel::setVolume,
            onRunAction = viewModel::runAction,
            onEditActions = {
                viewModel.setActionsSheetVisible(false)
                onEditActions()
            },
        )
    }
}

@Composable
private fun ControlModeChips(
    selected: ControlMode,
    onSelect: (ControlMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ControlMode.entries.forEach { mode ->
            val selectedChip = mode == selected
            FilterChip(
                selected = selectedChip,
                onClick = { onSelect(mode) },
                label = { Text(mode.label()) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(mode.iconRes()),
                        contentDescription = null,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun ControlModePlaceholder(mode: ControlMode) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.control_mode_coming_soon, mode.label()),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ControlMode.label(): String = when (this) {
    ControlMode.Mouse -> stringResource(R.string.control_mode_mouse)
    ControlMode.Stick -> stringResource(R.string.control_mode_stick)
    ControlMode.Player -> stringResource(R.string.control_mode_player)
    ControlMode.Text -> stringResource(R.string.control_mode_text)
}

private fun ControlMode.iconRes(): Int = when (this) {
    ControlMode.Mouse -> R.drawable.ic_control_mouse
    ControlMode.Stick -> R.drawable.ic_control_stick
    ControlMode.Player -> R.drawable.ic_control_player
    ControlMode.Text -> R.drawable.ic_control_text
}
