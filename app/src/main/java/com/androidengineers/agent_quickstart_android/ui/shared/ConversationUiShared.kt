package com.androidengineers.agent_quickstart_android.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.androidengineers.agent_quickstart_android.audio.TurnState
import com.androidengineers.agent_quickstart_android.domain.correction.CoachConversationLine
import com.androidengineers.agent_quickstart_android.domain.correction.toConversationDisplayText
import com.androidengineers.agent_quickstart_android.model.AgentVisualState
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.SessionIssue
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.model.TranscriptTurn
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import com.androidengineers.agent_quickstart_android.ui.components.AgentButton
import com.androidengineers.agent_quickstart_android.ui.components.AgentButtonVariant
import com.androidengineers.agent_quickstart_android.ui.components.AgentCard
import com.androidengineers.agent_quickstart_android.ui.components.StatusChip
import com.androidengineers.agent_quickstart_android.ui.theme.AgentquickstartandroidTheme


internal object VoiceAiLayout {
    val ScreenPadding = 20.dp
    val SectionSpacing = 18.dp
    val CardSpacing = 16.dp
    val ContentMaxWidth = 980.dp
    val BottomBarHeight = 116.dp
    val TranscriptMinHeight = 280.dp
    val TranscriptMaxHeight = 460.dp
}

internal data class StatusChipModel(
    val label: String,
    val highlighted: Boolean,
    val accent: Color,
)

internal data class InfoItemModel(
    val label: String,
    val value: String,
)

internal fun ConversationUiState.shouldShowCorrectionDetails(): Boolean {
    return inConversation &&
        (correctionRequestedAtMillis != null || correctionResponseText != null || isAnalyzingCorrection)
}

internal fun ConversationUiState.isCoachReadyForSpeech(): Boolean {
    val coachHasSpoken = liveTranscript?.speaker == TranscriptSpeaker.AGENT ||
        transcriptHistory.any { it.speaker == TranscriptSpeaker.AGENT }

    return !isStarting &&
        (
            agentVisualState == AgentVisualState.LISTENING ||
                agentVisualState == AgentVisualState.SPEAKING ||
                (agentVisualState == AgentVisualState.IDLE && coachHasSpoken) ||
                (agentVisualState == AgentVisualState.WAITING && coachHasSpoken)
            )
}

internal fun ConversationUiState.lastUserSentence(): String {
    return correctionOriginalText
        ?: transcriptHistory
        .lastOrNull { it.speaker == TranscriptSpeaker.USER && it.text.isNotBlank() }
        ?.text
        ?: "Yesterday I go market and buyed fruits"
}

internal fun ConversationUiState.lastAgentSentence(): String {
    return correctionResponseText
        ?: transcriptHistory
        .lastOrNull { it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank() }
        ?.text
        ?: liveTranscript
            ?.takeIf { it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank() }
            ?.text
        ?: lastUserSentence()
}

internal fun ConversationUiState.currentCorrectionResponseText(): String? {
    correctionResponseText?.let { return it }
    val newAgentTurn = transcriptHistory
        .filter { it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank() }
        .drop(correctionRequestedAgentTurnCount)
        .firstOrNull()
        ?.text
    return newAgentTurn
        ?: liveTranscript
            ?.takeIf { it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank() }
            ?.text
}


internal fun learningConceptLabel(tip: String): String {
    val lowerTip = tip.lowercase(Locale.ROOT)
    return when {
        listOf("past", "present", "future", "tense", "verb").any { it in lowerTip } -> "Grammar focus: verb tense"
        listOf("a ", "an ", "the ", "article").any { it in lowerTip } -> "Grammar focus: articles"
        listOf("pronoun", "he", "she", "they", "him", "her").any { it in lowerTip } -> "Grammar focus: pronouns"
        listOf("preposition", "in ", "on ", "at ", "to ").any { it in lowerTip } -> "Grammar focus: prepositions"
        listOf("plural", "singular", "many", "one").any { it in lowerTip } -> "Grammar focus: number agreement"
        else -> "Grammar focus: natural phrasing"
    }
}

