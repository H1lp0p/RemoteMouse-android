package com.stuf.remotemouse.ui.control

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stuf.remotemouse.R
import com.stuf.remotemouse.ui.control.mouse.MouseControlPanel
import com.stuf.remotemouse.ui.control.player.PlayerControlPanel
import com.stuf.remotemouse.ui.control.stick.StickControlPanel
import com.stuf.remotemouse.ui.control.text.TextControlPanel

@Composable
fun ControlScreen(
    onDisconnect: () -> Unit,
    onEditActions: () -> Unit = {},
    viewModel: ControlViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.ensureConnection()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun leaveControl() {
        viewModel.disconnect()
        onDisconnect()
    }

    BackHandler { leaveControl() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
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
                Text(
                    text = ui.pingMs?.let { stringResource(R.string.control_ping_ms, it) }
                        ?: stringResource(R.string.control_ping_unknown),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(end = 8.dp),
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
                        modifier = Modifier.width(208.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (ui.isFavorite) {
                                        stringResource(R.string.remove_from_favorite)
                                    } else {
                                        stringResource(R.string.add_to_favorite)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            onClick = viewModel::toggleFavorite,
                            enabled = !ui.favoriteBusy,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.edit_actions),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            onClick = {
                                viewModel.setDeviceMenuVisible(false)
                                onEditActions()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.disconnect),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            onClick = {
                                viewModel.setDeviceMenuVisible(false)
                                leaveControl()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                            ),
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
                        onScrollDeflection = viewModel::onScrollDeflection,
                        onLeftClick = viewModel::leftClick,
                        onRightClick = viewModel::rightClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ControlMode.Player -> PlayerControlPanel(
                        media = ui.media,
                        onPlayPause = viewModel::mediaPlayPause,
                        onSeekRelative = viewModel::mediaSeekRelative,
                        onSeekAbsolute = viewModel::mediaSeekAbsolute,
                        onPrev = viewModel::mediaPrev,
                        onNext = viewModel::mediaNext,
                        onOpenActions = { viewModel.setActionsSheetVisible(true) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    ControlMode.Text -> TextControlPanel(
                        text = ui.sharedText,
                        onTextChange = viewModel::onSharedTextChange,
                        modifier = Modifier.fillMaxSize(),
                    )
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
        ControlModeChip(
            selected = selected == ControlMode.Mouse,
            labelRes = R.string.control_mode_mouse,
            iconRes = R.drawable.ic_control_mouse,
            onClick = { onSelect(ControlMode.Mouse) },
        )
        ControlModeChip(
            selected = selected == ControlMode.Stick,
            labelRes = R.string.control_mode_stick,
            iconRes = R.drawable.ic_control_stick,
            onClick = { onSelect(ControlMode.Stick) },
        )
        ControlModeChip(
            selected = selected == ControlMode.Player,
            labelRes = R.string.control_mode_player,
            iconRes = R.drawable.ic_control_player,
            onClick = { onSelect(ControlMode.Player) },
        )
        ControlModeChip(
            selected = selected == ControlMode.Text,
            labelRes = R.string.control_mode_text,
            iconRes = R.drawable.ic_control_text,
            onClick = { onSelect(ControlMode.Text) },
        )
    }
}

@Composable
private fun ControlModeChip(
    selected: Boolean,
    labelRes: Int,
    iconRes: Int,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
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
