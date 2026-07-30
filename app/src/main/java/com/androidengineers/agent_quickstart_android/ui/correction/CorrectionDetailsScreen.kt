package com.androidengineers.agent_quickstart_android.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.androidengineers.agent_quickstart_android.domain.correction.CoachConversationLine
import com.androidengineers.agent_quickstart_android.domain.correction.CorrectionChange
import com.androidengineers.agent_quickstart_android.domain.correction.CorrectionHighlight
import com.androidengineers.agent_quickstart_android.domain.correction.CorrectionWord
import com.androidengineers.agent_quickstart_android.domain.correction.highlightedCorrectionWords
import com.androidengineers.agent_quickstart_android.domain.correction.parseCorrectionResponse
import com.androidengineers.agent_quickstart_android.domain.correction.quoteSentence
import com.androidengineers.agent_quickstart_android.domain.correction.normalizedWords
import com.androidengineers.agent_quickstart_android.domain.correction.normalizeCorrectionToken
import com.androidengineers.agent_quickstart_android.domain.correction.RE_FIELD_CHANGES
import com.androidengineers.agent_quickstart_android.domain.correction.RE_FIELD_CORRECTED
import com.androidengineers.agent_quickstart_android.domain.correction.RE_FIELD_ORIGINAL
import com.androidengineers.agent_quickstart_android.domain.correction.RE_FIELD_TIP
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.ui.components.StatusChip
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidCoral
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidCoralSoft
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSage
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSentenceStyle
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidShapes
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSpacing


@Composable
internal fun CorrectionDetailsScreen(
    uiState: ConversationUiState,
    onAskCoach: () -> Unit,
    onEndConversation: () -> Unit,
) {
    val correctionResponse = uiState.currentCorrectionResponseText()
    val lastUserSentence = uiState.lastUserSentence()
    val correction = remember(correctionResponse, lastUserSentence) {
        parseCorrectionResponse(
            originalFallback = lastUserSentence,
            response = correctionResponse,
        )
    }
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
                    val fromTokens = remember(correction.changePairs) {
                        correction.changePairs.map { it.from }
                    }
                    SentenceSection(
                        label = "Your sentence",
                        sentence = correction.original,
                        changedTokens = fromTokens,
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
    // Use direct State<Float> (no 'by') so the animated value is only read in the draw phase,
    // preventing 60fps recomposition of the entire correction content tree.
    val borderProgressState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "border-progress",
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

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
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    // Read animation state here (draw phase only — no recomposition)
                    val color = colorLerp(primaryColor, secondaryColor, borderProgressState.value)
                    drawRoundRect(
                        color = color,
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = CornerRadius(13.dp.toPx()),
                    )
                },
            shape = RoundedCornerShape(10.dp, 18.dp, 12.dp, 16.dp),
            color = Color.White,
            border = null,
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
        val words = remember(originalSentence, sentence, changePairs) {
            highlightedCorrectionWords(
                original = originalSentence,
                corrected = sentence,
                changePairs = changePairs,
            )
        }
        InlineCorrectionSentenceText(words = words)
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
        modifier = Modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(BetterSaidShapes.Md),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BetterSaidSpacing.Sm, vertical = BetterSaidSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
        ) {
            UnderlinedLegendText(
                text = change.from,
                color = BetterSaidCoral,
                strikeThrough = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
            ) {
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                )
                Text(
                    text = "to",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                )
            }
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
            .fillMaxWidth()
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
            softWrap = true,
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
) {
    val normalizedChanges = remember(changedTokens) {
        changedTokens.asSequence()
            .flatMap { it.normalizedWords().ifEmpty { listOf(it.normalizeCorrectionToken()) } }
            .filterTo(HashSet()) { it.isNotBlank() }
    }
    val words = remember(sentence) { sentence.split(" ") }

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
                    fontWeight = textStyle.fontWeight,
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
            FormattedCoachText(
                text = line.text,
                modifier = Modifier.padding(horizontal = BetterSaidSpacing.Sm, vertical = BetterSaidSpacing.Xs),
            )
        }
    }
}

@Composable
private fun FormattedCoachText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { text.toCoachTextBlocks() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is CoachTextBlock.Labeled -> Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = block.label.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is CoachTextBlock.Paragraph -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is CoachTextBlock.Bullet -> Row(
                    horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private sealed interface CoachTextBlock {
    data class Labeled(val label: String, val text: String) : CoachTextBlock
    data class Paragraph(val text: String) : CoachTextBlock
    data class Bullet(val text: String) : CoachTextBlock
}

private fun String.toCoachTextBlocks(): List<CoachTextBlock> {
    val structuredBlocks = toStructuredCoachTextBlocks()
    if (structuredBlocks.isNotEmpty()) {
        return structuredBlocks
    }

    val correctionLabels = listOf("BETTERSAID_CORRECTION")
    val cleaned = lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { line ->
            correctionLabels.any { label ->
                line.equals(label, ignoreCase = true) || line.startsWith(label, ignoreCase = true)
            }
        }
        .joinToString("\n")
        .replace(Regex("(?i)\\b(Original|Corrected|Tip|Changes)\\s*:"), "\n")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (cleaned.isBlank()) {
        return listOf(
            CoachTextBlock.Paragraph(
                "Ask a follow-up and BetterSaid will explain the mistake in simple words."
            )
        )
    }

    val sentenceBlocks = cleaned
        .split(Regex("(?<=[.!?])\\s+(?=[A-Z“\"])"))
        .map { it.trim().trim('-', '*', '•').trim() }
        .filter { it.isNotBlank() }

    return sentenceBlocks.mapIndexed { index, sentence ->
        val looksLikeListItem = sentence.contains(" -> ") ||
            sentence.contains("→") ||
            sentence.startsWith("Use ", ignoreCase = true) ||
            sentence.startsWith("Try ", ignoreCase = true)

        if (index > 0 && looksLikeListItem) {
            CoachTextBlock.Bullet(sentence)
        } else {
            CoachTextBlock.Paragraph(sentence)
        }
    }
}

private fun String.toStructuredCoachTextBlocks(): List<CoachTextBlock> {
    fun field(re: Regex): String = re.find(this)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    val original = field(RE_FIELD_ORIGINAL)
    val corrected = field(RE_FIELD_CORRECTED)
    val tip = field(RE_FIELD_TIP)
    val changes = field(RE_FIELD_CHANGES)

    if (original.isBlank() && corrected.isBlank() && tip.isBlank() && changes.isBlank()) {
        return emptyList()
    }

    return buildList {
        if (original.isNotBlank()) {
            add(CoachTextBlock.Labeled("Original", original))
        }
        if (corrected.isNotBlank()) {
            add(CoachTextBlock.Labeled("Corrected", corrected))
        }
        if (tip.isNotBlank()) {
            add(CoachTextBlock.Labeled("Tip", tip))
        }
        changes
            .split(';', '\n')
            .map { it.trim().trim('-', '*', '•').trim() }
            .filter { it.isNotBlank() && (it.contains("->") || it.contains("→") || it.contains("=>")) }
            .forEach { change ->
                add(CoachTextBlock.Bullet(change))
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
                .clip(RoundedCornerShape(BetterSaidShapes.Lg))
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
