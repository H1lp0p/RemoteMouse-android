package com.stuf.remotemouse.data.remote

import com.stuf.remotemouse.data.identity.IdentityRepository
import com.stuf.remotemouse.data.local.repo.ActionRepository
import com.stuf.remotemouse.data.local.repo.DeviceRepository
import com.stuf.remotemouse.data.settings.CursorSensitivity
import com.stuf.remotemouse.data.remote.dto.DEFAULT_UDP_PORT
import com.stuf.remotemouse.data.remote.dto.ErrorResponseDto
import com.stuf.remotemouse.data.remote.dto.FingerprintDto
import com.stuf.remotemouse.data.remote.dto.PROTOCOL_VERSION
import com.stuf.remotemouse.data.remote.dto.PairingRequestDto
import com.stuf.remotemouse.data.remote.dto.PairingResponseDto
import com.stuf.remotemouse.domain.ActiveSession
import com.stuf.remotemouse.domain.PairingException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.Retrofit

@Singleton
class ConnectionOrchestrator @Inject constructor(
    private val retrofitBuilder: Retrofit.Builder,
    private val discoveryClient: DiscoveryClient,
    private val commandSocket: CommandSocket,
    private val identityRepository: IdentityRepository,
    private val deviceRepository: DeviceRepository,
    private val actionRepository: ActionRepository,
    private val cursorSensitivity: CursorSensitivity,
    private val json: Json,
) {
    private val _session = MutableStateFlow<ActiveSession?>(null)
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    val wsState = commandSocket.state
    val events = commandSocket.events

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var inForeground = false

    init {
        scope.launch {
            commandSocket.remoteClosed.collect { closedByPeer ->
                if (_session.value != null && (closedByPeer || inForeground)) {
                    _session.value = null
                }
            }
        }
    }

    fun setInForeground(foreground: Boolean) {
        inForeground = foreground
    }

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
            protocolVersion = PROTOCOL_VERSION,
            pairingMode = "new",
            deviceId = deviceId,
            clientId = me.clientId,
            clientName = me.clientName,
            fingerprint = me.toFingerprintDto(),
            verificationCode = verificationCode,
        )
        val paired = executePair(api, body)
        openSocket(ip, port, paired.sessionId, paired.udpPort)
        actionRepository.ensureDefaultActions(deviceId)
        val active = ActiveSession(
            deviceId = deviceId,
            deviceName = deviceName,
            ip = ip,
            port = port,
            sessionId = paired.sessionId,
            udpPort = paired.udpPort,
        )
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
            protocolVersion = PROTOCOL_VERSION,
            pairingMode = "saved",
            deviceId = deviceId,
            clientId = me.clientId,
            clientName = me.clientName,
            fingerprint = me.toFingerprintDto(),
        )
        val paired = try {
            executePair(api, body)
        } catch (e: PairingException.DeviceNotTrusted) {
            deviceRepository.delete(deviceId)
            throw e
        }

        openSocket(ip, port, paired.sessionId, paired.udpPort)
        deviceRepository.touch(deviceId, ip, port)
        actionRepository.ensureDefaultActions(deviceId)
        val active = ActiveSession(
            deviceId = deviceId,
            deviceName = saved.name,
            ip = ip,
            port = port,
            sessionId = paired.sessionId,
            udpPort = paired.udpPort,
        )
        _session.value = active
        return active
    }

    /**
     * Reconnects the command WebSocket if a session is active but the socket dropped
     * (e.g. after the app was backgrounded). Does not clear the session on failure.
     */
    fun ensureSocketConnected() {
        val session = _session.value ?: return
        when (commandSocket.state.value) {
            WsConnectionState.Connected, WsConnectionState.Connecting -> return
            WsConnectionState.Disconnected -> runCatching {
                openSocket(session.ip, session.port, session.sessionId, session.udpPort)
            }
        }
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
                    cursorSensitivity = cursorSensitivity.coefficient.value,
                )
                actionRepository.ensureDefaultActions(event.deviceId)
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

    private fun openSocket(ip: String, port: Int, sessionId: Int, udpPort: Int) {
        try {
            commandSocket.connect(
                url = wsUrl(ip, port),
                udpHost = ip,
                udpPort = udpPort,
                sessionId = sessionId,
            )
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            throw PairingException.Network("Failed to open UDP: $detail")
        }
    }

    private fun apiFor(ip: String, port: Int): PairingApi =
        retrofitBuilder
            .baseUrl(baseHttpUrl(ip, port))
            .build()
            .create(PairingApi::class.java)

    private suspend fun executePair(api: PairingApi, body: PairingRequestDto): V2Pair {
        val response = try {
            api.pair(body)
        } catch (e: Exception) {
            throw PairingException.Network(e.message ?: "Pair request failed")
        }
        if (response.isSuccessful) {
            return readV2Pair(response.body())
        }

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
                    error?.message ?: error?.error ?: "Pair failed: HTTP ${response.code()}",
                )
            }
        }
    }

    private fun readV2Pair(body: PairingResponseDto?): V2Pair {
        val parsed = body ?: throw PairingException.Unexpected("Empty pair response")
        if (parsed.protocolVersion != PROTOCOL_VERSION) {
            val reported = parsed.protocolVersion?.toString() ?: "missing"
            throw PairingException.Unexpected("Server protocol version $reported is not supported")
        }
        val sessionId = parsed.sessionId
            ?: throw PairingException.Unexpected("Pair response missing session_id")
        if (sessionId !in 0..0xFFFF) {
            throw PairingException.Unexpected("Pair response session_id out of range")
        }
        val udpPort = parsed.udpPort ?: DEFAULT_UDP_PORT
        if (udpPort !in 1..65535) {
            throw PairingException.Unexpected("Pair response udp_port out of range")
        }
        return V2Pair(sessionId = sessionId, udpPort = udpPort)
    }

    private fun com.stuf.remotemouse.data.local.entity.MeEntity.toFingerprintDto() =
        FingerprintDto(
            androidId = androidId,
            model = model,
            manufacturer = manufacturer,
            osVersion = osVersion,
        )
}

private data class V2Pair(
    val sessionId: Int,
    val udpPort: Int,
)
