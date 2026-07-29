package com.androidengineers.agent_quickstart_android.ui

import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
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
import com.androidengineers.agent_quickstart_android.ui.components.LabeledIconText
import com.androidengineers.agent_quickstart_android.ui.components.StatusChip
import com.androidengineers.agent_quickstart_android.ui.theme.AgentquickstartandroidTheme
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidCoral
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidCoralSoft
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSage
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSentenceStyle
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidShapes
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSpacing
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidYellowSoft

private object VoiceAiLayout {
    val ScreenPadding = 20.dp
    val SectionSpacing = 18.dp
    val CardSpacing = 16.dp
    val ContentMaxWidth = 980.dp
    val BottomBarHeight = 116.dp
    val TranscriptMinHeight = 280.dp
    val TranscriptMaxHeight = 460.dp
}

private data class StatusChipModel(
    val label: String,
    val highlighted: Boolean,
    val accent: Color,
)

private data class InfoItemModel(
    val label: String,
    val value: String,
)

private fun ConversationUiState.shouldShowCorrectionDetails(): Boolean {
    return inConversation &&
        (correctionRequestedAtMillis != null || correctionResponseText != null || isAnalyzingCorrection)
}

private fun ConversationUiState.isCoachReadyForSpeech(): Boolean {
    return agentVisualState == AgentVisualState.LISTENING ||
        agentVisualState == AgentVisualState.IDLE ||
        agentVisualState == AgentVisualState.SPEAKING
}

private fun ConversationUiState.lastUserSentence(): String {
    return correctionOriginalText
        ?: transcriptHistory
        .lastOrNull { it.speaker == TranscriptSpeaker.USER && it.text.isNotBlank() }
        ?.text
        ?: "Yesterday I go market and buyed fruits"
}

private fun ConversationUiState.lastAgentSentence(): String {
    return correctionResponseText
        ?: transcriptHistory
        .lastOrNull { it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank() }
        ?.text
        ?: liveTranscript
            ?.takeIf { it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank() }
            ?.text
        ?: lastUserSentence()
}