internal fun ConversationUiState.coachConversationLines(
    initialCoachMessage: String,
): List<CoachConversationLine> {
    val correctionKey = correctionAgentTurnKey
    val followUps = if (correctionKey == null) {
        emptyList()
    } else {
        val correctionIndex = transcriptHistory.indexOfFirst { it.key == correctionKey }
        if (correctionIndex == -1) {
            emptyList()
        } else {
            transcriptHistory
                .drop(correctionIndex + 1)
                .filter { it.text.isNotBlank() }
                .mapNotNull { turn ->
                    val displayText = turn.text.toConversationDisplayText()
                    displayText
                        .takeIf { it.isNotBlank() }
                        ?.let { CoachConversationLine(speaker = turn.speaker, text = it) }
                }
        }
    }

    return buildList {
        if (initialCoachMessage.isNotBlank()) {
            add(CoachConversationLine(TranscriptSpeaker.AGENT, initialCoachMessage))
        }
        addAll(followUps)
        liveTranscript
            ?.takeIf { it.text.isNotBlank() && correctionResponseText != null }
            ?.let { turn ->
                val displayText = turn.text.toConversationDisplayText()
                if (displayText.isNotBlank()) {
                    add(CoachConversationLine(turn.speaker, displayText))
                }
            }
    }
}


internal fun androidx.compose.foundation.lazy.LazyListScope.transientMessages(
    errorMessage: String?,
    warningMessage: String?,
    onDismissMessages: () -> Unit,
) {
    if (errorMessage != null) {
        item {
            DismissibleMessageCard(
                title = "Action needed",
                message = errorMessage,
                accentColor = MaterialTheme.colorScheme.error,
                icon = Icons.Outlined.ErrorOutline,
                onDismiss = onDismissMessages,
            )
        }
    }

    if (warningMessage != null) {
        item {
            DismissibleMessageCard(
                title = "Heads up",
                message = warningMessage,
                accentColor = MaterialTheme.colorScheme.tertiary,
                icon = Icons.Outlined.WarningAmber,
                onDismiss = onDismissMessages,
            )
        }
    }
}

@Composable
private fun DismissibleMessageCard(
    title: String,
    message: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: () -> Unit,
) {
    AgentCard {
        InlineNoticeCard(
            title = title,
            message = message,
            accentColor = accentColor,
            icon = icon,
        )
        AgentButton(
            text = "Dismiss",
            modifier = Modifier.fillMaxWidth(),
            variant = AgentButtonVariant.Secondary,
            onClick = onDismiss,
        )
    }
}

