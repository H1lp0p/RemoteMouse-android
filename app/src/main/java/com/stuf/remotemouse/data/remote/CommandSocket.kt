package com.stuf.remotemouse.data.remote

import com.stuf.remotemouse.data.remote.dto.ContextChangeDto
import com.stuf.remotemouse.data.remote.dto.MediaUpdateDto
import com.stuf.remotemouse.data.remote.dto.SaveSuccessDto
import com.stuf.remotemouse.data.remote.dto.TextUpdateDto
import com.stuf.remotemouse.data.remote.dto.VolumeUpdateDto
import com.stuf.remotemouse.data.remote.dto.WsErrorDto
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
    private var moveFlushJob: Job? = null
    private val intentionalClose = AtomicBoolean(false)
    private val moveLock = Any()
    private var pendingMoveDx = 0
    private var pendingMoveDy = 0
    /** Last mouse_move RTT (ms) measured on this device via mouse_move_ack. Debug only. */
    @Volatile private var lastMouseMoveRttMs: Long? = null

    private val _state = MutableStateFlow(WsConnectionState.Disconnected)
    val state: StateFlow<WsConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    fun connect(url: String) {
        disconnect()
        intentionalClose.set(false)
        _state.value = WsConnectionState.Connecting
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        intentionalClose.set(true)
        stopMoveBatching(sendPending = false)
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
     * Accumulates move deltas and flushes ~every [MOVE_BATCH_INTERVAL_MS].
     * Call [flushMouseMove] before clicks/scroll so the cursor is up to date.
     */
    fun mouseMove(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        synchronized(moveLock) {
            pendingMoveDx += dx
            pendingMoveDy += dy
        }
        ensureMoveFlushLoop()
    }

    /** Replaces the pending move vector (used by stick velocity from center). */
    fun setMouseMoveVelocity(dx: Int, dy: Int) {
        synchronized(moveLock) {
            pendingMoveDx = dx
            pendingMoveDy = dy
        }
        if (dx != 0 || dy != 0) {
            ensureMoveFlushLoop()
        }
    }

    fun flushMouseMove() {
        val (dx, dy) = synchronized(moveLock) {
            val pair = pendingMoveDx to pendingMoveDy
            pendingMoveDx = 0
            pendingMoveDy = 0
            pair
        }
        if (dx == 0 && dy == 0) return
        sendJson(
            buildJsonObject {
                put("type", "mouse_move")
                put("dx", dx)
                put("dy", dy)
                // Not in AsyncAPI — debug only; strip by flipping [DEBUG_MOUSE_MOVE_LATENCY].
                if (DEBUG_MOUSE_MOVE_LATENCY) {
                    put("timestamp", System.currentTimeMillis())
                    lastMouseMoveRttMs?.let { put("prev_rtt_ms", it) }
                }
            },
        )
    }

    fun mouseClick(button: String, action: String): Boolean {
        flushMouseMove()
        return sendJson(
            buildJsonObject {
                put("type", "mouse_click")
                put("button", button)
                put("action", action)
            },
        )
    }

    fun mouseScroll(deltaY: Int, deltaX: Int = 0): Boolean {
        flushMouseMove()
        return sendJson(
            buildJsonObject {
                put("type", "mouse_scroll")
                put("delta_x", deltaX)
                put("delta_y", deltaY)
            },
        )
    }

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

    fun volumeSet(level: Int) = sendJson(
        buildJsonObject {
            put("type", "volume_set")
            put("level", level)
        },
    )

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
    fun mediaSeek(seconds: Int) = sendJson(
        buildJsonObject {
            put("type", "media_seek")
            put("seconds", seconds)
        },
    )

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

    private fun ensureMoveFlushLoop() {
        if (moveFlushJob?.isActive == true) return
        moveFlushJob = scope.launch {
            while (isActive && _state.value == WsConnectionState.Connected) {
                delay(MOVE_BATCH_INTERVAL_MS)
                flushMouseMove()
                val idle = synchronized(moveLock) {
                    pendingMoveDx == 0 && pendingMoveDy == 0
                }
                if (idle) break
            }
            moveFlushJob = null
        }
    }

    private fun stopMoveBatching(sendPending: Boolean) {
        moveFlushJob?.cancel()
        moveFlushJob = null
        if (sendPending) {
            flushMouseMove()
        } else {
            synchronized(moveLock) {
                pendingMoveDx = 0
                pendingMoveDy = 0
            }
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _state.value = WsConnectionState.Connected
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { handleMessage(text) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            stopMoveBatching(sendPending = false)
            heartbeatJob?.cancel()
            _state.value = WsConnectionState.Disconnected
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            stopMoveBatching(sendPending = false)
            heartbeatJob?.cancel()
            _state.value = WsConnectionState.Disconnected
            if (!intentionalClose.get()) {
                scope.launch {
                    _events.emit(ServerEvent.Error(-1, t.message ?: "WebSocket failure"))
                }
            }
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
            // Debug-only ack for mouse_move RTT (not in AsyncAPI).
            "mouse_move_ack" -> {
                if (!DEBUG_MOUSE_MOVE_LATENCY) return
                val ts = obj["timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return
                lastMouseMoveRttMs = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
            }
            "error" -> {
                val dto = json.decodeFromJsonElement(WsErrorDto.serializer(), obj)
                _events.emit(ServerEvent.Error(dto.code, dto.message))
            }
            else -> _events.emit(ServerEvent.Raw(type, text))
        }
    }

    companion object {
        /** Accumulate mouse deltas and flush about once per frame (~16ms). */
        const val MOVE_BATCH_INTERVAL_MS = 16L

        /**
         * Debug: measure mouse_move RTT via timestamp + stub `mouse_move_ack`.
         * Also piggybacks `prev_rtt_ms` on the next move so the stub can log real RTT.
         * Not part of AsyncAPI — set to `false` to disable.
         */
        const val DEBUG_MOUSE_MOVE_LATENCY = true
    }
}
