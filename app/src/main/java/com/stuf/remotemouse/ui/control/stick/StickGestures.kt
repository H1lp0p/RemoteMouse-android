package com.stuf.remotemouse.ui.control.stick

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class StickMode {
    Idle,
    Move,
    Scroll,
}

internal data class StickVisualState(
    val mode: StickMode = StickMode.Idle,
    val knobOffset: Offset = Offset.Zero,
)

/**
 * Stick: deflection from pad center drives mouse move (velocity).
 * Hold near center → scroll mode (MMB-style drag).
 * Tap / double-tap → LMB / RMB.
 */
internal suspend fun PointerInputScope.detectStickGestures(
    holdTimeoutMs: Long = 420L,
    doubleTapTimeoutMs: Long = 280L,
    moveSlop: Dp = 10.dp,
    centerHoldRadius: Dp = 36.dp,
    onVisual: (StickVisualState) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onStickDeflection: (ox: Float, oy: Float) -> Unit,
    onStickReleased: () -> Unit,
    onScroll: (dx: Float, dy: Float) -> Unit,
    onScrollModeEntered: () -> Unit,
) {
    val slopPx = moveSlop.toPx()
    val centerHoldPx = centerHoldRadius.toPx()
    var pendingSingleTap: Job? = null

    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val start = down.position
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxTravel = min(size.width, size.height) / 2f * 0.72f
            val startDist = hypot(start.x - center.x, start.y - center.y)
            val holdEligible = startDist <= centerHoldPx

            var mode = StickMode.Idle
            var moved = false
            var scrollArmed = false

            fun clampKnob(pos: Offset): Offset {
                val raw = pos - center
                val len = hypot(raw.x, raw.y)
                return if (len <= maxTravel || len == 0f) {
                    raw
                } else {
                    Offset(raw.x / len * maxTravel, raw.y / len * maxTravel)
                }
            }

            fun publish(offset: Offset = Offset.Zero) {
                onVisual(StickVisualState(mode = mode, knobOffset = offset))
            }

            publish()

            val holdJob = if (holdEligible) {
                launch {
                    delay(holdTimeoutMs)
                    if (!moved && mode == StickMode.Idle) {
                        mode = StickMode.Scroll
                        scrollArmed = true
                        pendingSingleTap?.cancel()
                        pendingSingleTap = null
                        onScrollModeEntered()
                        publish()
                    }
                }
            } else {
                null
            }

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                if (change.changedToUp()) {
                    holdJob?.cancel()
                    val isTap = !moved && mode == StickMode.Idle
                    if (isTap) {
                        val existing = pendingSingleTap
                        if (existing != null && existing.isActive) {
                            existing.cancel()
                            pendingSingleTap = null
                            onRightClick()
                        } else {
                            pendingSingleTap = launch {
                                delay(doubleTapTimeoutMs)
                                pendingSingleTap = null
                                onLeftClick()
                            }
                        }
                    } else {
                        pendingSingleTap?.cancel()
                        pendingSingleTap = null
                    }
                    onStickReleased()
                    mode = StickMode.Idle
                    publish(Offset.Zero)
                    change.consume()
                    break
                }

                val knob = clampKnob(change.position)
                val travel = hypot(
                    change.position.x - start.x,
                    change.position.y - start.y,
                )

                if (!moved && travel >= slopPx) {
                    moved = true
                    holdJob?.cancel()
                    pendingSingleTap?.cancel()
                    pendingSingleTap = null
                    if (mode == StickMode.Idle) {
                        mode = StickMode.Move
                    }
                }

                when (mode) {
                    StickMode.Move -> {
                        onStickDeflection(knob.x, knob.y)
                        publish(knob)
                    }
                    StickMode.Scroll -> {
                        if (scrollArmed) {
                            val delta = change.positionChange()
                            if (delta != Offset.Zero) {
                                onScroll(delta.x, delta.y)
                            }
                        }
                        publish(knob)
                    }
                    StickMode.Idle -> {
                        if (moved) {
                            mode = StickMode.Move
                            onStickDeflection(knob.x, knob.y)
                            publish(knob)
                        }
                    }
                }
                change.consume()
            }
        }
    }
}
