package com.stuf.remotemouse.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Only protocol this client speaks. The pair body sends this single value, never a list. */
const val PROTOCOL_VERSION = 2

/**
 * UDP command port used only when a version-2 pair response omits `udp_port`.
 * A port present in the response always wins.
 */
const val DEFAULT_UDP_PORT = 12346

@Serializable
data class FingerprintDto(
    @SerialName("android_id") val androidId: String,
    val model: String,
    val manufacturer: String,
    @SerialName("os_version") val osVersion: String,
)

@Serializable
data class PairingRequestDto(
    @SerialName("protocol_version") val protocolVersion: Int = PROTOCOL_VERSION,
    @SerialName("pairing_mode") val pairingMode: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_name") val clientName: String,
    val fingerprint: FingerprintDto,
    @SerialName("verification_code") val verificationCode: String? = null,
)

@Serializable
data class ServerInfoDto(
    val name: String? = null,
    val version: String? = null,
)

@Serializable
data class PairingResponseDto(
    val paired: Boolean,
    @SerialName("protocol_version") val protocolVersion: Int? = null,
    @SerialName("session_id") val sessionId: Int? = null,
    @SerialName("udp_port") val udpPort: Int? = null,
    @SerialName("server_info") val serverInfo: ServerInfoDto? = null,
)

@Serializable
data class StatusResponseDto(
    val version: String? = null,
    @SerialName("uptime_seconds") val uptimeSeconds: Int? = null,
    @SerialName("connected_clients") val connectedClients: Int? = null,
    @SerialName("max_clients") val maxClients: Int? = null,
    @SerialName("is_pairing_enabled") val isPairingEnabled: Boolean? = null,
)

@Serializable
data class ErrorResponseDto(
    val error: String? = null,
    val message: String? = null,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null,
)

@Serializable
data class DiscoveryRequestDto(
    val type: String = "discovery",
    @SerialName("client_id") val clientId: String,
    @SerialName("target_device_id") val targetDeviceId: String? = null,
)

@Serializable
data class DiscoveryResponseDto(
    val type: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    val ip: String,
    val port: Int = 12345,
    @SerialName("is_trusted") val isTrusted: Boolean = false,
    @SerialName("requires_pairing") val requiresPairing: Boolean = true,
)

@Serializable
data class WsEnvelope(
    val type: String,
    // Keep extra fields as flexible map via JsonObject parsing at call sites when needed
)

@Serializable
data class MediaUpdateDto(
    val type: String = "media_update",
    val title: String? = null,
    val artist: String? = null,
    @SerialName("position_seconds") val positionSeconds: Int = 0,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("can_seek") val canSeek: Boolean = false,
)

@Serializable
data class VolumeUpdateDto(
    val type: String = "volume_update",
    val level: Int = 0,
    val muted: Boolean = false,
)

@Serializable
data class TextUpdateDto(
    val type: String = "text_update",
    val text: String = "",
)

@Serializable
data class ContextChangeDto(
    val type: String = "context_change",
    val context: String,
)

@Serializable
data class SaveSuccessDto(
    val type: String = "save-success",
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class WsErrorDto(
    val type: String = "error",
    val code: Int = 0,
    val message: String = "",
)
