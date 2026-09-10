package com.stuf.remotemouse.ui.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stuf.remotemouse.data.remote.WsConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConnectScreen(
    onConnected: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DebugConnectViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Connect") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("1. UDP search", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = viewModel::search,
                enabled = !ui.isSearching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (ui.isSearching) "Searching…" else "Search PCs")
            }

            if (ui.discovered.isNotEmpty()) {
                ui.discovered.forEach { device ->
                    val selected = ui.selected?.deviceId == device.deviceId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectDiscovered(device) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = device.deviceName + if (selected) " ✓" else "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text("${device.ip}:${device.port}")
                            Text(
                                "pairing=${device.requiresPairing} trusted=${device.isTrusted}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("2. Pair with code", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = ui.code,
                onValueChange = viewModel::onCodeChange,
                label = { Text("6-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.pairWithCode(onConnected) },
                enabled = !ui.isPairing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (ui.isPairing) "Pairing…" else "Connect")
            }

            if (ui.savedDevices.isNotEmpty()) {
                HorizontalDivider()
                Text("Saved PCs", style = MaterialTheme.typography.titleMedium)
                ui.savedDevices.forEach { device ->
                    OutlinedButton(
                        onClick = { viewModel.pairSaved(device.deviceId, onConnected) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${device.name} (${device.lastIp})")
                    }
                }
            }

            HorizontalDivider()
            Text("3. Session", style = MaterialTheme.typography.titleMedium)
            Text("WS: ${ui.wsState}")
            Text("Session: ${ui.sessionLabel ?: "—"}")
            Text(
                text = "Ping: ${ui.pingMs?.let { "${it} ms" } ?: "—"}",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::sendHeartbeat,
                    enabled = ui.wsState == WsConnectionState.Connected,
                ) { Text("Heartbeat") }
                OutlinedButton(
                    onClick = viewModel::saveDevice,
                    enabled = ui.wsState == WsConnectionState.Connected,
                ) { Text("Save device") }
                OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
            }
            if (ui.wsState == WsConnectionState.Connected) {
                Button(onClick = onConnected, modifier = Modifier.fillMaxWidth()) {
                    Text("Open control")
                }
            }

            HorizontalDivider()
            Text("Log", style = MaterialTheme.typography.titleMedium)
            ui.log.forEach { line ->
                Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
