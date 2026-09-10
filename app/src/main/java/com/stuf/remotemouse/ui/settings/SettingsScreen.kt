package com.stuf.remotemouse.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val me by viewModel.me.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    var clientName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(me?.clientName) {
        if (me != null && clientName.isEmpty()) {
            clientName = me!!.clientName
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Client", style = MaterialTheme.typography.titleMedium)
            Text("client_id: ${me?.clientId ?: "…"}", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = clientName,
                onValueChange = {
                    clientName = it
                    viewModel.updateClientName(it)
                },
                label = { Text("Client name") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Touchpad sensitivity: ${"%.1f".format(sensitivity)}")
            Slider(
                value = sensitivity,
                onValueChange = viewModel::setSensitivity,
                valueRange = 0.3f..3f,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Saved PCs", style = MaterialTheme.typography.titleMedium)
            if (devices.isEmpty()) {
                Text("No saved devices")
            } else {
                devices.forEach { device ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name)
                                Text(
                                    "${device.lastIp}:${device.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { viewModel.deleteDevice(device.deviceId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