private fun ConversationUiState.currentCorrectionResponseText(): String? {
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

private fun quoteSentence(sentence: String): String {
    val trimmed = sentence.trim()
    return if (trimmed.startsWith("“") || trimmed.startsWith("\"")) {
        trimmed
    } else {
        "“$trimmed”"
    }
}

private data class ParsedCorrection(
    val original: String,
    val corrected: String,
    val hasCorrectedSentence: Boolean,
    val tip: String,
    val changes: String,
    val changePairs: List<CorrectionChange>,
    val coachMessage: String,
)

private data class CorrectionChange(
    val from: String,
    val to: String,
)

private enum class CorrectionHighlight {
    Replacement,
    Insertion,
}

private data class CorrectionWord(
    val text: String,
    val highlight: CorrectionHighlight? = null,
)

private data class CoachConversationLine(
    val speaker: TranscriptSpeaker,
    val text: String,
)

private fun parseCorrectionResponse(
    originalFallback: String,
    response: String?,
): ParsedCorrection {
    val raw = response.orEmpty()
    fun field(name: String): String {
        val nextFields = listOf("ORIGINAL", "CORRECTED", "TIP", "CHANGES")
            .filterNot { it.equals(name, ignoreCase = true) }
            .joinToString("|")
        return Regex(
            pattern = "(?is)\\b$name\\s*:\\s*(.*?)(?=\\s+\\b(?:$nextFields)\\s*:|$)",
        ).find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
    }

    val changes = field("CHANGES")
    val original = field("ORIGINAL").ifBlank { originalFallback }
    val correctedField = field("CORRECTED")
    val extractedCorrected = correctedField.ifBlank {
        raw.extractCorrectedSentenceFallback(originalFallback = originalFallback)
    }
    val corrected = extractedCorrected.ifBlank { originalFallback }
    val hasCorrectedSentence = correctedField.isNotBlank() || extractedCorrected.isNotBlank()

    return ParsedCorrection(
        original = original,
        corrected = corrected,
        hasCorrectedSentence = hasCorrectedSentence,
        tip = field("TIP").ifBlank {
            "Because this happened in the past, use past-tense verbs like 'went' and 'bought'."
        },
        changes = changes,
        changePairs = parseCorrectionChanges(changes).ifEmpty {
            inferCorrectionChanges(original = original, corrected = corrected)
        },
        coachMessage = raw.toCoachMessage(),
    )
}

private fun String.extractCorrectedSentenceFallback(originalFallback: String): String {
    val cleaned = replace("BETTERSAID_CORRECTION", "", ignoreCase = true)
        .replace(Regex("(?is)\\bORIGINAL\\s*:\\s*.*?(?=\\s+\\b(?:CORRECTED|TIP|CHANGES)\\s*:|$)"), "")
        .replace(Regex("(?is)\\bTIP\\s*:\\s*.*?(?=\\s+\\b(?:ORIGINAL|CORRECTED|CHANGES)\\s*:|$)"), "")
        .replace(Regex("(?is)\\bCHANGES\\s*:\\s*.*$"), "")
        .trim()

    val labeledCorrected = Regex(
        pattern = "(?is)(?:corrected sentence|more natural english|natural sentence|better sentence)\\s*(?:is|:)\\s*[\"“]?(.+?)[\"”]?(?:\\.|$)",
    ).find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
    if (labeledCorrected.isNotBlank() &&
        labeledCorrected.normalizeCorrectionToken() != originalFallback.normalizeCorrectionToken()
    ) {
        return labeledCorrected
    }

    val quotedSentence = Regex("[\"“]([^\"”]+)[\"”]").findAll(cleaned)
        .map { it.groupValues[1].trim() }
        .firstOrNull { it.normalizeCorrectionToken() != originalFallback.normalizeCorrectionToken() }
        .orEmpty()
    if (quotedSentence.isNotBlank()) {
        return quotedSentence
    }

    return cleaned
        .takeIf {
            it.isNotBlank() &&
                !it.contains("ORIGINAL:", ignoreCase = true) &&
                !it.contains("TIP:", ignoreCase = true) &&
                it.normalizeCorrectionToken() != originalFallback.normalizeCorrectionToken()
        }
        .orEmpty()
}

private fun String.toCoachMessage(): String {
    val cleaned = lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { line ->
            line.equals("BETTERSAID_CORRECTION", ignoreCase = true) ||
                line.startsWith("ORIGINAL:", ignoreCase = true) ||
                line.startsWith("CORRECTED:", ignoreCase = true) ||
                line.startsWith("TIP:", ignoreCase = true) ||
                line.startsWith("CHANGES:", ignoreCase = true)
        }
        .joinToString(" ")
        .trim()

    return cleaned.toConversationDisplayText()
}

private fun parseCorrectionChanges(changes: String): List<CorrectionChange> {
    return changes
        .split(';', '\n')
        .mapNotNull { part ->
            val pieces = part
                .replace("→", "->")
                .replace("=>", "->")
                .split("->", limit = 2)
            if (pieces.size != 2) {
                null
            } else {
                val from = pieces[0].trim().trim('"', '\'', '`', '-', '*')
                val to = pieces[1].trim().trim('"', '\'', '`', '-', '*')
                if (from.isBlank() || to.isBlank()) null else CorrectionChange(from, to)
            }
        }
}

private fun inferCorrectionChanges(
    original: String,
    corrected: String,
): List<CorrectionChange> {
    val originalDisplayWords = original.splitDisplayWords()
    val correctedDisplayWords = corrected.splitDisplayWords()
    val originalWords = originalDisplayWords.map { it.normalizeCorrectionToken() }
    val correctedWords = correctedDisplayWords.map { it.normalizeCorrectionToken() }
    val matches = lcsMatches(originalWords, correctedWords)
    return buildList {
        var previousOriginal = -1
        var previousCorrected = -1
        (matches + (originalWords.size to correctedWords.size)).forEach { (nextOriginal, nextCorrected) ->
            val originalGap = originalDisplayWords.subList(previousOriginal + 1, nextOriginal)
            val correctedGap = correctedDisplayWords.subList(previousCorrected + 1, nextCorrected)
            replacementIndexes(
                originalCount = originalGap.size,
                correctedCount = correctedGap.size,
            ).forEach { (originalGapIndex, correctedGapIndex) ->
                add(
                    CorrectionChange(
                        from = originalGap[originalGapIndex].trimCorrectionDisplay(),
                        to = correctedGap[correctedGapIndex].trimCorrectionDisplay(),
                    )
                )
            }
            previousOriginal = nextOriginal
            previousCorrected = nextCorrected
        }
    }.take(6)
}

private fun replacementIndexes(
    originalCount: Int,
    correctedCount: Int,
): List<Pair<Int, Int>> {
    if (originalCount == 0 || correctedCount == 0) {
        return emptyList()
    }
    if (originalCount == 1 || correctedCount == 1) {
        return listOf(0 to 0)
    }

    return (0 until minOf(originalCount, correctedCount)).map { index ->
        val correctedIndex = if (correctedCount > originalCount) {
            (index * (correctedCount - 1)) / (originalCount - 1)
        } else {
            index
        }
        index to correctedIndex
    }
}

private fun highlightedCorrectionWords(
    original: String,
    corrected: String,
    changePairs: List<CorrectionChange>,
): List<CorrectionWord> {
    val correctedDisplayWords = quoteSentence(corrected).splitDisplayWords()
    val originalWords = original.normalizedWords()
    val correctedWords = correctedDisplayWords.map { it.normalizeCorrectionToken() }
    val matchedCorrectedIndices = lcsMatches(originalWords, correctedWords)
        .map { it.second }
        .toSet()
    val unmatchedCorrectedIndices = correctedWords.indices
        .filter { index -> correctedWords[index].isNotBlank() && index !in matchedCorrectedIndices }
        .toSet()
    val inferredReplacementTargets = inferCorrectionChanges(original = original, corrected = corrected)
        .flatMap { it.to.normalizedWords() }
        .filter { it.isNotBlank() }
        .toSet()
    val explicitReplacementTargets = changePairs
        .flatMap { it.to.normalizedWords() }
        .filter { it.isNotBlank() }
        .toSet()
    val replacementTargets = (inferredReplacementTargets + explicitReplacementTargets)
        .filter { target -> correctedWords.any { it == target } }
        .toSet()

    return correctedDisplayWords.mapIndexed { index, word ->
        val normalized = word.normalizeCorrectionToken()
        val highlight = when {
            normalized.isBlank() -> null
            normalized in replacementTargets && index in unmatchedCorrectedIndices -> CorrectionHighlight.Replacement
            index in unmatchedCorrectedIndices -> CorrectionHighlight.Insertion
            else -> null
        }
        CorrectionWord(text = word, highlight = highlight)
    }
}

private fun lcsMatches(
    originalWords: List<String>,
    correctedWords: List<String>,
): List<Pair<Int, Int>> {
    val dp = Array(originalWords.size + 1) { IntArray(correctedWords.size + 1) }
    for (originalIndex in originalWords.indices.reversed()) {
        for (correctedIndex in correctedWords.indices.reversed()) {
            dp[originalIndex][correctedIndex] = if (
                originalWords[originalIndex].isNotBlank() &&
                originalWords[originalIndex] == correctedWords[correctedIndex]
            ) {
                dp[originalIndex + 1][correctedIndex + 1] + 1
            } else {
                maxOf(dp[originalIndex + 1][correctedIndex], dp[originalIndex][correctedIndex + 1])
            }
        }
    }

    return buildList {
        var originalIndex = 0
        var correctedIndex = 0
        while (originalIndex < originalWords.size && correctedIndex < correctedWords.size) {
            if (
                originalWords[originalIndex].isNotBlank() &&
                originalWords[originalIndex] == correctedWords[correctedIndex]
            ) {
                add(originalIndex to correctedIndex)
                originalIndex += 1
                correctedIndex += 1
            } else if (dp[originalIndex + 1][correctedIndex] >= dp[originalIndex][correctedIndex + 1]) {
                originalIndex += 1
            } else {
                correctedIndex += 1
            }
        }
    }
}

private fun String.splitDisplayWords(): List<String> {
    return trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
}

private fun String.normalizedWords(): List<String> {
    return splitDisplayWords()
        .mapNotNull { word ->
            word.normalizeCorrectionToken().takeIf { it.isNotBlank() }
        }
}

private fun String.normalizeCorrectionToken(): String {
    return trimCorrectionDisplay()
        .lowercase(Locale.ROOT)
}

private fun String.trimCorrectionDisplay(): String {
    return trim('“', '”', '"', '\'', ',', '.', '!', '?', ':', ';', '(', ')')
}

private fun learningConceptLabel(tip: String): String {
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

private fun String.toConversationDisplayText(): String {
    return lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { line ->
            line.equals("BETTERSAID_CORRECTION", ignoreCase = true) ||
                line.startsWith("ORIGINAL:", ignoreCase = true) ||
                line.startsWith("CORRECTED:", ignoreCase = true) ||
                line.startsWith("TIP:", ignoreCase = true) ||
                line.startsWith("CHANGES:", ignoreCase = true)
        }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun ConversationUiState.coachConversationLines(
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

@Composable
fun ConversationScreen(
    uiState: ConversationUiState,
    onStartRequested: () -> Unit,
    onEndConversation: () -> Unit,
    onDoneSpeaking: () -> Unit,
    onAskCoach: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleTheme: () -> Unit,
    onDismissMessages: () -> Unit,
) {
    VoiceAiAppScreen(
        uiState = uiState,
        onStartRequested = onStartRequested,
        onEndConversation = onEndConversation,
        onDoneSpeaking = onDoneSpeaking,
        onAskCoach = onAskCoach,
        onToggleMicrophone = onToggleMicrophone,
        onToggleTheme = onToggleTheme,
        onDismissMessages = onDismissMessages,
    )
}

@Composable
fun VoiceAiAppScreen(
    uiState: ConversationUiState,
    onStartRequested: () -> Unit,
    onEndConversation: () -> Unit,
    onDoneSpeaking: () -> Unit,
    onAskCoach: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleTheme: () -> Unit,
    onDismissMessages: () -> Unit,
) {
    val showCorrectionDetails = uiState.shouldShowCorrectionDetails()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            BetterSaidTopBar(
                focused = uiState.inConversation,
                correctionMode = showCorrectionDetails,
                onClose = onEndConversation,
                isDarkTheme = uiState.isDarkTheme,
                onToggleTheme = onToggleTheme,
            )
        },
        bottomBar = {
            when {
                !uiState.inConversation -> BetterSaidBottomNavigation()
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                val bottomPadding = if (uiState.inConversation) {
                    VoiceAiLayout.BottomBarHeight
                } else {
                    24.dp
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = VoiceAiLayout.ScreenPadding)
                        .widthIn(max = VoiceAiLayout.ContentMaxWidth)
                        .align(Alignment.TopCenter),
                    color = Color.Transparent,
                ) {
                    if (showCorrectionDetails) {
                        CorrectionDetailsScreen(
                            uiState = uiState,
                            onAskCoach = onAskCoach,
                            onEndConversation = onEndConversation,
                        )
                    } else if (uiState.inConversation) {
                        ListeningStateScreen(
                            uiState = uiState,
                            bottomPadding = bottomPadding,
                            onDoneSpeaking = onDoneSpeaking,
                            onDismissMessages = onDismissMessages,
                        )
                    } else {
                        HomeSpeakScreen(
                            uiState = uiState,
                            onStartRequested = onStartRequested,
                            onDismissMessages = onDismissMessages,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BetterSaidTopBar(
    focused: Boolean,
    correctionMode: Boolean = false,
    onClose: () -> Unit = {},
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    Surface(
        modifier = Modifier.statusBarsPadding(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = VoiceAiLayout.ScreenPadding,
                    vertical = if (focused) BetterSaidSpacing.Xl else 18.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = if (focused) {
                        Modifier.align(Alignment.Center)
                    } else {
                        Modifier.align(Alignment.CenterStart)
                    },
                    horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        text = "BetterSaid",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (correctionMode) {
                    AgentIconControlButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Close correction details",
                        active = false,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onClick = onClose,
                    )
                } else if (!focused) {
                    AgentIconControlButton(
                        icon = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.AccountCircle,
                        contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme",
                        active = false,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onClick = onToggleTheme,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSpeakScreen(
    uiState: ConversationUiState,
    onStartRequested: () -> Unit,
    onDismissMessages: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .betterSaidPaperPattern(),
        contentPadding = PaddingValues(top = BetterSaidSpacing.Lg, bottom = 132.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xl),
    ) {
        item {
            Text(
                text = "Say anything. Watch it become better English.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = BetterSaidSpacing.Md),
                style = BetterSaidSentenceStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        transientMessages(
            errorMessage = uiState.errorMessage,
            warningMessage = uiState.warningMessage,
            onDismissMessages = onDismissMessages,
        )

        item {
            BetterSaidModeChips()
        }

        item {
            BetterSaidMirror()
        }

        item {
            BetterSaidSpeakControl(
                uiState = uiState,
                onStartRequested = onStartRequested,
            )
        }
    }
}

@Composable
private fun BetterSaidModeChips() {
    val modes = listOf("Daily Life", "Interview", "Travel", "School")

    FlowRow(
        modifier = Modifier.widthIn(max = 360.dp),
        horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
    ) {
        modes.forEachIndexed { index, mode ->
            val selected = index == 0
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(34.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = BetterSaidSpacing.Md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BetterSaidMirror() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 320.dp)
            .aspectRatio(1f)
            .padding(horizontal = BetterSaidSpacing.Md),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .rotate(-1.4f),
        ) {
            val stroke = Stroke(width = 4f)
            drawRoundRect(
                color = Color.Black,
                style = stroke,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(36f, 54f),
            )
        }

        Surface(
            modifier = Modifier
                .matchParentSize()
                .padding(3.dp)
                .rotate(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(BetterSaidShapes.InkStroke, MaterialTheme.colorScheme.primary),
            shadowElevation = 0.dp,
        ) {}

        Column(
            modifier = Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f))
                .border(
                    width = BetterSaidShapes.InkStroke,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(BetterSaidSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                modifier = Modifier.size(88.dp),
            )
            Text(
                text = "BetterSaid",
                style = BetterSaidSentenceStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-8).dp)
                .size(52.dp)
                .rotate(12f)
                .clip(CircleShape)
                .background(BetterSaidYellowSoft)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun BetterSaidSpeakControl(
    uiState: ConversationUiState,
    onStartRequested: () -> Unit,
) {
    val isListening = uiState.isStarting
    val statusText = when {
        uiState.isStarting -> "Listening to your thoughts..."
        uiState.isConfigured -> "Tap and speak freely"
        else -> "Add Agora credentials to start"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Lg),
    ) {
        Text(
            text = statusText,
            modifier = Modifier.height(28.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier.size(106.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(if (isListening) BetterSaidCoralSoft else MaterialTheme.colorScheme.secondaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(enabled = uiState.isConfigured && !uiState.isStarting) {
                        onStartRequested()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = if (isListening) "Listening" else "Start speaking",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp),
                )
            }
        }
    }
}

@Composable
private fun BetterSaidBottomNavigation() {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = BetterSaidSpacing.Md, vertical = BetterSaidSpacing.Sm),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BetterSaidNavItem(
                label = "Home",
                selected = true,
                icon = Icons.Outlined.Home,
            )
            BetterSaidNavItem(
                label = "Journal",
                selected = false,
                icon = Icons.AutoMirrored.Outlined.MenuBook,
            )
            BetterSaidNavItem(
                label = "Settings",
                selected = false,
                icon = Icons.Outlined.Settings,
            )
        }
    }
}

@Composable
private fun BetterSaidNavItem(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .clip(if (selected) CircleShape else RoundedCornerShape(BetterSaidShapes.Md))
            .background(containerColor)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun Modifier.betterSaidPaperPattern(): Modifier {
    val backgroundColor = MaterialTheme.colorScheme.background
    val dotColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)

    return background(backgroundColor)
        .drawBehind {
            val spacing = 20.dp.toPx()
            val radius = 0.55.dp.toPx()
            var y = 0f
            while (y <= size.height + spacing) {
                var x = 0f
                while (x <= size.width + spacing) {
                    drawCircle(
                        color = dotColor,
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(x, y),
                    )
                    drawCircle(
                        color = dotColor,
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(x + spacing / 2f, y + spacing / 2f),
                    )
                    x += spacing
                }
                y += spacing
            }
        }
}

@Composable
private fun SessionSetupCard(
    uiState: ConversationUiState,
    onStartRequested: () -> Unit,
) {
    AgentCard(
        title = "Ready when you are",
        subtitle = "BetterSaid listens first, then helps your sentence sound clearer and more natural.",
    ) {
        LabeledIconText(
            icon = Icons.Outlined.Link,
            label = "Realtime speaking loop",
            value = "Agora connects the Android mic to the conversational AI agent so corrections can happen live.",
        )

        LabeledIconText(
            icon = Icons.Outlined.Link,
            label = "Gentle English coach",
            value = "The agent listens for meaning, says the improved sentence aloud, and keeps feedback short.",
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            preSessionStatusChips(uiState).forEach { chip ->
                StatusChip(
                    text = chip.label,
                    highlighted = chip.highlighted,
                    accentColor = chip.accent,
                )
            }
        }

        ResponsiveInfoGrid(
            items = listOf(
                InfoItemModel(
                    label = "Coach",
                    value = "BetterSaid AI",
                ),
                InfoItemModel(
                    label = "Setup",
                    value = if (uiState.isConfigured) "Ready to start" else "local.properties needed",
                ),
                InfoItemModel(
                    label = "Microphone",
                    value = if (uiState.microphonePermissionGranted) {
                        "Permission granted"
                    } else {
                        "Permission required"
                    },
                ),
            ),
        )

        if (!uiState.isConfigured && uiState.configMessage != null) {
            InlineNoticeCard(
                title = "Configuration needed",
                message = uiState.configMessage,
                accentColor = MaterialTheme.colorScheme.error,
                icon = Icons.Outlined.ErrorOutline,
            )
        }

        AgentButton(
            text = if (uiState.isStarting) "Opening the mic..." else "Start speaking",
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.isConfigured && !uiState.isStarting,
            onClick = onStartRequested,
        )
    }
}

@Composable
private fun CorrectionDetailsScreen(
    uiState: ConversationUiState,
    onAskCoach: () -> Unit,
    onEndConversation: () -> Unit,
) {
    val correctionResponse = uiState.currentCorrectionResponseText()
    val correction = parseCorrectionResponse(
        originalFallback = uiState.lastUserSentence(),
        response = correctionResponse,
    )
    val shouldShowCorrectionPlaceholder = !correction.hasCorrectedSentence
    val conversationLines = uiState.coachConversationLines(correction.coachMessage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .betterSaidPaperPattern(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = BetterSaidSpacing.Md,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xl),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 672.dp),
                    verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xl),
                ) {
                    SentenceSection(
                        label = "Your sentence",
                        sentence = correction.original,
                        changedTokens = correction.changePairs.map { it.from },
                        muted = true,
                    )
                    CorrectionMirrorSection(
                        originalSentence = correction.original,
                        sentence = correction.corrected,
                        changePairs = correction.changePairs,
                        isRefining = shouldShowCorrectionPlaceholder,
                    )
                    FriendlyTipCard(
                        tip = correction.tip,
                    )
                    ConversationalCoachCard(
                        lines = conversationLines,
                        isMicEnabled = uiState.micEnabled,
                        isCoachReady = uiState.isCoachReadyForSpeech(),
                        onTalkToCoach = onAskCoach,
                        onEndCall = onEndConversation,
                    )
                    DecorativeRule()
                }
            }
        }

        BackgroundAtmosphere()
    }
}

@Composable
private fun SentenceSection(
    label: String,
    sentence: String,
    changedTokens: List<String> = emptyList(),
    muted: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm)) {
        Text(
            text = label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BetterSaidShapes.Lg),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            shadowElevation = 0.dp,
        ) {
            MarkedSentenceText(
                sentence = quoteSentence(sentence),
                changedTokens = changedTokens,
                highlightColor = BetterSaidCoralSoft,
                textColor = if (muted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(BetterSaidSpacing.Md),
                textStyle = MaterialTheme.typography.bodyLarge,
                italic = muted,
            )
        }
    }
}

@Composable
private fun CorrectionMirrorSection(
    originalSentence: String,
    sentence: String,
    changePairs: List<CorrectionChange>,
    isRefining: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "correction-mirror")
    val borderProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "border-progress",
    )
    val borderColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        borderProgress,
    )

    Column(verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "MORE NATURAL ENGLISH",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (isRefining) "Refining live..." else "Refined just now",
                style = BetterSaidSentenceStyle,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp, 18.dp, 12.dp, 16.dp),
            color = Color.White,
            border = BorderStroke(2.dp, borderColor),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(BetterSaidSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Md),
            ) {
                CorrectionSentenceText(
                    originalSentence = originalSentence,
                    sentence = sentence,
                    changePairs = changePairs,
                    isRefining = isRefining,
                )
                if (!isRefining && changePairs.isNotEmpty()) {
                    CorrectionChangeLegend(changePairs = changePairs)
                }
            }
        }
    }
}

