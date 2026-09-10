package com.stuf.remotemouse.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stuf.remotemouse.R
import com.stuf.remotemouse.data.local.entity.ActionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsBottomSheet(
    volumeLevel: Int,
    actions: List<ActionEntity>,
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Slider(
                value = volumeLevel.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.play_action),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onEditActions) {
                    Text(stringResource(R.string.edit))
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(actions, key = { it.id }) { action ->
                        TextButton(
                            onClick = { onRunAction(action.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = action.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
