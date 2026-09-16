package com.stuf.remotemouse.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stuf.remotemouse.R
import com.stuf.remotemouse.ui.components.DeviceListCard
import com.stuf.remotemouse.ui.components.DeviceListTone
import com.stuf.remotemouse.ui.connecting.ConnectingArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onAddDevice: () -> Unit,
    onStartConnecting: (ConnectingArgs) -> Unit,
    viewModel: DeviceListViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDevice,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_device),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.remote_app_title),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        lineHeight = 36.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            PullToRefreshBox(
                isRefreshing = ui.isSearching,
                onRefresh = viewModel::refreshNetwork,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    DeviceListCard(
                        title = stringResource(R.string.favorite_devices),
                        devices = favorites.map { it.deviceId to it.name },
                        expanded = ui.favoritesExpanded,
                        onToggle = viewModel::toggleFavorites,
                        onDeviceClick = viewModel::selectFavorite,
                        tone = DeviceListTone.Favorite,
                    )

                    DeviceListCard(
                        title = stringResource(R.string.found_in_network),
                        devices = ui.discovered.map { it.deviceId to it.deviceName },
                        expanded = ui.networkExpanded,
                        onToggle = viewModel::toggleNetwork,
                        onDeviceClick = { id ->
                            val device = ui.discovered.firstOrNull { it.deviceId == id }
                                ?: return@DeviceListCard
                            viewModel.selectDiscovered(device)
                        },
                        tone = DeviceListTone.Network,
                    )
                }
            }
        }
    }

    ui.selection?.let { selection ->
        DeviceSelectionBottomSheet(
            selection = selection,
            step = ui.sheetStep,
            code = ui.verificationCode,
            errorMessage = ui.sheetError,
            sheetState = sheetState,
            onDismiss = viewModel::dismissSheet,
            onCodeChange = viewModel::onCodeChange,
            onConnectClick = {
                viewModel.onConnectClick { args ->
                    scope.launch {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        // Wait for IME dismiss animation before closing the sheet / navigating.
                        delay(220)
                        sheetState.hide()
                        viewModel.dismissSheet()
                        onStartConnecting(args)
                    }
                }
            },
        )
    }
}
