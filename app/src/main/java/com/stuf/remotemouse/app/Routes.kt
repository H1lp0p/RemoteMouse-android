package com.stuf.remotemouse.app

import android.net.Uri
import com.stuf.remotemouse.ui.connecting.ConnectingArgs
import com.stuf.remotemouse.ui.connecting.ConnectingViewModel

object Routes {
    const val DEVICE_LIST = "device_list"
    const val DEBUG_CONNECT = "debug_connect"
    const val CONNECTING =
        "connecting/{mode}/{deviceId}/{deviceName}?ip={ip}&port={port}&code={code}"
    const val CONTROL = "control"
    const val TEXT = "text"
    const val MEDIA = "media"
    const val ACTIONS = "actions"
    const val ACTION_EDITOR = "action_editor/{actionId}"
    const val SETTINGS = "settings"

    fun actionEditor(actionId: Long = -1L) = "action_editor/$actionId"

    fun connecting(args: ConnectingArgs): String {
        val mode = when (args) {
            is ConnectingArgs.Saved -> ConnectingViewModel.MODE_SAVED
            is ConnectingArgs.New -> ConnectingViewModel.MODE_NEW
        }
        val ip = (args as? ConnectingArgs.New)?.ip.orEmpty()
        val port = (args as? ConnectingArgs.New)?.port ?: 0
        val code = (args as? ConnectingArgs.New)?.verificationCode.orEmpty()
        return buildString {
            append("connecting/")
            append(Uri.encode(mode))
            append('/')
            append(Uri.encode(args.deviceId))
            append('/')
            append(Uri.encode(args.deviceName))
            append("?ip=")
            append(Uri.encode(ip))
            append("&port=")
            append(port)
            append("&code=")
            append(Uri.encode(code))
        }
    }
}
