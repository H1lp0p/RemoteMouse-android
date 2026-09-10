package com.stuf.remotemouse.data.local.repo

import com.stuf.remotemouse.data.local.dao.ActionDao
import com.stuf.remotemouse.data.local.dao.ActionStepDao
import com.stuf.remotemouse.data.local.dao.PcDeviceDao
import com.stuf.remotemouse.data.local.entity.ActionEntity
import com.stuf.remotemouse.data.local.entity.ActionStepEntity
import com.stuf.remotemouse.data.local.entity.PcDeviceEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

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
    ) {
        pcDeviceDao.upsert(
            PcDeviceEntity(
                deviceId = deviceId,
                name = name,
                lastIp = lastIp,
                port = port,
                lastUsedAt = System.currentTimeMillis(),
            ),
        )
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

data class ActionWithSteps(
    val action: ActionEntity,
    val steps: List<ActionStepEntity>,
)

@Singleton
class ActionRepository @Inject constructor(
    private val actionDao: ActionDao,
    private val actionStepDao: ActionStepDao,
) {
    fun observeActions(deviceId: String): Flow<List<ActionEntity>> =
        actionDao.observeByDevice(deviceId)

    fun observeSteps(actionId: Long): Flow<List<ActionStepEntity>> =
        actionStepDao.observeByAction(actionId)

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
            actionDao.upsert(ActionEntity(id = actionId, deviceId = deviceId, name = name))
            actionId
        } else {
            actionDao.upsert(ActionEntity(deviceId = deviceId, name = name))
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
        actionDao.delete(actionId)
    }

    fun parseVkCodes(stepStringCode: String): List<Int> =
        stepStringCode
            .split(',', '+', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
}
