package com.stuf.remotemouse.ui.connecting

sealed interface ConnectingArgs {
    val deviceId: String
    val deviceName: String

    data class Saved(
        override val deviceId: String,
        override val deviceName: String,
    ) : ConnectingArgs

    data class New(
        override val deviceId: String,
        override val deviceName: String,
        val ip: String,
        val port: Int,
        val verificationCode: String,
    ) : ConnectingArgs
}
