package com.stuf.remotemouse.ui.connecting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stuf.remotemouse.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectingScreen(
    onConnected: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConnectingViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui) {
        if (ui is ConnectingUiState.Succeeded) {
            onConnected()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.connecting_to),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = viewModel.deviceName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }

        ContainedLoadingIndicator(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(128.dp),
        )

        when (val state = ui) {
            is ConnectingUiState.Failed -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Button(
                    onClick = viewModel::retry,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.back))
                }
            }
            ConnectingUiState.Connecting,
            ConnectingUiState.Succeeded -> Unit
        }
    }
}
