package com.stuf.remotemouse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.stuf.remotemouse.data.local.entity.ActionEntity
import com.stuf.remotemouse.data.local.entity.ActionPadSlotEntity
import com.stuf.remotemouse.data.local.entity.ActionStepEntity
import com.stuf.remotemouse.data.local.entity.ActionWithSteps
import com.stuf.remotemouse.data.local.entity.MeEntity
import com.stuf.remotemouse.data.local.entity.PcDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeDao {
    @Query("SELECT * FROM me WHERE id = 1 LIMIT 1")
    suspend fun getMe(): MeEntity?

    @Query("SELECT * FROM me WHERE id = 1 LIMIT 1")
    fun observeMe(): Flow<MeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(me: MeEntity)

    @Update
    suspend fun update(me: MeEntity)
}

@Dao
interface PcDeviceDao {
    @Query("SELECT * FROM pc_devices ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<PcDeviceEntity>>

    @Query("SELECT * FROM pc_devices ORDER BY lastUsedAt DESC")
    suspend fun getAll(): List<PcDeviceEntity>

    @Query("SELECT * FROM pc_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getById(deviceId: String): PcDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: PcDeviceEntity)

    @Query("DELETE FROM pc_devices WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)
}

@Dao
interface ActionDao {
    @Query("SELECT * FROM actions WHERE deviceId = :deviceId ORDER BY sortOrder ASC, id ASC")
    fun observeByDevice(deviceId: String): Flow<List<ActionEntity>>

    @androidx.room.Transaction
    @Query("SELECT * FROM actions WHERE deviceId = :deviceId ORDER BY sortOrder ASC, id ASC")
    fun observeByDeviceWithSteps(deviceId: String): Flow<List<ActionWithSteps>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM actions WHERE deviceId = :deviceId")
    suspend fun maxSortOrder(deviceId: String): Int

    @Query("UPDATE actions SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query(
        "UPDATE actions SET sortOrder = sortOrder - 1 " +
            "WHERE deviceId = :deviceId AND sortOrder > :removedOrder",
    )
    suspend fun closeGap(deviceId: String, removedOrder: Int)

    @Query("SELECT COUNT(*) FROM actions WHERE deviceId = :deviceId")
    suspend fun countByDevice(deviceId: String): Int

    @Query("SELECT * FROM actions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ActionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(action: ActionEntity): Long

    @Query("DELETE FROM actions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ActionStepDao {
    @Query("SELECT * FROM action_steps WHERE actionId = :actionId ORDER BY sortOrder ASC")
    fun observeByAction(actionId: Long): Flow<List<ActionStepEntity>>

    @Query("SELECT * FROM action_steps WHERE actionId = :actionId ORDER BY sortOrder ASC")
    suspend fun getByAction(actionId: Long): List<ActionStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(step: ActionStepEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(steps: List<ActionStepEntity>)

    @Query("DELETE FROM action_steps WHERE actionId = :actionId")
    suspend fun deleteByAction(actionId: Long)

    @Query("DELETE FROM action_steps WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ActionPadDao {
    @Query("SELECT * FROM action_pad_slots WHERE deviceId = :deviceId")
    fun observe(deviceId: String): Flow<List<ActionPadSlotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: ActionPadSlotEntity)

    @Query("DELETE FROM action_pad_slots WHERE deviceId = :deviceId AND slot = :slot")
    suspend fun clear(deviceId: String, slot: Int)
}
