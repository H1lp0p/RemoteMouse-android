package com.stuf.remotemouse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stuf.remotemouse.R

enum class DeviceListTone {
    Favorite,
    Network,
}

@Composable
fun DeviceListCard(
    title: String,
    devices: List<Pair<String, String>>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDeviceClick: (id: String) -> Unit,
    tone: DeviceListTone,
    modifier: Modifier = Modifier,
    emptyHint: String = stringResource(R.string.device_list_empty_hint),
) {
    val containerColor = when (tone) {
        DeviceListTone.Favorite -> MaterialTheme.colorScheme.surfaceContainerHighest
        DeviceListTone.Network -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clickable(role = Role.Button, onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        lineHeight = 36.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse_section else R.string.expand_section,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (devices.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.device_list_empty),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = 24.sp,
                                    lineHeight = 36.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = emptyHint,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 14.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(
                                if (tone == DeviceListTone.Network) 24.dp else 16.dp,
                            ),
                        ) {
                            devices.forEach { (id, name) ->
                                PcDeviceItem(
                                    name = name,
                                    onClick = { onDeviceClick(id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
