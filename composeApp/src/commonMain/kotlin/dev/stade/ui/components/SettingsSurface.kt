package dev.stade.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val SETTINGS_ROW_RADIUS = 20.dp
private val SETTINGS_ROW_INNER_RADIUS = 6.dp
private val SETTINGS_ROW_PRESSED_RADIUS = 36.dp
private val SETTINGS_ROW_MIN_HEIGHT = 68.dp
private val SETTINGS_BADGE_SIZE = 42.dp
private val SETTINGS_GROUP_GAP = 2.dp

enum class GroupPosition { Single, Top, Middle, Bottom }

val LocalGroupPosition = compositionLocalOf { GroupPosition.Single }

class SettingsGroupScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    val size: Int get() = rows.size

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

fun groupPositions(count: Int): List<GroupPosition> = List(count) { index ->
    when {
        count <= 1 -> GroupPosition.Single
        index == 0 -> GroupPosition.Top
        index == count - 1 -> GroupPosition.Bottom
        else -> GroupPosition.Middle
    }
}

@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    content: SettingsGroupScope.() -> Unit
) {
    val scope = SettingsGroupScope().apply(content)
    val positions = groupPositions(scope.rows.size)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SETTINGS_GROUP_GAP)
    ) {
        scope.rows.forEachIndexed { index, row ->
            CompositionLocalProvider(LocalGroupPosition provides positions[index]) {
                row()
            }
        }
    }
}

@Composable
fun SettingsRowSurface(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = pressed && enabled && onClick != null
    val position = LocalGroupPosition.current

    val restingTop = when (position) {
        GroupPosition.Single, GroupPosition.Top -> SETTINGS_ROW_RADIUS
        GroupPosition.Middle, GroupPosition.Bottom -> SETTINGS_ROW_INNER_RADIUS
    }
    val restingBottom = when (position) {
        GroupPosition.Single, GroupPosition.Bottom -> SETTINGS_ROW_RADIUS
        GroupPosition.Middle, GroupPosition.Top -> SETTINGS_ROW_INNER_RADIUS
    }

    val cornerSpring = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val topRadius by animateDpAsState(
        targetValue = if (active) SETTINGS_ROW_PRESSED_RADIUS else restingTop,
        animationSpec = cornerSpring,
        label = "settingsRowTopRadius"
    )
    val bottomRadius by animateDpAsState(
        targetValue = if (active) SETTINGS_ROW_PRESSED_RADIUS else restingBottom,
        animationSpec = cornerSpring,
        label = "settingsRowBottomRadius"
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "settingsRowScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(
            topStart = topRadius,
            topEnd = topRadius,
            bottomStart = bottomRadius,
            bottomEnd = bottomRadius
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                )
                .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            content()
        }
    }
}

@Composable
fun SettingsBadge(icon: ImageVector, tint: Color) {
    Surface(
        modifier = Modifier.size(SETTINGS_BADGE_SIZE),
        shape = CircleShape,
        color = tint.copy(alpha = 0.12f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}
