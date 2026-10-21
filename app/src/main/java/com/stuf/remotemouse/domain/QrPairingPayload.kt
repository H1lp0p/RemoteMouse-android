package com.stuf.remotemouse.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class QrPairingPayload(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    val ip: String,
    val port: Int = 12345,
    @SerialName("verification_code") val verificationCode: String,
)

object QrPairingCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: QrPairingPayload): String = json.encodeToString(QrPairingPayload.serializer(), payload)

    fun decode(raw: String): QrPairingPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val parsed = json.decodeFromString(QrPairingPayload.serializer(), trimmed)
            if (
                parsed.deviceId.isBlank() ||
                parsed.ip.isBlank() ||
                parsed.verificationCode.length != 6 ||
                parsed.port !in 1..65535
            ) {
                null
            } else {
                parsed.copy(
                    deviceName = parsed.deviceName.ifBlank { "PC" },
                    verificationCode = parsed.verificationCode.filter { it.isDigit() },
                ).takeIf { it.verificationCode.length == 6 }
            }
        } catch (_: Exception) {
            null
        }
    }
}
