package dev.stade.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

private const val SCROLL_BUTTON_MESSAGES_AWAY = 5

@Composable
fun ScrollToBottomButton(listState: LazyListState, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val visible by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_BUTTON_MESSAGES_AWAY }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut() + scaleOut(targetScale = 0.7f),
        modifier = modifier
    ) {
        SmallFloatingActionButton(
            onClick = {
                scope.launch { listState.animateScrollToItem(0) }
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = strings.scrollToBottomAction)
        }
    }
}
