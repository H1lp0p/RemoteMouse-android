package com.stuf.remotemouse.ui.control.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stuf.remotemouse.R
import com.stuf.remotemouse.data.remote.dto.MediaUpdateDto

@Composable
fun PlayerControlPanel(
    media: MediaUpdateDto?,
    onPlayPause: () -> Unit,
    onSeekRelative: (seconds: Int) -> Unit,
    onSeekAbsolute: (positionSeconds: Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = media?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.media_no_data)
    val artist = media?.artist?.takeIf { it.isNotBlank() }
    val duration = (media?.durationSeconds ?: 0).coerceAtLeast(0)
    val position = (media?.positionSeconds ?: 0).coerceIn(0, duration.coerceAtLeast(0))
    val isPlaying = media?.isPlaying == true
    val canSeek = media?.canSeek != false && duration > 0

    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    val displayedPosition = if (sliderDragging) {
        sliderValue.toInt()
    } else {
        position
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist != null) {
                    Text(
                        text = stringResource(R.string.media_by_artist, artist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Slider(
                    value = if (sliderDragging) {
                        sliderValue
                    } else {
                        position.toFloat()
                    },
                    onValueChange = { value ->
                        if (!canSeek) return@Slider
                        sliderDragging = true
                        sliderValue = value
                    },
                    onValueChangeFinished = {
                        if (sliderDragging && canSeek) {
                            onSeekAbsolute(sliderValue.toInt())
                        }
                        sliderDragging = false
                    },
                    valueRange = 0f..(duration.coerceAtLeast(1).toFloat()),
                    enabled = canSeek,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatMediaTime(displayedPosition),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = formatMediaTime(duration),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerPrimaryButton(
                        icon = Icons.Default.FastRewind,
                        contentDescription = stringResource(R.string.media_rewind),
                        wide = true,
                        onClick = { onSeekRelative(-10) },
                    )
                    PlayerPrimaryButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.media_play_pause),
                        wide = false,
                        onClick = onPlayPause,
                    )
                    PlayerPrimaryButton(
                        icon = Icons.Default.FastForward,
                        contentDescription = stringResource(R.string.media_forward),
                        wide = true,
                        onClick = { onSeekRelative(10) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerSecondaryButton(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.media_prev),
                        onClick = onPrev,
                        modifier = Modifier.weight(1f),
                    )
                    PlayerSecondaryButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.media_next),
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Surface(
            onClick = onOpenActions,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.actions),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }
    }
}

@Composable
private fun PlayerPrimaryButton(
    icon: ImageVector,
    contentDescription: String,
    wide: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = if (wide) {
            Modifier.size(width = 128.dp, height = 96.dp)
        } else {
            Modifier.size(96.dp)
        },
        shape = RoundedCornerShape(16.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun PlayerSecondaryButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

internal fun formatMediaTime(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
