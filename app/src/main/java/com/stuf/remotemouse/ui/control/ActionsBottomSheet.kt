package com.stuf.remotemouse.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stuf.remotemouse.R

data class ActionListItem(
    val id: Long,
    val name: String,
    val subtitle: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsBottomSheet(
    volumeLevel: Int,
    actions: List<ActionListItem>,
    onDismiss: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onRunAction: (Long) -> Unit,
    onEditActions: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume),
                    contentDescription = stringResource(R.string.volume),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
                Slider(
                    value = volumeLevel.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.play_action),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(
                    onClick = onEditActions,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.edit),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

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
                        ActionRow(
                            item = action,
                            onPlay = { onRunAction(action.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    item: ActionListItem,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
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
        FilledIconButton(
            onClick = onPlay,
            shape = RoundedCornerShape(12.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_control_player),
                contentDescription = stringResource(R.string.run_action),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
