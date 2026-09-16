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
)

@Entity(
    tableName = "actions",
    indices = [Index("deviceId")],
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val name: String,
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
