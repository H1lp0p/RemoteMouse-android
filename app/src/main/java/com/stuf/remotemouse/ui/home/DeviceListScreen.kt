package com.stuf.remotemouse.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
    onOpenQrScanner: () -> Unit,
    onStartConnecting: (ConnectingArgs) -> Unit,
    viewModel: DeviceListViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val clientName by viewModel.clientName.collectAsStateWithLifecycle()
    val deviceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val renameSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun openQrScanner() {
        scope.launch {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            if (ui.selection != null) {
                deviceSheetState.hide()
                viewModel.dismissSheet()
            }
            onOpenQrScanner()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = ::openQrScanner,
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
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
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

                    ClientNameRow(
                        name = clientName.ifBlank { stringResource(R.string.device_name_fallback) },
                        onEditClick = viewModel::openRenameSheet,
                        modifier = Modifier.fillMaxWidth(),
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
            sheetState = deviceSheetState,
            onDismiss = viewModel::dismissSheet,
            onCodeChange = viewModel::onCodeChange,
            onConnectClick = {
                viewModel.onConnectClick { args ->
                    scope.launch {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        delay(220)
                        deviceSheetState.hide()
                        viewModel.dismissSheet()
                        onStartConnecting(args)
                    }
                }
            },
            onQrClick = ::openQrScanner,
        )
    }

    if (ui.showRenameSheet) {
        RenameDeviceBottomSheet(
            draft = ui.renameDraft,
            sheetState = renameSheetState,
            onDraftChange = viewModel::onRenameDraftChange,
            onDismiss = { save ->
                scope.launch {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    renameSheetState.hide()
                    viewModel.dismissRenameSheet(save = save)
                }
            },
        )
    }
}

@Composable
private fun ClientNameRow(
    name: String,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 36.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEditClick) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(R.string.edit_device_name),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameDeviceBottomSheet(
    draft: String,
    sheetState: SheetState,
    onDraftChange: (String) -> Unit,
    onDismiss: (save: Boolean) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss(true) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                label = { Text(stringResource(R.string.device_name_label)) },
                shape = RoundedCornerShape(4.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { onDismiss(true) },
                ),
            )
        }
    }
}
