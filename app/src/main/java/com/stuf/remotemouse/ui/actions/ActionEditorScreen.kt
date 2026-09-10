package com.stuf.remotemouse.ui.actions

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
fun ActionEditorScreen(
    actionId: Long,
    onBack: () -> Unit,
    viewModel: ActionEditorViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var stepName by rememberSaveable { mutableStateOf("") }
    var stepCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(ui.saved) {
        if (ui.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (actionId > 0) "Edit action" else "New action") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = ui.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Action name") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Steps (VK codes, e.g. 17,67 for Ctrl+C)")
            ui.steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${index + 1}. ${step.name} [${step.code}]", modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.moveStep(index, index - 1) },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up")
                    }
                    IconButton(
                        onClick = { viewModel.moveStep(index, index + 1) },
                        enabled = index < ui.steps.lastIndex,
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down")
                    }
                    IconButton(onClick = { viewModel.removeStep(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }

            OutlinedTextField(
                value = stepName,
                onValueChange = { stepName = it },
                label = { Text("Step name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = stepCode,
                onValueChange = { stepCode = it },
                label = { Text("VK codes (17,67)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    viewModel.addStep(stepName, stepCode)
                    stepName = ""
                    stepCode = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add step")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Ctrl+C" to "17,67",
                    "Ctrl+V" to "17,86",
                    "Alt+Tab" to "18,9",
                    "Win+D" to "91,68",
                    "Esc" to "27",
                    "F5" to "116",
                ).forEach { (label, code) ->
                    OutlinedButton(onClick = { viewModel.addStep(label, code) }) {
                        Text(label)
                    }
                }
            }

            ui.error?.let { Text(it) }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Save action")
            }
        }
    }
}
