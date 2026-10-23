package com.stuf.remotemouse.domain

sealed class PairingException(
    message: String,
    val retryAfterSeconds: Int? = null,
) : Exception(message) {
    class DeviceNotTrusted(message: String = "device_not_trusted") : PairingException(message)
    class PairingBlocked(message: String = "pairing blocked") : PairingException(message)
    class RateLimited(retryAfter: Int?, message: String = "too many attempts") :
        PairingException(message, retryAfter)
    class InvalidCode(message: String = "invalid verification code") : PairingException(message)
    class Network(message: String) : PairingException(message)
    class Unexpected(message: String) : PairingException(message)
}

data class ActiveSession(
    val deviceId: String,
    val deviceName: String,
    val ip: String,
    val port: Int,
    val sessionId: Int,
    val udpPort: Int,
)
