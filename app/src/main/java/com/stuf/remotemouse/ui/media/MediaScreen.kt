package com.stuf.remotemouse.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    onBack: () -> Unit,
    viewModel: MediaViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val media = ui.media

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(media?.title ?: "No media info", style = MaterialTheme.typography.titleLarge)
            Text(media?.artist ?: "—", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${media?.positionSeconds ?: 0}s / ${media?.durationSeconds ?: 0}s",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = viewModel::prev) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev")
                }
                IconButton(onClick = viewModel::playPause) {
                    Icon(
                        if (media?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                    )
                }
                IconButton(onClick = viewModel::next) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.seek(-10) }) { Text("-10s") }
                OutlinedButton(onClick = { viewModel.seek(10) }) { Text("+10s") }
            }

            Text("Volume: ${ui.volume.level}%${if (ui.volume.muted) " (muted)" else ""}")
            Slider(
                value = ui.volume.level.toFloat(),
                onValueChange = { viewModel.volumeSet(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.volumeStep(false) }) { Text("Vol -") }
                OutlinedButton(onClick = { viewModel.volumeStep(true) }) { Text("Vol +") }
                IconButton(onClick = viewModel::mute) {
                    Icon(
                        if (ui.volume.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute",
                    )
                }
            }
        }
    }
}
