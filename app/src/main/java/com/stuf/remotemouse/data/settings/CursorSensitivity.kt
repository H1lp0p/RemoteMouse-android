package com.stuf.remotemouse.data.settings

import com.stuf.remotemouse.data.local.entity.PcDeviceEntity
import com.stuf.remotemouse.data.local.repo.DeviceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live cursor multiplier for the active PC.
 * Saved devices persist it; an unsaved session keeps the value until disconnect or save.
 */
@Singleton
class CursorSensitivity @Inject constructor(
    private val deviceRepository: DeviceRepository,
) {
    private val _coefficient = MutableStateFlow(PcDeviceEntity.DEFAULT_CURSOR_SENSITIVITY)
    val coefficient: StateFlow<Float> = _coefficient.asStateFlow()

    private var attachedDeviceId: String? = null

    suspend fun attach(deviceId: String?) {
        if (deviceId == attachedDeviceId) return
        attachedDeviceId = deviceId
        _coefficient.value = if (deviceId == null) {
            PcDeviceEntity.DEFAULT_CURSOR_SENSITIVITY
        } else {
            deviceRepository.getById(deviceId)?.cursorSensitivity
                ?: PcDeviceEntity.DEFAULT_CURSOR_SENSITIVITY
        }
    }

    fun setLocal(value: Float) {
        _coefficient.value = value.coerceIn(MIN, MAX)
    }

    /** Scaled deltas. Whole pixels are taken from the fractional remainder when a move is sent. */
    fun scale(dx: Float, dy: Float): Pair<Float, Float> {
        val sens = _coefficient.value
        return dx * sens to dy * sens
    }

    companion object {
        const val MIN = 0.25f
        const val MAX = 3f
    }
}
