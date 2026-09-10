package com.stuf.remotemouse.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stuf.remotemouse.ui.actions.ActionEditorScreen
import com.stuf.remotemouse.ui.actions.ActionsScreen
import com.stuf.remotemouse.ui.connecting.ConnectingScreen
import com.stuf.remotemouse.ui.control.ControlScreen
import com.stuf.remotemouse.ui.debug.DebugConnectScreen
import com.stuf.remotemouse.ui.home.DeviceListScreen
import com.stuf.remotemouse.ui.media.MediaScreen
import com.stuf.remotemouse.ui.settings.SettingsScreen
import com.stuf.remotemouse.ui.text.TextInputScreen

@Composable
fun RemoteMouseNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.DEVICE_LIST,
    ) {
        composable(Routes.DEVICE_LIST) {
            DeviceListScreen(
                onAddDevice = { navController.navigate(Routes.DEBUG_CONNECT) },
                onStartConnecting = { args ->
                    navController.navigate(Routes.connecting(args))
                },
            )
        }
        composable(Routes.DEBUG_CONNECT) {
            DebugConnectScreen(
                onConnected = { navController.navigate(Routes.CONTROL) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
                onDisconnect = {
                    navController.navigate(Routes.DEVICE_LIST) {
                        popUpTo(Routes.DEVICE_LIST) { inclusive = true }
                    }
                },
                onEditActions = {
                    // Stub: dedicated edit-actions screen later.
                },
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
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