@Composable
private fun CorrectionSentenceText(
    originalSentence: String,
    sentence: String,
    changePairs: List<CorrectionChange>,
    isRefining: Boolean,
) {
    if (isRefining) {
        Text(
            text = "BetterSaid is shaping a more natural sentence...",
            modifier = Modifier.fillMaxWidth(),
            style = BetterSaidSentenceStyle.copy(
                fontSize = 26.sp,
                lineHeight = 34.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        )
    } else {
        InlineCorrectionSentenceText(
            words = highlightedCorrectionWords(
                original = originalSentence,
                corrected = sentence,
                changePairs = changePairs,
            ),
        )
    }
}

@Composable
private fun InlineCorrectionSentenceText(
    words: List<CorrectionWord>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        words.forEach { word ->
            CorrectionWordText(word = word)
        }
    }
}

@Composable
private fun CorrectionWordText(word: CorrectionWord) {
    val highlightColor = when (word.highlight) {
        CorrectionHighlight.Replacement -> BetterSaidSage
        CorrectionHighlight.Insertion -> BetterSaidCoral
        null -> Color.Transparent
    }
    Box(
        modifier = if (word.highlight == null) {
            Modifier
        } else {
            Modifier
                .padding(horizontal = 1.dp)
                .drawBehind {
                    val strokeHeight = size.height * 0.34f
                    drawRoundRect(
                        color = highlightColor.copy(alpha = 0.32f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - strokeHeight - 4.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width, strokeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            x = 8.dp.toPx(),
                            y = 8.dp.toPx(),
                        ),
                    )
                    drawRoundRect(
                        color = highlightColor.copy(alpha = 0.92f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            x = 3.dp.toPx(),
                            y = 3.dp.toPx(),
                        ),
                    )
                }
                .padding(horizontal = 2.dp)
        },
    ) {
        Text(
            text = word.text,
            style = BetterSaidSentenceStyle.copy(
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = if (word.highlight == null) FontWeight.Normal else FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CorrectionChangeLegend(
    changePairs: List<CorrectionChange>,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
    ) {
        changePairs.take(5).forEach { change ->
            CorrectionChangePill(change = change)
        }
    }
}

@Composable
private fun CorrectionChangePill(
    change: CorrectionChange,
) {
    Surface(
        shape = RoundedCornerShape(BetterSaidShapes.Md),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BetterSaidSpacing.Sm, vertical = BetterSaidSpacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnderlinedLegendText(
                text = change.from,
                color = BetterSaidCoral,
                strikeThrough = true,
            )
            Text(
                text = "->",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UnderlinedLegendText(
                text = change.to,
                color = BetterSaidSage,
                strikeThrough = false,
            )
        }
    }
}

@Composable
private fun UnderlinedLegendText(
    text: String,
    color: Color,
    strikeThrough: Boolean,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.22f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                textDecoration = if (strikeThrough) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MarkedSentenceText(
    sentence: String,
    changedTokens: List<String>,
    highlightColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle,
    italic: Boolean = false,
    emphasizeHighlights: Boolean = false,
) {
    val normalizedChanges = changedTokens
        .flatMap { it.normalizedWords().ifEmpty { listOf(it.normalizeCorrectionToken()) } }
        .filter { it.isNotBlank() }
        .toSet()
    val words = sentence.split(" ")

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        words.forEach { word ->
            val highlighted = word.normalizeCorrectionToken() in normalizedChanges
            Text(
                text = word,
                modifier = if (highlighted) {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(highlightColor.copy(alpha = 0.74f))
                        .padding(horizontal = 2.dp)
                } else {
                    Modifier
                },
                style = textStyle.copy(
                    fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else textStyle.fontStyle,
                    fontWeight = if (highlighted && emphasizeHighlights) FontWeight.SemiBold else textStyle.fontWeight,
                ),
                color = textColor,
            )
        }
    }
}

@Composable
private fun FriendlyTipCard(
    tip: String,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(BetterSaidShapes.Lg))
                .background(Color.Black),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BetterSaidShapes.Lg),
            color = Color.White,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(BetterSaidSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Md),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BetterSaidCoralSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs)) {
                    Text(
                        text = "Friendly Tip",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = learningConceptLabel(tip).uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationalCoachCard(
    lines: List<CoachConversationLine>,
    isMicEnabled: Boolean,
    isCoachReady: Boolean,
    onTalkToCoach: () -> Unit,
    onEndCall: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BetterSaidShapes.Lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(BetterSaidSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Conversational Coach",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isMicEnabled) "Listening for your follow-up" else "Ask anything about the correction",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(
                    text = if (isMicEnabled) "LIVE" else "READY",
                    highlighted = isMicEnabled,
                    accentColor = MaterialTheme.colorScheme.secondary,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
            ) {
                if (lines.isEmpty()) {
                    CoachBubble(
                        line = CoachConversationLine(
                            speaker = TranscriptSpeaker.AGENT,
                            text = "Tap Talk to Coach and ask why the sentence changed, or try another example using the same grammar idea.",
                        )
                    )
                } else {
                    lines.takeLast(5).forEach { line ->
                        CoachBubble(line = line)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SketchButton(
                    text = if (isMicEnabled) "Keep Talking" else "Talk to Coach",
                    modifier = Modifier.weight(1f),
                    enabled = isCoachReady,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    onClick = onTalkToCoach,
                )
                SketchButton(
                    text = "End Call",
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onEndCall,
                )
            }
        }
    }
}

@Composable
private fun CoachBubble(
    line: CoachConversationLine,
) {
    val isUser = line.speaker == TranscriptSpeaker.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = if (isUser) "You" else "Coach",
            style = MaterialTheme.typography.labelMedium,
            color = if (isUser) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.secondary,
        )
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
            ),
            color = if (isUser) BetterSaidCoralSoft else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
            shadowElevation = 0.dp,
        ) {
            Text(
                text = line.text,
                modifier = Modifier.padding(horizontal = BetterSaidSpacing.Sm, vertical = BetterSaidSpacing.Xs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DecorativeRule() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        )
    }
}

