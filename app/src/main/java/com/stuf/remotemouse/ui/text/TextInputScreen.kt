package com.stuf.remotemouse.ui.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputScreen(
    onBack: () -> Unit,
    viewModel: TextInputViewModel = hiltViewModel(),
) {
    var text by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text input") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text to type on PC") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = {
                    viewModel.sendText(text)
                    text = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send text")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // VK codes: Enter=13, Backspace=8, Escape=27, Tab=9, Space=32
                OutlinedButton(onClick = { viewModel.sendKey(13) }) { Text("Enter") }
                OutlinedButton(onClick = { viewModel.sendKey(8) }) { Text("Backspace") }
                OutlinedButton(onClick = { viewModel.sendKey(27) }) { Text("Esc") }
                OutlinedButton(onClick = { viewModel.sendKey(9) }) { Text("Tab") }
            }
        }
    }
}
