package com.stuf.remotemouse.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.ui.actions.ActionEditorScreen
import com.stuf.remotemouse.ui.actions.ActionsScreen
import com.stuf.remotemouse.ui.connecting.ConnectingScreen
import com.stuf.remotemouse.ui.control.ControlScreen
import com.stuf.remotemouse.ui.home.DeviceListScreen
import com.stuf.remotemouse.ui.media.MediaScreen
import com.stuf.remotemouse.ui.qr.QrScannerScreen
import com.stuf.remotemouse.ui.text.TextInputScreen

@Composable
fun RemoteMouseNavHost(orchestrator: ConnectionOrchestrator) {
    val navController = rememberNavController()
    LaunchedEffect(orchestrator) {
        var hadSession = orchestrator.session.value != null
        orchestrator.session.collect { session ->
            val lost = hadSession && session == null
            hadSession = session != null
            if (!lost) return@collect
            val route = navController.currentDestination?.route ?: return@collect
            if (route == Routes.DEVICE_LIST || route == Routes.QR_SCANNER) return@collect
            navController.navigate(Routes.DEVICE_LIST) {
                popUpTo(Routes.DEVICE_LIST) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = Routes.DEVICE_LIST,
    ) {
        composable(Routes.DEVICE_LIST) {
            DeviceListScreen(
                onOpenQrScanner = { navController.navigate(Routes.QR_SCANNER) },
                onStartConnecting = { args ->
                    navController.navigate(Routes.connecting(args))
                },
            )
        }
        composable(Routes.QR_SCANNER) {
            QrScannerScreen(
                onClose = { navController.popBackStack() },
                onQrScanned = { args ->
                    navController.navigate(Routes.connecting(args)) {
                        popUpTo(Routes.DEVICE_LIST) { inclusive = false }
                    }
                },
            )
        }
        composable(
            route = Routes.CONNECTING,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("deviceId") { type = NavType.StringType },
                navArgument("deviceName") { type = NavType.StringType },
                navArgument("ip") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("port") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("code") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            ConnectingScreen(
                onConnected = {
                    navController.navigate(Routes.CONTROL) {
                        popUpTo(Routes.DEVICE_LIST) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CONTROL) {
            ControlScreen(
                onEditActions = { navController.navigate(Routes.ACTIONS) },
            )
        }
        composable(Routes.TEXT) {
            TextInputScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MEDIA) {
            MediaScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACTIONS) {
            ActionsScreen(
                onBack = { navController.popBackStack() },
                onEditAction = { id -> navController.navigate(Routes.actionEditor(id)) },
                onCreateAction = { navController.navigate(Routes.actionEditor()) },
            )
        }
        composable(
            route = Routes.ACTION_EDITOR,
            arguments = listOf(navArgument("actionId") { type = NavType.LongType }),
        ) { entry ->
            val actionId = entry.arguments?.getLong("actionId") ?: -1L
            ActionEditorScreen(
                actionId = actionId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
