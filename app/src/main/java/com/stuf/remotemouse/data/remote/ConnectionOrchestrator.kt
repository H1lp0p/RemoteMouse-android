package com.stuf.remotemouse.data.remote

import com.stuf.remotemouse.data.identity.IdentityRepository
import com.stuf.remotemouse.data.local.repo.DeviceRepository
import com.stuf.remotemouse.data.remote.dto.ErrorResponseDto
import com.stuf.remotemouse.data.remote.dto.FingerprintDto
import com.stuf.remotemouse.data.remote.dto.PairingRequestDto
import com.stuf.remotemouse.domain.ActiveSession
import com.stuf.remotemouse.domain.PairingException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.Retrofit

@Singleton
class ConnectionOrchestrator @Inject constructor(
    private val retrofitBuilder: Retrofit.Builder,
    private val discoveryClient: DiscoveryClient,
    private val commandSocket: CommandSocket,
    private val identityRepository: IdentityRepository,
    private val deviceRepository: DeviceRepository,
    private val json: Json,
) {
    private val _session = MutableStateFlow<ActiveSession?>(null)
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    val wsState = commandSocket.state
    val events = commandSocket.events

    suspend fun pairNew(
        deviceId: String,
        deviceName: String,
        ip: String,
        port: Int,
        verificationCode: String,
    ): ActiveSession {
        val me = identityRepository.ensureMe()
        val api = apiFor(ip, port)
        val body = PairingRequestDto(
            pairingMode = "new",
            deviceId = deviceId,
            clientId = me.clientId,
            clientName = me.clientName,
            fingerprint = me.toFingerprintDto(),
            verificationCode = verificationCode,
        )
        executePair(api, body)
        commandSocket.connect(wsUrl(ip, port))
        val active = ActiveSession(deviceId, deviceName, ip, port)
        _session.value = active
        return active
    }

    suspend fun pairSaved(deviceId: String): ActiveSession {
        val me = identityRepository.ensureMe()
        val saved = deviceRepository.getById(deviceId)
            ?: throw PairingException.Unexpected("Device not in local storage")

        var ip = saved.lastIp
        var port = saved.port
        val reachable = runCatching { apiFor(ip, port).status().isSuccessful }.getOrDefault(false)
        if (!reachable) {
            val resolved = discoveryClient.resolveDevice(me.clientId, deviceId)
                ?: throw PairingException.Network("Could not rediscover device via UDP")
            ip = resolved.ip
            port = resolved.port
        }

        val api = apiFor(ip, port)
        val body = PairingRequestDto(
            pairingMode = "saved",
            deviceId = deviceId,
            clientId = me.clientId,
            clientName = me.clientName,
            fingerprint = me.toFingerprintDto(),
        )
        try {
            executePair(api, body)
        } catch (e: PairingException.DeviceNotTrusted) {
            deviceRepository.delete(deviceId)
            throw e
        }

        commandSocket.connect(wsUrl(ip, port))
        deviceRepository.touch(deviceId, ip, port)
        val active = ActiveSession(deviceId, saved.name, ip, port)
        _session.value = active
        return active
    }

    suspend fun saveCurrentDevice() {
        val session = _session.value ?: throw PairingException.Unexpected("No active session")
        val me = identityRepository.ensureMe()
        if (!commandSocket.save(me.clientName)) {
            throw PairingException.Network("WebSocket is not connected")
        }
        val event = events.first { it is ServerEvent.SaveSuccess || it is ServerEvent.Error }
        when (event) {
            is ServerEvent.SaveSuccess -> {
                deviceRepository.upsert(
                    deviceId = event.deviceId,
                    name = event.deviceName.ifBlank { session.deviceName },
                    lastIp = session.ip,
                    port = session.port,
                )
                _session.value = session.copy(
                    deviceId = event.deviceId,
                    deviceName = event.deviceName.ifBlank { session.deviceName },
                )
            }
            is ServerEvent.Error -> throw PairingException.Unexpected(event.message)
            else -> Unit
        }
    }

    fun disconnect() {
        commandSocket.disconnect()
        _session.value = null
    }

    fun socket(): CommandSocket = commandSocket

    private fun apiFor(ip: String, port: Int): PairingApi =
        retrofitBuilder
            .baseUrl(baseHttpUrl(ip, port))
            .build()
            .create(PairingApi::class.java)

    private suspend fun executePair(api: PairingApi, body: PairingRequestDto) {
        val response = try {
            api.pair(body)
        } catch (e: Exception) {
            throw PairingException.Network(e.message ?: "Pair request failed")
        }
        if (response.isSuccessful) return

        val errorBody = response.errorBody()?.string().orEmpty()
        val error = runCatching {
            json.decodeFromString(ErrorResponseDto.serializer(), errorBody)
        }.getOrNull()

        when (response.code()) {
            401 -> throw PairingException.DeviceNotTrusted(error?.message ?: "device_not_trusted")
            403 -> throw PairingException.PairingBlocked(error?.message ?: "pairing blocked")
            429 -> throw PairingException.RateLimited(
                retryAfter = error?.retryAfterSeconds
                    ?: response.headers()["Retry-After"]?.toIntOrNull(),
                message = error?.message ?: "rate limited",
            )
            else -> {
                if (error?.error == "invalid_code") {
                    throw PairingException.InvalidCode(error.message ?: "invalid code")
                }
                throw PairingException.Unexpected(
                    error?.message ?: "Pair failed: HTTP ${response.code()}",
                )
            }
        }
    }

    private fun com.stuf.remotemouse.data.local.entity.MeEntity.toFingerprintDto() =
        FingerprintDto(
            androidId = androidId,
            model = model,
            manufacturer = manufacturer,
            osVersion = osVersion,
        )
}
