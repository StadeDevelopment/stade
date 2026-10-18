package dev.stade.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.stade.message.SearchFilter
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

private const val SEARCH_DEBOUNCE_MS = 180L

class ChatSearchState internal constructor() {
    var active by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var filter by mutableStateOf(SearchFilter.All)
    var hits by mutableStateOf<List<String>>(emptyList())
    var cursor by mutableStateOf(0)

    val current: String? get() = hits.getOrNull(cursor)
    val hasQuery: Boolean get() = query.isNotBlank() || filter != SearchFilter.All

    fun open() {
        active = true
    }

    fun close() {
        active = false
        query = ""
        filter = SearchFilter.All
        hits = emptyList()
        cursor = 0
    }

    fun step(forward: Boolean) {
        if (hits.isEmpty()) return
        cursor = if (forward) {
            if (cursor + 1 >= hits.size) 0 else cursor + 1
        } else {
            if (cursor - 1 < 0) hits.lastIndex else cursor - 1
        }
    }
}

@Composable
fun rememberChatSearchState(key: Any?): ChatSearchState = remember(key) { ChatSearchState() }

@Composable
fun ChatSearchRunner(
    state: ChatSearchState,
    search: (String, SearchFilter) -> List<String>
) {
    LaunchedEffect(state) {
        snapshotFlow { state.query to state.filter }
            .distinctUntilChanged()
            .debounce(SEARCH_DEBOUNCE_MS)
            .collect { (text, filter) ->
                val trimmed = text.trim()
                state.hits = when {
                    filter != SearchFilter.All -> search("", filter)
                    trimmed.isEmpty() -> emptyList()
                    else -> search(trimmed, SearchFilter.All)
                }
                state.cursor = 0
            }
    }
}

@Composable
fun searchFilterLabel(filter: SearchFilter): String {
    val strings = LocalStrings.current
    return when (filter) {
        SearchFilter.All -> strings.searchFilterAll
        SearchFilter.Media -> strings.searchFilterMedia
        SearchFilter.Voice -> strings.searchFilterVoice
        SearchFilter.Stickers -> strings.searchFilterStickers
    }
}

private fun searchFilterIcon(filter: SearchFilter) = when (filter) {
    SearchFilter.All -> Icons.Default.FilterList
    SearchFilter.Media -> Icons.Default.Image
    SearchFilter.Voice -> Icons.Default.Mic
    SearchFilter.Stickers -> Icons.Default.EmojiEmotions
}

@Composable
fun ChatSearchBar(state: ChatSearchState, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    var filterMenuOpen by remember { mutableStateOf(false) }
    val filtering = state.filter != SearchFilter.All

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconButton(onClick = { filterMenuOpen = true }) {
                Icon(
                    searchFilterIcon(state.filter),
                    contentDescription = strings.searchFilterAction,
                    modifier = Modifier.size(20.dp),
                    tint = if (filtering) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                SearchFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(searchFilterLabel(option)) },
                        leadingIcon = {
                            Icon(
                                searchFilterIcon(option),
                                contentDescription = null,
                                tint = if (option == state.filter) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (option == state.filter) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            filterMenuOpen = false
                            state.filter = option
                            if (option != SearchFilter.All) state.query = ""
                        }
                    )
                }
            }
        }
        TextField(
            value = state.query,
            onValueChange = { state.query = it },
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            enabled = !filtering,
            placeholder = {
                Text(
                    if (filtering) strings.searchFilterBrowsing(searchFilterLabel(state.filter))
                    else strings.searchInChatPlaceholder
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { state.step(forward = true) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        if (state.hasQuery) {
            Text(
                text = if (state.hits.isEmpty()) {
                    strings.searchInChatNoResults
                } else {
                    strings.searchInChatCount(state.cursor + 1, state.hits.size)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = { state.step(forward = false) },
                enabled = state.hits.isNotEmpty()
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = strings.searchInChatPrevious,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { state.step(forward = true) },
                enabled = state.hits.isNotEmpty()
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = strings.searchInChatNext,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
