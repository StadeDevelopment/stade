package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.BotBadge
import dev.stade.ui.components.BrandIcons
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

private const val DISCORD_INVITE_URL = "https://discord.gg/MScqr7KeSP"

private sealed class StadeyBubble {
    data class Question(val text: String) : StadeyBubble()
    data class Answer(val text: String, val linkUrl: String? = null, val linkLabel: String? = null) : StadeyBubble()
}

private data class FaqTopic(val question: String, val answer: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StadeyScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val topics = remember(strings) {
        listOf(
            FaqTopic(strings.stadeyFaqAddFriendsQuestion, strings.stadeyFaqAddFriendsAnswer),
            FaqTopic(strings.stadeyFaqSecurityQuestion, strings.stadeyFaqSecurityAnswer),
            FaqTopic(strings.stadeyFaqGroupsStadiumsQuestion, strings.stadeyFaqGroupsStadiumsAnswer),
            FaqTopic(strings.stadeyFaqMediaQuestion, strings.stadeyFaqMediaAnswer),
            FaqTopic(strings.stadeyFaqNetworkingQuestion, strings.stadeyFaqNetworkingAnswer),
            FaqTopic(strings.stadeyFaqLockdownQuestion, strings.stadeyFaqLockdownAnswer)
        )
    }

    var bubbles by remember { mutableStateOf<List<StadeyBubble>>(listOf(StadeyBubble.Answer(strings.stadeyIntro))) }

    fun respond(newBubbles: List<StadeyBubble>) {
        bubbles = bubbles + newBubbles
        val targetIndex = bubbles.lastIndex
        scope.launch { listState.animateScrollToItem(targetIndex.coerceAtLeast(0)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = "Stadey", size = 38.dp, icon = Icons.Default.SmartToy)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Stadey", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(6.dp))
                                BotBadge()
                            }
                            Text(
                                strings.stadeyRowSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(topics, key = { it.question }) { topic ->
                        AssistChip(
                            onClick = {
                                respond(listOf(StadeyBubble.Question(topic.question), StadeyBubble.Answer(topic.answer)))
                            },
                            label = { Text(topic.question, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                    item(key = "stadey-support") {
                        AssistChip(
                            onClick = {
                                respond(
                                    listOf(
                                        StadeyBubble.Answer(
                                            text = strings.stadeySupportAnswer,
                                            linkUrl = DISCORD_INVITE_URL,
                                            linkLabel = "Discord"
                                        )
                                    )
                                )
                            },
                            label = { Text(strings.stadeySupportLabel, maxLines = 1) },
                            shape = RoundedCornerShape(50),
                            leadingIcon = {
                                Icon(BrandIcons.Discord, contentDescription = null, modifier = Modifier.width(16.dp))
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bubbles) { bubble ->
                when (bubble) {
                    is StadeyBubble.Question -> QuestionBubble(bubble.text)
                    is StadeyBubble.Answer -> AnswerBubble(bubble, uriHandler)
                }
            }
        }
    }
}

@Composable
private fun QuestionBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AnswerBubble(bubble: StadeyBubble.Answer, uriHandler: UriHandler) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    bubble.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                val linkUrl = bubble.linkUrl
                if (linkUrl != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        bubble.linkLabel ?: linkUrl,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier.clickable { uriHandler.openUri(linkUrl) }
                    )
                }
            }
        }
    }
}
