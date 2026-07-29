package com.androidengineers.agent_quickstart_android.ui

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidengineers.agent_quickstart_android.audio.TurnState
import com.androidengineers.agent_quickstart_android.model.AgentVisualState
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSage
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSentenceStyle
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidShapes
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSpacing
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidYellowSoft


@Composable
internal fun ListeningStateScreen(
    uiState: ConversationUiState,
    bottomPadding: Dp,
    onDoneSpeaking: () -> Unit,
    onDismissMessages: () -> Unit,
) {
    val coachReady = uiState.isCoachReadyForSpeech()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .betterSaidPaperPattern(),
    ) {
        val liveSpeechCardMinHeight = (maxHeight * 0.42f).coerceIn(260.dp, 420.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            contentPadding = PaddingValues(
                top = BetterSaidSpacing.Md,
                bottom = bottomPadding + 118.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xl),
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
                    if (coachReady) {
                        LiveSpeechCard(
                            uiState = uiState,
                            minHeight = liveSpeechCardMinHeight,
                        )
                    } else {
                        PreparingCoachCard(uiState = uiState)
                    }
                    SpeakingPipeWaveCard(
                        isAnalyzing = uiState.isAnalyzingCorrection,
                        isCoachReady = coachReady,
                        micRequestedEnabled = uiState.micRequestedEnabled,
                        isSpeaking = uiState.turnState == TurnState.USER_SPEAKING,
                    )
                    DoneSpeakingButton(
                        isAnalyzing = uiState.isAnalyzingCorrection,
                        agentVisualState = uiState.agentVisualState,
                        isCoachReady = coachReady,
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
private fun PreparingCoachCard(uiState: ConversationUiState) {
    val title = when (uiState.agentVisualState) {
        AgentVisualState.SPEAKING -> "Coach is saying hello"
        AgentVisualState.THINKING -> "Coach is getting ready"
        AgentVisualState.DISCONNECTED -> "Reconnecting the coach"
        else -> "Preparing your coach"
    }
    val message = when (uiState.agentVisualState) {
        AgentVisualState.SPEAKING -> "Listen first. Your mic will feel ready after the coach finishes the intro."
        AgentVisualState.THINKING -> "One moment while BetterSaid prepares the room."
        AgentVisualState.DISCONNECTED -> "Hold on while the Agora room reconnects."
        else -> "Wait here until BetterSaid joins and starts listening."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(BetterSaidShapes.InkStroke, MaterialTheme.colorScheme.primary),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(BetterSaidSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Md),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BetterSaidYellowSoft)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SpeakingPipeWaveCard(
    isAnalyzing: Boolean,
    isCoachReady: Boolean,
    micRequestedEnabled: Boolean,
    isSpeaking: Boolean,
) {
    val statusLabel = when {
        isAnalyzing -> "ANALYZING"
        !isCoachReady -> "GETTING READY"
        isSpeaking -> "LISTENING"
        micRequestedEnabled -> "LISTENING"
        else -> "PAUSED"
    }
    val statusMessage = when {
        isAnalyzing -> "BetterSaid is shaping your English..."
        !isCoachReady -> "Wait for the coach to start listening..."
        isSpeaking -> "Catching your voice..."
        micRequestedEnabled -> "You can speak now"
        else -> "Mic is paused"
    }
    val inactivePipeColor = MaterialTheme.colorScheme.outlineVariant
    val statusTextColor = if (isSpeaking) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    }
    val transition = rememberInfiniteTransition(label = "pipe-wave-card")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 860),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pipe-wave-progress",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
        shape = RoundedCornerShape(10.dp, 18.dp, 12.dp, 16.dp),
        color = Color.White,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BetterSaidSpacing.Md, vertical = BetterSaidSpacing.Sm),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .align(Alignment.Center),
            ) {
                val centerY = size.height / 2f
                val pipeCount = 17
                val gap = 7.dp.toPx()
                val pipeWidth = 6.dp.toPx()
                val totalWidth = pipeCount * pipeWidth + (pipeCount - 1) * gap
                val startX = (size.width - totalWidth) / 2f
                val quietHeights = listOf(0.18f, 0.28f, 0.22f, 0.36f, 0.24f)

                repeat(pipeCount) { index ->
                    val wavePhase = ((progress + index * 0.075f) % 1f)
                    val triangle = 1f - kotlin.math.abs(wavePhase * 2f - 1f)
                    val heightFactor = if (isSpeaking) {
                        0.24f + triangle * 0.68f
                    } else {
                        quietHeights[index % quietHeights.size]
                    }
                    val pipeHeight = size.height * heightFactor
                    val x = startX + index * (pipeWidth + gap)
                    val color = if (isSpeaking) {
                        BetterSaidSage.copy(alpha = 0.42f + triangle * 0.48f)
                    } else {
                        inactivePipeColor.copy(alpha = 0.36f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(x, centerY - pipeHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(pipeWidth, pipeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            x = pipeWidth,
                            y = pipeWidth,
                        ),
                    )
                }
            }

            Text(
                text = statusLabel,
                modifier = Modifier.align(Alignment.TopCenter),
                style = MaterialTheme.typography.labelLarge,
                color = if (isSpeaking) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = statusMessage,
                modifier = Modifier.align(Alignment.BottomCenter),
                style = MaterialTheme.typography.labelMedium,
                color = statusTextColor,
            )
        }
    }
}

@Composable
private fun LiveSpeechCard(
    uiState: ConversationUiState,
    minHeight: Dp,
) {
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
                .heightIn(min = minHeight)
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight),
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
                        modifier = Modifier.weight(1f),
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
    agentVisualState: AgentVisualState,
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
            .clip(CircleShape)
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
                    isCoachReady -> "DONE SPEAKING"
                    agentVisualState == AgentVisualState.SPEAKING -> "COACH SPEAKING..."
                    agentVisualState == AgentVisualState.THINKING -> "COACH THINKING..."
                    agentVisualState == AgentVisualState.DISCONNECTED -> "RECONNECTING..."
                    else -> "GETTING READY..."
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
