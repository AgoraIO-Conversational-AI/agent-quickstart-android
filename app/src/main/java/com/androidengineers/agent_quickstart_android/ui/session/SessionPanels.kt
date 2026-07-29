package com.androidengineers.agent_quickstart_android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.androidengineers.agent_quickstart_android.audio.TurnState
import com.androidengineers.agent_quickstart_android.model.AgentVisualState
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.SessionIssue
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.model.TranscriptTurn
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import com.androidengineers.agent_quickstart_android.ui.components.AgentAvatarBadge
import com.androidengineers.agent_quickstart_android.ui.components.AgentButton
import com.androidengineers.agent_quickstart_android.ui.components.AgentButtonVariant
import com.androidengineers.agent_quickstart_android.ui.components.AgentCard
import com.androidengineers.agent_quickstart_android.ui.components.AgentIconControlButton
import com.androidengineers.agent_quickstart_android.ui.components.InfoField
import com.androidengineers.agent_quickstart_android.ui.components.StatusChip
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSentenceStyle


@Composable
fun ConnectedSessionScreen(
    uiState: ConversationUiState,
    bottomPadding: Dp,
    onDismissMessages: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 24.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(VoiceAiLayout.SectionSpacing),
    ) {
        transientMessages(
            errorMessage = uiState.errorMessage,
            warningMessage = uiState.warningMessage,
            onDismissMessages = onDismissMessages,
        )

        item {
            AgentPresenceCard(
                visualState = uiState.agentVisualState,
                label = uiState.agentStateLabel,
                turnState = uiState.turnState,
            )
        }

        item {
            TranscriptPanel(
                history = uiState.transcriptHistory,
                liveTranscript = uiState.liveTranscript,
            )
        }

        if (uiState.issues.isNotEmpty()) {
            item {
                IssuesPanel(issues = uiState.issues)
            }
        }

        item {
            LiveSessionCard(uiState = uiState)
        }
    }
}

@Composable
private fun LiveSessionCard(
    modifier: Modifier = Modifier,
    uiState: ConversationUiState,
) {
    AgentCard(
        modifier = modifier,
        title = "Session snapshot",
        subtitle = "A quiet view of the live channel, connection health, and microphone state.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            connectedStatusChips(uiState).forEach { item ->
                StatusChip(
                    text = item.label,
                    highlighted = item.highlighted,
                    accentColor = item.accent,
                )
            }
        }

        ResponsiveInfoGrid(
            items = connectedInfoItems(uiState),
        )
    }
}

@Composable
internal fun ResponsiveInfoGrid(
    items: List<InfoItemModel>,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 640.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowItems.forEach { item ->
                            InfoField(
                                label = item.label,
                                value = item.value,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { item ->
                    InfoField(
                        label = item.label,
                        value = item.value,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentPresenceCard(
    modifier: Modifier = Modifier,
    visualState: AgentVisualState,
    label: String,
    turnState: TurnState,
) {
    AgentCard(
        modifier = modifier,
        title = "Your speaking coach",
        subtitle = "Current listening and speaking state.",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AgentAvatarBadge(
                name = "BetterSaid",
                modifier = Modifier.size(88.dp),
                highlightColor = visualState.accentColor(),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            StatusChip(
                text = turnState.toReadableLabel(),
                highlighted = true,
                accentColor = visualState.accentColor(),
            )
        }
    }
}

@Composable
fun TranscriptPanel(
    modifier: Modifier = Modifier,
    history: List<TranscriptTurn>,
    liveTranscript: TranscriptTurn?,
) {
    val listState = rememberLazyListState()
    val visibleTurns = buildList {
        addAll(history)
        if (liveTranscript != null) {
            add(liveTranscript)
        }
    }

    LaunchedEffect(visibleTurns.size, liveTranscript?.text) {
        if (visibleTurns.isNotEmpty()) {
            listState.animateScrollToItem(visibleTurns.lastIndex)
        }
    }

    AgentCard(
        modifier = modifier,
        title = "Speaking mirror",
        subtitle = "Your words and the coach's clearer version appear here as the conversation unfolds.",
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            ),
        ) {
            if (visibleTurns.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(VoiceAiLayout.TranscriptMinHeight)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AgentAvatarBadge(
                            name = "AI",
                            modifier = Modifier.size(64.dp),
                        )
                        Text(
                            text = "Your spoken English appears here.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Say anything. BetterSaid will shape it into clearer English.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = VoiceAiLayout.TranscriptMinHeight,
                            max = VoiceAiLayout.TranscriptMaxHeight,
                        )
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    state = listState,
                ) {
                    items(items = visibleTurns, key = { it.key }) { turn ->
                        TranscriptBubble(turn = turn)
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(
    turn: TranscriptTurn,
) {
    val isUser = turn.speaker == TranscriptSpeaker.USER
    val containerColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        turn.status == TranscriptTurnStatus.INTERRUPTED -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        turn.status == TranscriptTurnStatus.INTERRUPTED -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (isUser) "You said" else "BetterSaid",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.76f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = turn.text.ifBlank { "..." },
                    style = if (isUser) BetterSaidSentenceStyle else MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
                if (turn.status != TranscriptTurnStatus.END) {
                    StatusChip(
                        text = if (turn.status == TranscriptTurnStatus.IN_PROGRESS) {
                            "Streaming"
                        } else {
                            "Interrupted"
                        },
                        highlighted = true,
                        accentColor = if (turn.status == TranscriptTurnStatus.IN_PROGRESS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IssuesPanel(
    issues: List<SessionIssue>,
) {
    AgentCard(
        title = "Session notes",
        subtitle = "Recent warnings and runtime signals from the speaking loop.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            issues.take(4).forEachIndexed { index, issue ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AgentAvatarBadge(
                            name = issue.source.uppercase(Locale.ROOT),
                            modifier = Modifier.size(42.dp),
                            highlightColor = MaterialTheme.colorScheme.error,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = issue.source.uppercase(Locale.ROOT),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = issue.code,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = issue.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (index != issues.take(4).lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
fun BottomCallControls(
    micEnabled: Boolean,
    isStopping: Boolean,
    onToggleMicrophone: () -> Unit,
    onEndConversation: () -> Unit,
) {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VoiceAiLayout.ScreenPadding, vertical = 16.dp)
                .heightIn(min = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            AgentIconControlButton(
                icon = if (micEnabled) Icons.Outlined.Mic else Icons.Outlined.MicOff,
                contentDescription = if (micEnabled) "Mute microphone" else "Unmute microphone",
                active = micEnabled,
                onClick = onToggleMicrophone,
            )
            AgentButton(
                text = if (micEnabled) "Pause mic" else "Resume mic",
                modifier = Modifier.weight(1f),
                variant = AgentButtonVariant.Secondary,
                onClick = onToggleMicrophone,
            )
            AgentButton(
                text = if (isStopping) "Ending..." else "End",
                modifier = Modifier.weight(1f),
                variant = AgentButtonVariant.Destructive,
                enabled = !isStopping,
                onClick = onEndConversation,
            )
        }
    }
}

@Composable
internal fun InlineNoticeCard(
    title: String,
    message: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
