package com.stuf.remotemouse.data.local.repo

import com.stuf.remotemouse.data.local.dao.ActionDao
import com.stuf.remotemouse.data.local.dao.ActionPadDao
import com.stuf.remotemouse.data.local.dao.ActionStepDao
import com.stuf.remotemouse.data.local.dao.PcDeviceDao
import com.stuf.remotemouse.data.local.entity.ACTION_PAD_SLOT_COUNT
import com.stuf.remotemouse.data.local.entity.ActionEntity
import com.stuf.remotemouse.data.local.entity.ActionPadSlotEntity
import com.stuf.remotemouse.data.local.entity.ActionStepEntity
import com.stuf.remotemouse.data.local.entity.ActionWithSteps
import com.stuf.remotemouse.data.local.entity.PcDeviceEntity
import com.stuf.remotemouse.domain.VkCodes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DeviceRepository @Inject constructor(
    private val pcDeviceDao: PcDeviceDao,
) {
    fun observeDevices(): Flow<List<PcDeviceEntity>> = pcDeviceDao.observeAll()

    suspend fun getAll(): List<PcDeviceEntity> = pcDeviceDao.getAll()

    suspend fun getById(deviceId: String): PcDeviceEntity? = pcDeviceDao.getById(deviceId)

    suspend fun upsert(
        deviceId: String,
        name: String,
        lastIp: String,
        port: Int = 12345,
        cursorSensitivity: Float? = null,
    ) {
        val existing = pcDeviceDao.getById(deviceId)
        pcDeviceDao.upsert(
            PcDeviceEntity(
                deviceId = deviceId,
                name = name,
                lastIp = lastIp,
                port = port,
                lastUsedAt = System.currentTimeMillis(),
                cursorSensitivity = cursorSensitivity
                    ?: existing?.cursorSensitivity
                    ?: PcDeviceEntity.DEFAULT_CURSOR_SENSITIVITY,
            ),
        )
    }

    suspend fun setCursorSensitivity(deviceId: String, value: Float) {
        val existing = pcDeviceDao.getById(deviceId) ?: return
        if (existing.cursorSensitivity == value) return
        pcDeviceDao.upsert(existing.copy(cursorSensitivity = value))
    }

    suspend fun touch(deviceId: String, lastIp: String, port: Int) {
        val existing = pcDeviceDao.getById(deviceId) ?: return
        pcDeviceDao.upsert(
            existing.copy(
                lastIp = lastIp,
                port = port,
                lastUsedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(deviceId: String) {
        pcDeviceDao.delete(deviceId)
    }
}

@Singleton
class ActionRepository @Inject constructor(
    private val actionDao: ActionDao,
    private val actionStepDao: ActionStepDao,
    private val actionPadDao: ActionPadDao,
) {
    fun observeActions(deviceId: String): Flow<List<ActionEntity>> =
        actionDao.observeByDevice(deviceId)

    fun observeActionsWithSteps(deviceId: String): Flow<List<ActionWithSteps>> =
        actionDao.observeByDeviceWithSteps(deviceId)

    fun observeSteps(actionId: Long): Flow<List<ActionStepEntity>> =
        actionStepDao.observeByAction(actionId)

    fun observePadSlots(deviceId: String): Flow<List<Long?>> =
        actionPadDao.observe(deviceId).map { rows ->
            val bySlot = rows.associate { it.slot to it.actionId }
            List(ACTION_PAD_SLOT_COUNT) { index -> bySlot[index] }
        }

    suspend fun setPadSlot(deviceId: String, slot: Int, actionId: Long?) {
        if (slot !in 0 until ACTION_PAD_SLOT_COUNT) return
        if (actionId == null) {
            actionPadDao.clear(deviceId, slot)
        } else {
            actionPadDao.upsert(ActionPadSlotEntity(deviceId, slot, actionId))
        }
    }

    suspend fun getActionWithSteps(actionId: Long): ActionWithSteps? {
        val action = actionDao.getById(actionId) ?: return null
        return ActionWithSteps(action, actionStepDao.getByAction(actionId))
    }

    suspend fun saveAction(
        actionId: Long,
        deviceId: String,
        name: String,
        steps: List<Pair<String, String>>,
    ): Long {
        val id = if (actionId > 0) {
            val existing = actionDao.getById(actionId)
            actionDao.upsert(
                ActionEntity(
                    id = actionId,
                    deviceId = deviceId,
                    name = name,
                    sortOrder = existing?.sortOrder ?: 0,
                ),
            )
            actionId
        } else {
            val sortOrder = actionDao.maxSortOrder(deviceId) + 1
            actionDao.upsert(
                ActionEntity(
                    deviceId = deviceId,
                    name = name,
                    sortOrder = sortOrder,
                ),
            )
        }
        actionStepDao.deleteByAction(id)
        actionStepDao.upsertAll(
            steps.mapIndexed { index, (stepName, code) ->
                ActionStepEntity(
                    actionId = id,
                    name = stepName,
                    stepStringCode = code,
                    sortOrder = index,
                )
            },
        )
        return id
    }

    suspend fun deleteAction(actionId: Long) {
        val existing = actionDao.getById(actionId) ?: return
        actionDao.delete(actionId)
        actionDao.closeGap(existing.deviceId, existing.sortOrder)
    }

    suspend fun reorder(deviceId: String, orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            actionDao.updateSortOrder(id, index)
        }
    }

    suspend fun ensureDefaultActions(deviceId: String) {
        if (deviceId.isBlank()) return
        if (actionDao.countByDevice(deviceId) > 0) return
        DEFAULT_ACTIONS.forEach { (name, stepName, code) ->
            saveAction(
                actionId = 0,
                deviceId = deviceId,
                name = name,
                steps = listOf(stepName to code),
            )
        }
    }

    fun parseVkCodes(stepStringCode: String): List<Int> = VkCodes.parse(stepStringCode)

    companion object {
        private val DEFAULT_ACTIONS = listOf(
            Triple("Copy", "Ctrl+C", "17,67"),
            Triple("Paste", "Ctrl+V", "17,86"),
            Triple("Alt+Tab", "Alt+Tab", "18,9"),
        )
    }
}
