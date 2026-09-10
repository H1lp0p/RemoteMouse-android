package com.stuf.remotemouse.ui.control.mouse

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HoldTimeoutMs = 420L
private const val DoubleTapTimeoutMs = 280L
private const val MoveSlopPx = 18f

private enum class TouchMode {
    Pending,
    Move,
    Scroll,
}

fun Modifier.touchpadGestures(
    haptic: HapticFeedback,
    onMove: (dx: Float, dy: Float) -> Unit,
    onScroll: (dy: Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
): Modifier = pointerInput(Unit) {
    var pendingSingleTap: Job? = null

    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalTravel = 0f
            var mode = TouchMode.Pending

            val holdJob = launch {
                delay(HoldTimeoutMs)
                if (mode == TouchMode.Pending && totalTravel < MoveSlopPx) {
                    mode = TouchMode.Scroll
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (!change.pressed) {
                    change.consume()
                    holdJob.cancel()

                    val isTap = mode == TouchMode.Pending && totalTravel < MoveSlopPx
                    if (isTap) {
                        val existing = pendingSingleTap
                        if (existing != null && existing.isActive) {
                            existing.cancel()
                            pendingSingleTap = null
                            onRightClick()
                        } else {
                            pendingSingleTap = launch {
                                delay(DoubleTapTimeoutMs)
                                pendingSingleTap = null
                                onLeftClick()
                            }
                        }
                    } else {
                        pendingSingleTap?.cancel()
                        pendingSingleTap = null
                    }
                    break
                }

                val delta = change.positionChange()
                if (delta != Offset.Zero) {
                    change.consume()
                    totalTravel += hypot(delta.x, delta.y)
                    when {
                        mode == TouchMode.Scroll -> onScroll(delta.y)
                        mode == TouchMode.Move || totalTravel >= MoveSlopPx -> {
                            if (mode != TouchMode.Move) {
                                mode = TouchMode.Move
                                holdJob.cancel()
                                pendingSingleTap?.cancel()
                                pendingSingleTap = null
                            }
                            onMove(delta.x, delta.y)
                        }
                    }
                }
            }

            holdJob.cancel()
        }
    }
}
