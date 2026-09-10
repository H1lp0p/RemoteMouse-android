package com.stuf.remotemouse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.stuf.remotemouse.data.local.dao.ActionDao
import com.stuf.remotemouse.data.local.dao.ActionStepDao
import com.stuf.remotemouse.data.local.dao.MeDao
import com.stuf.remotemouse.data.local.dao.PcDeviceDao
import com.stuf.remotemouse.data.local.entity.ActionEntity
import com.stuf.remotemouse.data.local.entity.ActionStepEntity
import com.stuf.remotemouse.data.local.entity.MeEntity
import com.stuf.remotemouse.data.local.entity.PcDeviceEntity

@Database(
    entities = [
        MeEntity::class,
        PcDeviceEntity::class,
        ActionEntity::class,
        ActionStepEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meDao(): MeDao
    abstract fun pcDeviceDao(): PcDeviceDao
    abstract fun actionDao(): ActionDao
    abstract fun actionStepDao(): ActionStepDao
}
