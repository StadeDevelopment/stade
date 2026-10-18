package dev.stade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stade.identity.ProfileStatus
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.theme.StadeColors

private val IdleColor = Color(0xFFE2A33C)

@Composable
fun profileStatusLabel(status: ProfileStatus): String {
    val strings = LocalStrings.current
    return when (status) {
        ProfileStatus.NONE -> strings.profileStatusNone
        ProfileStatus.ONLINE -> strings.online
        ProfileStatus.IDLE -> strings.profileStatusIdle
        ProfileStatus.DND -> strings.profileStatusDnd
        ProfileStatus.OFFLINE -> strings.offline
    }
}

@Composable
fun profileStatusColor(status: ProfileStatus): Color = when (status) {
    ProfileStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    ProfileStatus.ONLINE -> StadeColors.online
    ProfileStatus.IDLE -> IdleColor
    ProfileStatus.DND -> MaterialTheme.colorScheme.error
    ProfileStatus.OFFLINE -> StadeColors.offline
}

@Composable
fun ProfileStatusPill(status: ProfileStatus, modifier: Modifier = Modifier) {
    if (status == ProfileStatus.NONE) {
        Text(
            profileStatusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    val tint = profileStatusColor(status)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(7.dp))
        Text(
            profileStatusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

@Composable
fun ProfileStatusDot(
    status: ProfileStatus,
    modifier: Modifier = Modifier,
    size: Dp = 13.dp,
    ringColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    if (status == ProfileStatus.NONE) return
    val label = profileStatusLabel(status)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ringColor)
            .padding(2.dp)
            .semantics { contentDescription = label }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(profileStatusColor(status))
        )
    }
}
