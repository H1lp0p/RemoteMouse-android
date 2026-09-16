package com.stuf.remotemouse.ui.control.stick

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stuf.remotemouse.R

@Composable
fun StickControlPanel(
    leftHeld: Boolean,
    rightHeld: Boolean,
    onToggleLeft: () -> Unit,
    onToggleRight: () -> Unit,
    onOpenActions: () -> Unit,
    onStickDeflection: (ox: Float, oy: Float) -> Unit,
    onStickReleased: () -> Unit,
    onScrollDeflection: (ox: Float, oy: Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var visual by remember { mutableStateOf(StickVisualState()) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onOpenActions,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.actions),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.stick_hint_top),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .pointerInput(Unit) {
                            detectStickGestures(
                                onVisual = { visual = it },
                                onLeftClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onLeftClick()
                                },
                                onRightClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onRightClick()
                                },
                                onStickDeflection = onStickDeflection,
                                onStickReleased = onStickReleased,
                                onScrollDeflection = onScrollDeflection,
                                onScrollModeEntered = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    StickPad(
                        visual = visual,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Text(
                    text = stringResource(R.string.stick_hint_bottom),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StickButtonToggle(
                label = stringResource(R.string.lmb),
                held = leftHeld,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (leftHeld) onToggleLeft() else onLeftClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!leftHeld) onToggleLeft()
                },
                modifier = Modifier.weight(1f),
            )
            StickButtonToggle(
                label = stringResource(R.string.rmb),
                held = rightHeld,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (rightHeld) onToggleRight() else onRightClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!rightHeld) onToggleRight()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StickPad(
    visual: StickVisualState,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.onSecondary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSecondary = MaterialTheme.colorScheme.onSecondary
    val primary = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val edgeR = minOf(size.width, size.height) * 0.42f
        val centerR = edgeR * 0.07f
        val knobR = edgeR * 0.28f
        val knobStroke = 4.dp.toPx()
        val knobCenter = Offset(cx + visual.knobOffset.x, cy + visual.knobOffset.y)

        drawCircle(
            color = outline,
            radius = edgeR,
            center = Offset(cx, cy),
            style = Stroke(width = 3.dp.toPx()),
        )

        drawCircle(
            color = onSecondary,
            radius = centerR,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = secondary.copy(alpha = 0.35f),
            radius = centerR * 2.2f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
        )

        if (visual.knobOffset != Offset.Zero) {
            val dash = 8.dp.toPx()
            val gap = 6.dp.toPx()
            val start = Offset(cx, cy)
            val end = knobCenter
            val dist = (end - start).getDistance()
            if (dist > 1f) {
                val dir = (end - start) / dist
                var t = centerR + 4.dp.toPx()
                val stop = dist - knobR
                while (t < stop) {
                    val a = start + dir * t
                    val b = start + dir * minOf(t + dash, stop)
                    drawLine(
                        color = outline,
                        start = a,
                        end = b,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    t += dash + gap
                }
            }
        }

        when (visual.mode) {
            StickMode.Scroll -> {
                val side = knobR * 1.5f
                val corner = 8.dp.toPx()
                rotate(degrees = 45f, pivot = knobCenter) {
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(knobCenter.x - side / 2f, knobCenter.y - side / 2f),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = knobStroke),
                    )
                }
            }
            else -> {
                drawCircle(
                    color = primary,
                    radius = knobR,
                    center = knobCenter,
                    style = Stroke(width = knobStroke),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickButtonToggle(
    label: String,
    held: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(
                color = if (held) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shape = shape,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (held) ">$label<" else label,
            style = MaterialTheme.typography.headlineSmall,
            color = if (held) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondary
            },
        )
    }
}