@Composable
private fun CorrectionActionBar(
    onAskCoach: () -> Unit,
) {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BetterSaidSpacing.ContainerMargin, vertical = BetterSaidSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Md),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 672.dp),
                horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SketchButton(
                    text = "Ask Coach",
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    onClick = onAskCoach,
                )
                SketchIconButton(icon = Icons.Outlined.Star)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryCorrectionAction(icon = Icons.AutoMirrored.Outlined.VolumeUp, label = "Listen")
                SecondaryCorrectionAction(icon = Icons.Outlined.Share, label = "Share")
                SecondaryCorrectionAction(icon = Icons.Outlined.History, label = "Journal")
            }
        }
    }
}

@Composable
private fun SketchButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(modifier = modifier.height(60.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(BetterSaidShapes.Lg))
                .background(Color.Black),
        )
        Surface(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { onClick() },
            shape = RoundedCornerShape(BetterSaidShapes.Lg),
            color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SketchIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Box(modifier = Modifier.size(60.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(BetterSaidShapes.Lg))
                .background(Color.Black),
        )
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(BetterSaidShapes.Lg),
            color = Color.White,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun SecondaryCorrectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(
        modifier = Modifier.padding(vertical = BetterSaidSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackgroundAtmosphere() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = Color(0xFFC9E8CB).copy(alpha = 0.16f),
            radius = size.minDimension * 0.34f,
            center = androidx.compose.ui.geometry.Offset(size.width * 1.08f, size.height * 0.22f),
        )
        drawCircle(
            color = BetterSaidCoralSoft.copy(alpha = 0.13f),
            radius = size.minDimension * 0.42f,
            center = androidx.compose.ui.geometry.Offset(size.width * -0.12f, size.height * 0.9f),
        )
    }
}

