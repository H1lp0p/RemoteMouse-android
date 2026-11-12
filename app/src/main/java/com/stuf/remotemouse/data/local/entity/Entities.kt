package com.stuf.remotemouse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "me")
data class MeEntity(
    @PrimaryKey val id: Int = 1,
    val clientId: String,
    val clientName: String,
    val androidId: String,
    val model: String,
    val manufacturer: String,
    val osVersion: String,
)

@Entity(tableName = "pc_devices")
data class PcDeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val lastIp: String,
    val port: Int = 12345,
    val lastUsedAt: Long,
    /** Multiplier applied to cursor dx/dy on the phone before send. */
    val cursorSensitivity: Float = DEFAULT_CURSOR_SENSITIVITY,
) {
    companion object {
        const val DEFAULT_CURSOR_SENSITIVITY = 1f
    }
}

@Entity(
    tableName = "actions",
    indices = [Index("deviceId")],
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val name: String,
    /** Position in this device's action list. Zero is the top. */
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "action_steps",
    foreignKeys = [
        ForeignKey(
            entity = ActionEntity::class,
            parentColumns = ["id"],
            childColumns = ["actionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("actionId")],
)
data class ActionStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionId: Long,
    val name: String,
    val stepStringCode: String,
    val sortOrder: Int,
)

/** One cell of the 2×2 action pad. Slot 0 is top-left, then right, then the next row. */
const val ACTION_PAD_SLOT_COUNT = 4

@Entity(
    tableName = "action_pad_slots",
    primaryKeys = ["deviceId", "slot"],
    foreignKeys = [
        ForeignKey(
            entity = ActionEntity::class,
            parentColumns = ["id"],
            childColumns = ["actionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("actionId")],
)
data class ActionPadSlotEntity(
    val deviceId: String,
    val slot: Int,
    val actionId: Long?,
)
