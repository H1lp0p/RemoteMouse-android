package com.stuf.remotemouse.data.remote

import android.os.SystemClock
import com.stuf.remotemouse.data.remote.dto.ContextChangeDto
import com.stuf.remotemouse.data.remote.dto.MediaUpdateDto
import com.stuf.remotemouse.data.remote.dto.SaveSuccessDto
import com.stuf.remotemouse.data.remote.dto.TextUpdateDto
import com.stuf.remotemouse.data.remote.dto.VolumeUpdateDto
import com.stuf.remotemouse.data.remote.dto.WsErrorDto
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

sealed interface ServerEvent {
    data class SaveSuccess(val deviceId: String, val deviceName: String) : ServerEvent
    data class ContextChange(val context: String) : ServerEvent
    data class MediaUpdate(val data: MediaUpdateDto) : ServerEvent
    data class VolumeUpdate(val data: VolumeUpdateDto) : ServerEvent
    data class TextUpdate(val text: String) : ServerEvent
    data class HeartbeatAck(val timestamp: Long?) : ServerEvent
    data class Error(val code: Int, val message: String) : ServerEvent
    data class Raw(val type: String, val json: String) : ServerEvent
}

enum class WsConnectionState {
    Disconnected,
    Connecting,
    Connected,
}

@Singleton
class CommandSocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val intentionalClose = AtomicBoolean(false)
    private var activeGeneration = 0
    private val _remoteClosed = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    /**
     * Current socket died and this client did not call [disconnect].
     * `true` — peer sent a close frame. `false` — transport failure.
     */
    val remoteClosed: SharedFlow<Boolean> = _remoteClosed.asSharedFlow()

    private val ioLock = Any()
    /** One thread so datagrams leave in gesture order and never on the main thread. */
    private val udpSender = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "remote-mouse-udp").apply { isDaemon = true }
    }
    private var udpSocket: DatagramSocket? = null
    private var remoteAddress: InetSocketAddress? = null
    private var sessionId: Int = 0
    /** Sub-pixel remainder shared by the touchpad and the stick. It is not a send batch. */
    private var remainderDx = 0f
    private var remainderDy = 0f
    /** Timestamp of the last mouse datagram. Scroll and click fence on this, not on "now". */
    private var lastMouseTimestamp: Long? = null

    private val _state = MutableStateFlow(WsConnectionState.Disconnected)
    val state: StateFlow<WsConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    fun connect(url: String, udpHost: String, udpPort: Int, sessionId: Int) {
        disconnect()
        val generation = ++activeGeneration
        intentionalClose.set(false)
        synchronized(ioLock) {
            this.sessionId = sessionId and 0xFFFF
        }
        try {
            openUdp(udpHost, udpPort)
            _state.value = WsConnectionState.Connecting
            val request = Request.Builder().url(url).build()
            webSocket = okHttpClient.newWebSocket(request, SocketListener(generation))
        } catch (t: Throwable) {
            closeUdp()
            _state.value = WsConnectionState.Disconnected
            throw t
        }
    }

    fun disconnect() {
        intentionalClose.set(true)
        closeUdp()
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        _state.value = WsConnectionState.Disconnected
    }

    fun sendJson(obj: JsonObject): Boolean {
        val ws = webSocket ?: return false
        return ws.send(obj.toString())
    }

    fun save(clientName: String) = sendJson(
        buildJsonObject {
            put("type", "save")
            put("client_name", clientName)
        },
    )

    /**
     * Touchpad sample. Adds the already-scaled delta to the fractional remainder,
     * then sends one mouse datagram if at least one whole pixel is available.
     */
    fun mouseMove(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        synchronized(ioLock) {
            remainderDx += dx
            remainderDy += dy
            sendPendingMouse()
        }
    }

    /**
     * One stick tick. The caller integrates velocity on its own ~16 ms loop.
     * Uses the same remainder as [mouseMove]. A zero delta leaves that remainder in place.
     */
    fun setMouseMoveVelocity(dx: Float, dy: Float) {
        mouseMove(dx, dy)
    }

    fun mouseClick(button: String, action: String): Boolean {
        val inputTime = inputTimeAfterFlush()
        return sendJson(
            buildJsonObject {
                put("type", "mouse_click")
                put("button", button)
                put("action", action)
                if (inputTime != null) put("input_time", inputTime)
            },
        )
    }

    fun mouseScroll(deltaY: Int, deltaX: Int = 0): Boolean = sendJson(
        buildJsonObject {
            put("type", "mouse_scroll")
            put("delta_x", deltaX)
            put("delta_y", deltaY)
        },
    )

    fun textInput(text: String) = sendJson(
        buildJsonObject {
            put("type", "text_input")
            put("text", text)
        },
    )

    /** Full shared text-field sync (Text control). */
    fun textSet(text: String) = sendJson(
        buildJsonObject {
            put("type", "text_set")
            put("text", text)
        },
    )

    fun textSubscribe() = sendJson(buildJsonObject { put("type", "text_subscribe") })
    fun textUnsubscribe() = sendJson(buildJsonObject { put("type", "text_unsubscribe") })

    fun keySequence(keys: List<Int>) = sendJson(
        buildJsonObject {
            put("type", "key_sequence")
            putJsonArray("keys") {
                keys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
        },
    )

    /** Absolute level 0..100 over UDP. */
    fun volumeSet(level: Int): Boolean = synchronized(ioLock) {
        val clamped = level.coerceIn(0, 100)
        writeDatagram(OPCODE_VOLUME, 1) {
            put(clamped.toByte())
        } != null
    }

    fun volumeStep(direction: String) = sendJson(
        buildJsonObject {
            put("type", "volume_step")
            put("direction", direction)
        },
    )

    fun volumeMute(mute: Boolean) = sendJson(
        buildJsonObject {
            put("type", "volume_mute")
            put("mute", mute)
        },
    )

    fun mediaPlayPause() = sendJson(buildJsonObject { put("type", "media_play_pause") })
    fun mediaNext() = sendJson(buildJsonObject { put("type", "media_next") })
    fun mediaPrev() = sendJson(buildJsonObject { put("type", "media_prev") })

    /** Relative seek, seconds. WebSocket. */
    fun mediaSeek(seconds: Int) = sendJson(
        buildJsonObject {
            put("type", "media_seek")
            put("seconds", seconds)
        },
    )

    /** Absolute playback position, seconds. UDP. */
    fun seekAbsolute(positionSeconds: Int): Boolean = synchronized(ioLock) {
        val pos = positionSeconds.coerceIn(0, 0xFFFF)
        writeDatagram(OPCODE_SEEK, 2) {
            putShort(pos.toShort())
        } != null
    }

    fun mediaSubscribe() = sendJson(buildJsonObject { put("type", "media_subscribe") })
    fun mediaUnsubscribe() = sendJson(buildJsonObject { put("type", "media_unsubscribe") })

    fun heartbeat(timestamp: Long = System.currentTimeMillis()) = sendJson(
        buildJsonObject {
            put("type", "heartbeat")
            put("timestamp", timestamp)
        },
    )

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _state.value == WsConnectionState.Connected) {
                heartbeat()
                delay(5_000)
            }
        }
    }

    private fun openUdp(host: String, port: Int) {
        val address = InetAddress.getByName(host) ?: throw UnknownHostException(host)
        val remote = InetSocketAddress(address, port)
        // Bind an IPv4 socket and do not call connect(). On Android, connect() on an
        // unbound dual-stack datagram socket throws with an empty message.
        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
        } catch (t: Throwable) {
            socket.close()
            throw t
        }
        synchronized(ioLock) {
            udpSocket?.close()
            udpSocket = socket
            remoteAddress = remote
        }
    }

    private fun closeUdp() {
        synchronized(ioLock) {
            udpSocket?.close()
            udpSocket = null
            remoteAddress = null
            remainderDx = 0f
            remainderDy = 0f
            lastMouseTimestamp = null
        }
    }

    /**
     * Flushes a pending mouse datagram, then returns the timestamp of the last one sent.
     * A pure scroll has no newer mouse sample, so the PC must not wait for "now".
     */
    private fun inputTimeAfterFlush(): Long? = synchronized(ioLock) {
        sendPendingMouse()
        lastMouseTimestamp
    }

    /** Caller holds [ioLock]. Sends at most one mouse datagram. */
    private fun sendPendingMouse(): Boolean {
        if (udpSocket == null) return false
        val (dx, dy) = takeWholePixels()
        if (dx == 0 && dy == 0) return false
        val stamped = writeDatagram(OPCODE_MOUSE_MOVE, 4) {
            putShort(dx.toShort())
            putShort(dy.toShort())
        } ?: return false
        lastMouseTimestamp = stamped
        return true
    }

    /**
     * Caller holds [ioLock].
     * Truncates toward zero so the fractional part stays on [remainderDx] / [remainderDy].
     */
    private fun takeWholePixels(): Pair<Int, Int> {
        val ix = remainderDx.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        val iy = remainderDy.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        if (ix == 0 && iy == 0) return 0 to 0
        remainderDx -= ix
        remainderDy -= iy
        return ix to iy
    }

    /**
     * Caller holds [ioLock]. Builds the frame here and hands the send to [udpSender].
     * Header is opcode, session id, elapsedRealtime low 32, little-endian.
     */
    private fun writeDatagram(
        opcode: Int,
        payloadSize: Int,
        writePayload: ByteBuffer.() -> Unit,
    ): Long? {
        val socket = udpSocket ?: return null
        val remote = remoteAddress ?: return null
        val address = remote.address ?: return null
        val port = remote.port
        val timestamp = elapsedTimestamp()
        val buf = ByteBuffer.allocate(HEADER_SIZE + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(opcode.toByte())
        buf.putShort(sessionId.toShort())
        buf.putInt(timestamp.toInt())
        buf.writePayload()
        check(buf.position() == buf.capacity())
        val data = buf.array()
        udpSender.execute {
            try {
                socket.send(DatagramPacket(data, data.size, address, port))
            } catch (_: IOException) {
                // The socket was closed or the network dropped this datagram.
            }
        }
        return timestamp
    }

    private fun elapsedTimestamp(): Long = SystemClock.elapsedRealtime() and 0xFFFFFFFFL

    private inner class SocketListener(
        private val generation: Int,
    ) : WebSocketListener() {
        private fun isCurrent(): Boolean = generation == activeGeneration

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isCurrent()) return
            _state.value = WsConnectionState.Connected
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent()) return
            scope.launch { handleMessage(text) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent()) return
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent()) return
            markSocketGone(closedByPeer = true)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent()) return
            markSocketGone(closedByPeer = false)
            if (!intentionalClose.get()) {
                scope.launch {
                    _events.emit(ServerEvent.Error(-1, t.message ?: "WebSocket failure"))
                }
            }
        }
    }

    private fun markSocketGone(closedByPeer: Boolean) {
        closeUdp()
        heartbeatJob?.cancel()
        heartbeatJob = null
        _state.value = WsConnectionState.Disconnected
        if (!intentionalClose.get()) {
            _remoteClosed.tryEmit(closedByPeer)
        }
    }

    private suspend fun handleMessage(text: String) {
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return
        val obj = element as? JsonObject ?: return
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return
        when (type) {
            "save-success" -> {
                val dto = json.decodeFromJsonElement(SaveSuccessDto.serializer(), obj)
                _events.emit(ServerEvent.SaveSuccess(dto.deviceId, dto.deviceName))
            }
            "context_change" -> {
                val dto = json.decodeFromJsonElement(ContextChangeDto.serializer(), obj)
                _events.emit(ServerEvent.ContextChange(dto.context))
            }
            "media_update" -> {
                val dto = json.decodeFromJsonElement(MediaUpdateDto.serializer(), obj)
                _events.emit(ServerEvent.MediaUpdate(dto))
            }
            "volume_update" -> {
                val dto = json.decodeFromJsonElement(VolumeUpdateDto.serializer(), obj)
                _events.emit(ServerEvent.VolumeUpdate(dto))
            }
            "text_update" -> {
                val dto = json.decodeFromJsonElement(TextUpdateDto.serializer(), obj)
                _events.emit(ServerEvent.TextUpdate(dto.text))
            }
            "heartbeat_ack" -> {
                val ts = obj["timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                _events.emit(ServerEvent.HeartbeatAck(ts))
            }
            "error" -> {
                val dto = json.decodeFromJsonElement(WsErrorDto.serializer(), obj)
                _events.emit(ServerEvent.Error(dto.code, dto.message))
            }
            else -> _events.emit(ServerEvent.Raw(type, text))
        }
    }

    companion object {
        private const val HEADER_SIZE = 7
        private const val OPCODE_MOUSE_MOVE = 0x01
        private const val OPCODE_VOLUME = 0x02
        private const val OPCODE_SEEK = 0x03
    }
}