@Composable
internal fun preSessionStatusChips(uiState: ConversationUiState): List<StatusChipModel> {
    return listOf(
        StatusChipModel(
            label = if (uiState.isConfigured) "Coach ready" else "Credentials needed",
            highlighted = uiState.isConfigured,
            accent = MaterialTheme.colorScheme.secondary,
        ),
        StatusChipModel(
            label = if (uiState.microphonePermissionGranted) "Microphone ready" else "Microphone permission needed",
            highlighted = uiState.microphonePermissionGranted,
            accent = MaterialTheme.colorScheme.secondary,
        ),
        StatusChipModel(
            label = "Mirror idle",
            highlighted = false,
            accent = MaterialTheme.colorScheme.tertiary,
        ),
        StatusChipModel(
            label = "Coach waiting",
            highlighted = false,
            accent = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
internal fun connectedStatusChips(uiState: ConversationUiState): List<StatusChipModel> {
    val agentJoined = uiState.agentVisualState != AgentVisualState.WAITING &&
        uiState.agentVisualState != AgentVisualState.DISCONNECTED

    return listOf(
        StatusChipModel(
            label = "Coach live",
            highlighted = true,
            accent = MaterialTheme.colorScheme.secondary,
        ),
        StatusChipModel(
            label = if (uiState.micRequestedEnabled) "Microphone ready" else "Microphone muted",
            highlighted = uiState.micRequestedEnabled,
            accent = MaterialTheme.colorScheme.secondary,
        ),
        StatusChipModel(
            label = uiState.rtcConnectionLabel,
            highlighted = uiState.rtcConnectionLabel.contains("connected", ignoreCase = true),
            accent = MaterialTheme.colorScheme.primary,
        ),
        StatusChipModel(
            label = if (agentJoined) "Coach joined" else "Waiting for coach",
            highlighted = agentJoined,
            accent = uiState.agentVisualState.accentColor(),
        ),
    )
}

internal fun connectedInfoItems(uiState: ConversationUiState): List<InfoItemModel> {
    return listOf(
        InfoItemModel("Practice room", uiState.channelName ?: "Joining..."),
        InfoItemModel("Speaker ID", uiState.localUid ?: "Pending"),
        InfoItemModel("Live text", uiState.rtmConnectionLabel),
    )
}

@Composable
internal fun AgentVisualState.accentColor(): Color {
    return when (this) {
        AgentVisualState.WAITING -> MaterialTheme.colorScheme.outline
        AgentVisualState.LISTENING -> MaterialTheme.colorScheme.secondary
        AgentVisualState.THINKING -> MaterialTheme.colorScheme.tertiary
        AgentVisualState.SPEAKING -> MaterialTheme.colorScheme.primary
        AgentVisualState.IDLE -> MaterialTheme.colorScheme.primary
        AgentVisualState.DISCONNECTED -> MaterialTheme.colorScheme.error
    }
}

internal fun TurnState.toReadableLabel(): String {
    return when (this) {
        TurnState.IDLE -> "Standing by"
        TurnState.USER_SPEAKING -> "User speaking"
        TurnState.USER_TURN_FINALIZING -> "Finalizing user turn"
        TurnState.AGENT_THINKING -> "Coach thinking"
        TurnState.AGENT_SPEAKING -> "Coach speaking"
        TurnState.BARGE_IN_DETECTED -> "Barge-in detected"
    }
}

@Preview(
    name = "Pre-session light",

    showBackground = true,
    widthDp = 420,
    heightDp = 900,
)
@Composable
private fun PreSessionPreview() {
    AgentquickstartandroidTheme {
        ConversationScreen(
            uiState = previewPreSessionState(),
            onStartRequested = {},
            onEndConversation = {},
            onDoneSpeaking = {},
            onAskCoach = {},
            onToggleMicrophone = {},
            onToggleTheme = {},
            onPracticeModeSelected = {},
            onDismissMessages = {},
        )
    }
}

@Preview(
    name = "Connected dark",
    showBackground = true,
    widthDp = 420,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ConnectedSessionPreview() {
    AgentquickstartandroidTheme(darkTheme = true) {
        ConversationScreen(
            uiState = previewConnectedState(),
            onStartRequested = {},
            onEndConversation = {},
            onDoneSpeaking = {},
            onAskCoach = {},
            onToggleMicrophone = {},
            onToggleTheme = {},
            onPracticeModeSelected = {},
            onDismissMessages = {},
        )
    }
}

private fun previewPreSessionState(): ConversationUiState {
    return ConversationUiState(
        isConfigured = true,
        microphonePermissionGranted = true,
        configMessage = null,
        warningMessage = null,
        errorMessage = null,
    )
}

private fun previewConnectedState(): ConversationUiState {
    return ConversationUiState(
        isConfigured = true,
        microphonePermissionGranted = true,
        inConversation = true,
        channelName = "bettersaid-practice-room",
        localUid = "1045",
        rtcConnectionLabel = "RTC connected",
        rtmConnectionLabel = "Connected",
        agentVisualState = AgentVisualState.SPEAKING,
        agentStateLabel = "Speaking back in real time",
        turnState = TurnState.AGENT_SPEAKING,
        micEnabled = true,
        micRequestedEnabled = true,
        transcriptHistory = listOf(
            TranscriptTurn(
                key = "1",
                turnId = 1L,
                streamId = 1L,
                speaker = TranscriptSpeaker.AGENT,
                text = "Hi. Say any sentence, and I will help make it sound clearer.",
                status = TranscriptTurnStatus.END,
                createdAtMillis = 0L,
            ),
            TranscriptTurn(
                key = "2",
                turnId = 2L,
                streamId = 1L,
                speaker = TranscriptSpeaker.USER,
                text = "Yesterday I go market and buyed fruits.",
                status = TranscriptTurnStatus.END,
                createdAtMillis = 1L,
            ),
        ),
        liveTranscript = TranscriptTurn(
            key = "3",
            turnId = 3L,
            streamId = 2L,
            speaker = TranscriptSpeaker.AGENT,
            text = "Yesterday, I went to the market and bought some fruit.",
            status = TranscriptTurnStatus.IN_PROGRESS,
            createdAtMillis = 2L,
        ),
        issues = listOf(
            SessionIssue(
                id = "issue-1",
                source = "rtc",
                code = "TOKEN_RENEWAL",
                message = "Token renewal path is active and healthy.",
                timestampMillis = 0L,
            ),
        ),
    )
}
