package com.stuf.remotemouse.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ActionWithSteps(
    @Embedded val action: ActionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "actionId",
    )
    val steps: List<ActionStepEntity>,
)
