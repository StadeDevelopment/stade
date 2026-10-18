package dev.stade.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import dev.stade.ui.i18n.LocalStrings
import kotlinx.datetime.Clock
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.stade.ui.rememberGearHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val TOP_PILL_SIZE = 40.dp
val TOP_PILL_GAP = 8.dp

private val BADGE_SIZE = 18.dp

private const val GEAR_SPIN_MS = 400
private const val GEAR_SPIN_HANDOFF_MS = GEAR_SPIN_MS.toLong()

private const val SPARKLE_MS = 460
private const val SPARKLE_HANDOFF_MS = 300L
internal val SPARKLE_GOLD = Color(0xFFFFC53D)

@Composable
fun UnreadCountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = BADGE_SIZE, minHeight = BADGE_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
            maxLines = 1
        )
    }
}

@Composable
fun TopBarPill(
    icon: ImageVector,
    contentDescription: String,
    spinOnClick: Boolean = false,
    sparkleOnClick: Boolean = false,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val haptic = rememberGearHaptic()
    val baseTint = MaterialTheme.colorScheme.onSurface

    Box {
        Surface(
            modifier = Modifier.size(TOP_PILL_SIZE),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (sparkleOnClick && burst.value > 0f && burst.value < 1f) {
                    SparkleBurst(progress = burst.value)
                }
                IconButton(
                    onClick = {
                        when {
                            spinOnClick -> {
                                scope.launch {
                                    spin.animateTo(
                                        targetValue = spin.value + 360f,
                                        animationSpec = tween(GEAR_SPIN_MS, easing = FastOutSlowInEasing)
                                    )
                                }
                                scope.launch { haptic.play() }
                                scope.launch {
                                    delay(GEAR_SPIN_HANDOFF_MS)
                                    onClick()
                                }
                            }
                            sparkleOnClick -> {
                                scope.launch {
                                    burst.snapTo(0f)
                                    burst.animateTo(1f, tween(SPARKLE_MS, easing = LinearEasing))
                                    burst.snapTo(0f)
                                }
                                scope.launch { haptic.play() }
                                scope.launch {
                                    delay(SPARKLE_HANDOFF_MS)
                                    onClick()
                                }
                            }
                            else -> onClick()
                        }
                    },
                    modifier = Modifier.size(TOP_PILL_SIZE)
                ) {
                    val pop = sin(burst.value * PI.toFloat())
                    Icon(
                        icon,
                        contentDescription = contentDescription,
                        tint = if (sparkleOnClick) lerp(baseTint, SPARKLE_GOLD, pop) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                rotationZ = spin.value
                                val s = 1f + pop * 0.5f
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
            }
        }
        UnreadCountBadge(
            count = badgeCount,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-3).dp, y = 3.dp)
        )
    }
}

private const val GEAR_RAPID_WINDOW_MS = 700L
private const val GEAR_ANGRY_AFTER = 3
private const val GEAR_ANGRY_HOLD_MS = 1600L

@Composable
fun SpinningGearButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = TOP_PILL_SIZE,
    iconSize: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    alreadyOpen: Boolean = false
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    val wobble = remember { Animatable(0f) }
    val haptic = rememberGearHaptic()

    var rapidCount by remember { mutableStateOf(0) }
    var lastClickAt by remember { mutableStateOf(0L) }
    var annoyed by remember { mutableStateOf(false) }

    LaunchedEffect(alreadyOpen) {
        if (!alreadyOpen) {
            rapidCount = 0
            annoyed = false
        }
    }

    LaunchedEffect(annoyed) {
        if (!annoyed) return@LaunchedEffect
        repeat(3) {
            wobble.animateTo(1f, tween(70, easing = LinearEasing))
            wobble.animateTo(-1f, tween(70, easing = LinearEasing))
        }
        wobble.animateTo(0f, tween(70, easing = LinearEasing))
        delay(GEAR_ANGRY_HOLD_MS)
        annoyed = false
        rapidCount = 0
    }

    val iconTint by animateColorAsState(
        targetValue = if (annoyed) MaterialTheme.colorScheme.error else tint,
        animationSpec = tween(180),
        label = "gearTint"
    )

    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = {
                val now = Clock.System.now().toEpochMilliseconds()
                rapidCount = if (now - lastClickAt <= GEAR_RAPID_WINDOW_MS) rapidCount + 1 else 1
                lastClickAt = now

                if (alreadyOpen && rapidCount >= GEAR_ANGRY_AFTER) {
                    annoyed = true
                    return@IconButton
                }

                scope.launch {
                    spin.animateTo(
                        targetValue = spin.value + 360f,
                        animationSpec = tween(GEAR_SPIN_MS, easing = FastOutSlowInEasing)
                    )
                }
                scope.launch { haptic.play() }
                scope.launch {
                    delay(GEAR_SPIN_HANDOFF_MS)
                    onClick()
                }
            },
            modifier = modifier.size(buttonSize)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer { rotationZ = spin.value + wobble.value * 14f }
            )
        }

        if (annoyed) {
            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -(buttonSize.value.toInt() + 18))) {
                GearSpeechBubble(strings.gearAnnoyed)
            }
        }
    }
}

@Composable
private fun GearSpeechBubble(message: String) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            val s = 0.6f + 0.4f * pop.value
            scaleX = s
            scaleY = s
            alpha = pop.value.coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shadowElevation = 4.dp
        ) {
            Text(
                message,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                maxLines = 1
            )
        }
        val tailColor = MaterialTheme.colorScheme.errorContainer
        Canvas(Modifier.size(width = 12.dp, height = 6.dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tail, color = tailColor)
        }
    }
}

@Composable
private fun SparkleBurst(progress: Float) {
    Canvas(Modifier.size(TOP_PILL_SIZE)) {
        val radius = size.minDimension / 2f
        drawCircle(
            color = SPARKLE_GOLD.copy(alpha = (1f - progress) * 0.32f),
            radius = radius * (0.45f + progress * 0.85f)
        )
        val points = 6
        for (i in 0 until points) {
            val angle = (i / points.toFloat()) * 2f * PI.toFloat() + progress * 1.2f
            val distance = radius * (0.35f + progress * 0.8f)
            val dotRadius = (1f - progress) * 2.4.dp.toPx()
            if (dotRadius > 0f) {
                drawCircle(
                    color = SPARKLE_GOLD.copy(alpha = (1f - progress) * 0.9f),
                    radius = dotRadius,
                    center = center + Offset(cos(angle) * distance, sin(angle) * distance)
                )
            }
        }
    }
}