@Composable
private fun ListeningStateScreen(
    uiState: ConversationUiState,
    bottomPadding: Dp,
    onDoneSpeaking: () -> Unit,
    onDismissMessages: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .betterSaidPaperPattern(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            contentPadding = PaddingValues(
                top = BetterSaidSpacing.Xl,
                bottom = bottomPadding + 86.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            transientMessages(
                errorMessage = uiState.errorMessage,
                warningMessage = uiState.warningMessage,
                onDismissMessages = onDismissMessages,
            )

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 512.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xl),
                ) {
                    LiveSpeechCard(uiState = uiState)
                    ListeningInteractionCenter(uiState = uiState)
                    DoneSpeakingButton(
                        isAnalyzing = uiState.isAnalyzingCorrection,
                        isCoachReady = uiState.isCoachReadyForSpeech(),
                        onClick = onDoneSpeaking,
                    )
                }
            }
        }

        ListeningFooterNote(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BetterSaidSpacing.Xl),
        )
    }
}

@Composable
private fun ListeningInteractionCenter(uiState: ConversationUiState) {
    val transition = rememberInfiniteTransition(label = "listening-mirror")
    val pulse by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val primary = MaterialTheme.colorScheme.primary
    val coachReady = uiState.isCoachReadyForSpeech()
    val userSpeaking = uiState.turnState == TurnState.USER_SPEAKING
    val wavePulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920),
            repeatMode = RepeatMode.Restart,
        ),
        label = "speaking-wave",
    )

    Box(
        modifier = Modifier.size(232.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            listOf(
                80.dp.toPx() * pulse to 0.10f,
                112.dp.toPx() * pulse to 0.05f,
            ).forEach { (radius, alpha) ->
                drawCircle(
                    color = primary.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        if (userSpeaking) {
            SpeakingWaveRings(progress = wavePulse)
        }

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (userSpeaking) BetterSaidSage else MaterialTheme.colorScheme.primary)
                .clickable(enabled = uiState.micRequestedEnabled) {},
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(42.dp),
            )
            Canvas(
                modifier = Modifier
                    .size(104.dp),
            ) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.42f),
                    radius = size.minDimension / 2f,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            intervals = floatArrayOf(10f, 5f),
                        ),
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
        ) {
            Text(
                text = when {
                    uiState.isAnalyzingCorrection -> "ANALYZING"
                    !coachReady -> "GETTING READY"
                    userSpeaking -> "LISTENING"
                    uiState.micRequestedEnabled -> "LISTENING"
                    else -> "PAUSED"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Text(
                text = when {
                    uiState.isAnalyzingCorrection -> "BetterSaid is shaping your English..."
                    !coachReady -> "Wait for the coach to start listening..."
                    userSpeaking -> "Keep going..."
                    uiState.micRequestedEnabled -> "Speak naturally..."
                    else -> "Mic is paused"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
        }
    }
}

@Composable
private fun SpeakingWaveRings(
    progress: Float,
) {
    Canvas(modifier = Modifier.size(178.dp)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.31f
        listOf(0f, 0.34f, 0.68f).forEachIndexed { index, offset ->
            val phase = (progress + offset) % 1f
            val radius = baseRadius + phase * size.minDimension * 0.22f
            val alpha = (1f - phase).coerceIn(0f, 1f) * 0.42f
            drawCircle(
                color = BetterSaidSage.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = (3 - index.coerceAtMost(2)).dp.toPx()),
            )
        }
    }
}

@Composable
private fun LiveSpeechCard(uiState: ConversationUiState) {
    val liveUserText = uiState.liveTranscript
        ?.takeIf { it.speaker == TranscriptSpeaker.USER }
        ?.text
        ?.takeIf { it.isNotBlank() }
    val lastUserText = uiState.transcriptHistory
        .lastOrNull { it.speaker == TranscriptSpeaker.USER && it.text.isNotBlank() }
        ?.text
    val coachReady = uiState.isCoachReadyForSpeech()
    val transcriptText = liveUserText ?: lastUserText ?: if (coachReady) {
        "Start speaking. Your sentence will appear here as ink on paper."
    } else {
        "BetterSaid is joining. Your mic will open when the coach is ready."
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(BetterSaidShapes.InkStroke, MaterialTheme.colorScheme.primary),
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(BetterSaidSpacing.Lg),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transcriptText,
                        style = BetterSaidSentenceStyle.copy(fontSize = 24.sp, lineHeight = 32.sp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    )
                    if (coachReady) {
                        BlinkingInkCursor()
                    }
                }
                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                        .size(36.dp),
                ) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.16f, size.height * 0.84f)
                        cubicTo(
                            size.width * 0.16f,
                            size.height * 0.84f,
                            size.width * 0.22f,
                            size.height * 0.5f,
                            size.width * 0.5f,
                            size.height * 0.5f,
                        )
                        cubicTo(
                            size.width * 0.78f,
                            size.height * 0.5f,
                            size.width * 0.84f,
                            size.height * 0.16f,
                            size.width * 0.84f,
                            size.height * 0.16f,
                        )
                    }
                    drawPath(
                        path = path,
                        color = Color.Black.copy(alpha = 0.2f),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun BlinkingInkCursor() {
    val transition = rememberInfiniteTransition(label = "ink-cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor-alpha",
    )

    Box(
        modifier = Modifier
            .padding(start = BetterSaidSpacing.Xs)
            .width(8.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
    )
}

@Composable
private fun DoneSpeakingButton(
    isAnalyzing: Boolean,
    isCoachReady: Boolean,
    onClick: () -> Unit,
) {
    val enabled = !isAnalyzing && isCoachReady
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .padding(top = BetterSaidSpacing.Lg)
            .clickable(enabled = enabled) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BetterSaidSpacing.Xl, vertical = BetterSaidSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.StopCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = when {
                    isAnalyzing -> "ANALYZING..."
                    !isCoachReady -> "COACH JOINING..."
                    else -> "DONE SPEAKING"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListeningFooterNote(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BetterSaidSpacing.ContainerMargin)
            .widthIn(max = 430.dp),
        shape = RoundedCornerShape(BetterSaidShapes.Md),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BetterSaidSpacing.Md, vertical = BetterSaidSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Don't worry about mistakes-the mirror will reflect them kindly.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
    }
}

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
private fun HeroIntroCard(
    title: String,
    subtitle: String,
) {
    AgentCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun ResponsiveInfoGrid(
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
private fun InlineNoticeCard(
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

private fun androidx.compose.foundation.lazy.LazyListScope.transientMessages(
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
private fun preSessionStatusChips(uiState: ConversationUiState): List<StatusChipModel> {
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
private fun connectedStatusChips(uiState: ConversationUiState): List<StatusChipModel> {
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

private fun connectedInfoItems(uiState: ConversationUiState): List<InfoItemModel> {
    return listOf(
        InfoItemModel("Practice room", uiState.channelName ?: "Joining..."),
        InfoItemModel("Speaker ID", uiState.localUid ?: "Pending"),
        InfoItemModel("Live text", uiState.rtmConnectionLabel),
    )
}

@Composable
private fun AgentVisualState.accentColor(): Color {
    return when (this) {
        AgentVisualState.WAITING -> MaterialTheme.colorScheme.outline
        AgentVisualState.LISTENING -> MaterialTheme.colorScheme.secondary
        AgentVisualState.THINKING -> MaterialTheme.colorScheme.tertiary
        AgentVisualState.SPEAKING -> MaterialTheme.colorScheme.primary
        AgentVisualState.IDLE -> MaterialTheme.colorScheme.primary
        AgentVisualState.DISCONNECTED -> MaterialTheme.colorScheme.error
    }
}

private fun TurnState.toReadableLabel(): String {
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
