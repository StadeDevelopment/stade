package dev.stade.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.lerp
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity

private const val ACTIONS_SWAP_MS = 200
private const val SEARCH_FOCUS_DELAY_MS = 80L
private const val SEARCH_UNMOUNT_DELAY_MS = 180L

val LocalHomeTopBarClearance = compositionLocalOf { 0.dp }

class HomeTopBarState {
    var searchActive by mutableStateOf(false)
    var query by mutableStateOf("")
    var starPillCenter by mutableStateOf<Offset?>(null)
    var starPlaced by mutableStateOf(true)
    val searchFocus = FocusRequester()

    fun closeSearch() {
        searchActive = false
        query = ""
    }
}

@Composable
fun rememberHomeTopBarState(): HomeTopBarState = remember { HomeTopBarState() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    container: AppContainer,
    owner: LocalIdentity,
    actionsKey: Any,
    modifier: Modifier = Modifier,
    actions: @Composable (barWidth: Dp) -> Unit
) {
    TopAppBar(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest).then(modifier),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(end = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val barWidth = maxWidth
                HomeIdentityHeader(container = container, owner = owner)
                AnimatedContent(
                    targetState = actionsKey,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    transitionSpec = {
                        val appear = tween<Float>(ACTIONS_SWAP_MS)
                        (fadeIn(appear) + scaleIn(appear, initialScale = 0.8f)) togetherWith
                            (fadeOut(appear) + scaleOut(appear, targetScale = 0.8f))
                    },
                    label = "homeTopBarActions"
                ) { _ ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TOP_PILL_GAP)
                    ) {
                        actions(barWidth)
                    }
                }
            }
        }
    )
}

@Composable
fun SearchPill(
    expanded: Boolean,
    query: String,
    expandedWidth: Dp,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onToggle: () -> Unit
) {
    val strings = LocalStrings.current
    val target = expandedWidth.coerceAtLeast(TOP_PILL_SIZE)
    val progress = animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "searchExpand"
    )

    var fieldMounted by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded) {
        if (expanded) {
            fieldMounted = true
            delay(SEARCH_FOCUS_DELAY_MS)
            runCatching { focusRequester.requestFocus() }
        } else {
            delay(SEARCH_UNMOUNT_DELAY_MS)
            fieldMounted = false
        }
    }

    Surface(
        modifier = Modifier
            .height(TOP_PILL_SIZE)
            .layout { measurable, constraints ->
                val w = lerp(TOP_PILL_SIZE, target, progress.value)
                    .roundToPx()
                    .coerceIn(0, constraints.maxWidth)
                val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fieldMounted) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp)
                        .graphicsLayer {
                            alpha = ((progress.value - 0.35f) / 0.45f).coerceIn(0f, 1f)
                        }
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    strings.searchContactsPlaceholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(TOP_PILL_SIZE)) {
                Box(
                    modifier = Modifier.graphicsLayer { rotationZ = progress.value * 90f },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = expanded,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                        label = "searchIcon"
                    ) { open ->
                        Icon(
                            imageVector = if (open) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (open) strings.closeSearch else strings.searchAction,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTopBarActions(
    screen: HomeProfileActions,
    state: HomeTopBarState,
    barWidth: Dp,
    showSearch: Boolean,
    onOpenStarred: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRadarSettings: () -> Unit
) {
    val strings = LocalStrings.current
    when (screen) {
        HomeProfileActions.Chats -> {
            if (showSearch) {
                SearchPill(
                    expanded = state.searchActive,
                    query = state.query,
                    expandedWidth = barWidth - TOP_PILL_SIZE - TOP_PILL_GAP,
                    focusRequester = state.searchFocus,
                    onQueryChange = { state.query = it },
                    onToggle = {
                        if (state.searchActive) state.closeSearch() else state.searchActive = true
                    }
                )
            }
            Box(
                modifier = Modifier.onGloballyPositioned { coords ->
                    val bounds = coords.boundsInRoot()
                    state.starPillCenter = Offset(bounds.center.x, bounds.center.y)
                }
            ) {
                Box(Modifier.alpha(if (state.starPlaced) 1f else 0f)) {
                    TopBarPill(
                        icon = Icons.Default.Star,
                        contentDescription = strings.starredMessagesTitle,
                        sparkleOnClick = true,
                        onClick = onOpenStarred
                    )
                }
            }
            TopBarPill(
                icon = Icons.Default.Settings,
                contentDescription = strings.settingsAction,
                spinOnClick = true,
                onClick = onOpenSettings
            )
        }
        HomeProfileActions.Radar -> {
            TopBarPill(
                icon = Icons.Default.Tune,
                contentDescription = strings.radarSettingsTitle,
                onClick = onOpenRadarSettings
            )
        }
    }
}

enum class HomeProfileActions { Chats, Radar }
