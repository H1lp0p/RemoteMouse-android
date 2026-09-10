package com.stuf.remotemouse.data.remote

import com.stuf.remotemouse.data.remote.dto.DiscoveryRequestDto
import com.stuf.remotemouse.data.remote.dto.DiscoveryResponseDto
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class DiscoveryClient @Inject constructor(
    private val json: Json,
) {
    suspend fun discover(
        clientId: String,
        targetDeviceId: String? = null,
        port: Int = DEFAULT_PORT,
        timeoutMs: Int = 2500,
    ): List<DiscoveryResponseDto> = withContext(Dispatchers.IO) {
        val request = DiscoveryRequestDto(
            clientId = clientId,
            targetDeviceId = targetDeviceId,
        )
        val payload = json.encodeToString(DiscoveryRequestDto.serializer(), request)
            .toByteArray(Charsets.UTF_8)

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 400
            val broadcast = InetAddress.getByName("255.255.255.255")
            socket.send(DatagramPacket(payload, payload.size, broadcast, port))

            val found = LinkedHashMap<String, DiscoveryResponseDto>()
            val buffer = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val response = runCatching {
                        json.decodeFromString(DiscoveryResponseDto.serializer(), text)
                    }.getOrNull() ?: continue
                    if (response.type != "discovery_response") continue
                    if (targetDeviceId != null && response.deviceId != targetDeviceId) continue
                    val ip = response.ip.ifBlank { packet.address.hostAddress ?: continue }
                    found[response.deviceId] = response.copy(ip = ip)
                } catch (_: SocketTimeoutException) {
                    // keep waiting until deadline
                }
            }
            found.values.toList()
        }
    }

    suspend fun resolveDevice(
        clientId: String,
        deviceId: String,
        port: Int = DEFAULT_PORT,
    ): DiscoveryResponseDto? =
        discover(clientId = clientId, targetDeviceId = deviceId, port = port).firstOrNull()

    companion object {
        const val DEFAULT_PORT = 12345
    }
}

fun baseHttpUrl(ip: String, port: Int): String = "http://$ip:$port/"

fun wsUrl(ip: String, port: Int): String = "ws://$ip:$port/ws"
