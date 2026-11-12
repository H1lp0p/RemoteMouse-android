package com.stuf.remotemouse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stuf.remotemouse.data.local.dao.ActionDao
import com.stuf.remotemouse.data.local.dao.ActionStepDao
import com.stuf.remotemouse.data.local.dao.MeDao
import com.stuf.remotemouse.data.local.dao.PcDeviceDao
import com.stuf.remotemouse.data.local.dao.ActionPadDao
import com.stuf.remotemouse.data.local.entity.ActionEntity
import com.stuf.remotemouse.data.local.entity.ActionPadSlotEntity
import com.stuf.remotemouse.data.local.entity.ActionStepEntity
import com.stuf.remotemouse.data.local.entity.MeEntity
import com.stuf.remotemouse.data.local.entity.PcDeviceEntity

@Database(
    entities = [
        MeEntity::class,
        PcDeviceEntity::class,
        ActionEntity::class,
        ActionStepEntity::class,
        ActionPadSlotEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meDao(): MeDao
    abstract fun pcDeviceDao(): PcDeviceDao
    abstract fun actionDao(): ActionDao
    abstract fun actionStepDao(): ActionStepDao
    abstract fun actionPadDao(): ActionPadDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE pc_devices ADD COLUMN cursorSensitivity REAL NOT NULL DEFAULT 1.0",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE actions ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0",
        )
        val cursor = db.query(
            "SELECT id, deviceId FROM actions ORDER BY deviceId, name COLLATE NOCASE, id",
        )
        cursor.use { rows ->
            var device: String? = null
            var order = 0
            while (rows.moveToNext()) {
                val id = rows.getLong(0)
                val deviceId = rows.getString(1)
                if (deviceId != device) {
                    device = deviceId
                    order = 0
                }
                db.execSQL(
                    "UPDATE actions SET sortOrder = ? WHERE id = ?",
                    arrayOf<Any>(order, id),
                )
                order++
            }
        }
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS action_pad_slots (
                deviceId TEXT NOT NULL,
                slot INTEGER NOT NULL,
                actionId INTEGER,
                PRIMARY KEY(deviceId, slot),
                FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_action_pad_slots_actionId ON action_pad_slots(actionId)",
        )
    }
}
