package com.androidengineers.agent_quickstart_android.ui

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.PracticeMode
import com.androidengineers.agent_quickstart_android.ui.components.AgentButton
import com.androidengineers.agent_quickstart_android.ui.components.AgentCard
import com.androidengineers.agent_quickstart_android.ui.components.AgentIconControlButton
import com.androidengineers.agent_quickstart_android.ui.components.LabeledIconText
import com.androidengineers.agent_quickstart_android.ui.components.StatusChip
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidCoral
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidCoralSoft
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSentenceStyle
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidShapes
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSpacing
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidYellowSoft


@Composable
internal fun BetterSaidTopBar(
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
                    vertical = 18.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
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
                if (focused) {
                    AgentIconControlButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = if (correctionMode) {
                            "Close correction details"
                        } else {
                            "End speaking session"
                        },
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
internal fun HomeSpeakScreen(
    uiState: ConversationUiState,
    onStartRequested: () -> Unit,
    onPracticeModeSelected: (PracticeMode) -> Unit,
    onDismissMessages: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .betterSaidPaperPattern(),
    ) {
        val mirrorSize = when {
            maxHeight < 560.dp -> 168.dp
            maxHeight < 660.dp -> 220.dp
            else -> 300.dp
        }
        val itemSpacing = if (maxHeight < 660.dp) BetterSaidSpacing.Md else BetterSaidSpacing.Xl

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = BetterSaidSpacing.Xs, bottom = 132.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = "Say anything. Watch it become better English.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = BetterSaidSpacing.Xs),
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
                BetterSaidModeChips(
                    selectedMode = uiState.practiceMode,
                    onModeSelected = onPracticeModeSelected,
                    modifier = Modifier.padding(top = BetterSaidSpacing.Sm),
                )
            }

            item {
                BetterSaidMirror(
                    mirrorSize = mirrorSize,
                    modifier = Modifier.padding(top = itemSpacing),
                )
            }

            item {
                BetterSaidSpeakControl(
                    uiState = uiState,
                    onStartRequested = onStartRequested,
                    modifier = Modifier.padding(top = itemSpacing),
                )
            }
        }
    }
}

@Composable
private fun BetterSaidModeChips(
    selectedMode: PracticeMode,
    onModeSelected: (PracticeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.widthIn(max = 360.dp),
        horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
    ) {
        PracticeMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .clickable { onModeSelected(mode) },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = BetterSaidSpacing.Md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.label,
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
private fun BetterSaidMirror(
    mirrorSize: Dp,
    modifier: Modifier = Modifier,
) {
    val innerSize = mirrorSize * 0.66f
    val iconSize = mirrorSize * 0.3f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = mirrorSize)
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
                .size(innerSize)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f))
                .border(
                    width = BetterSaidShapes.InkStroke,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(BetterSaidSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                modifier = Modifier.size(iconSize),
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
    modifier: Modifier = Modifier,
) {
    val isListening = uiState.isStarting
    val micInteractionSource = remember { MutableInteractionSource() }
    val micPressed by micInteractionSource.collectIsPressedAsState()
    val micScale by animateFloatAsState(
        targetValue = if (micPressed) 0.94f else 1f,
        label = "home-mic-press-scale",
    )
    val statusText = when {
        uiState.isStarting -> "Listening to your thoughts..."
        uiState.isConfigured -> "Tap and speak freely"
        else -> "Add Agora credentials to start"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
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
                    .scale(micScale)
                    .clip(CircleShape)
                    .background(if (isListening) BetterSaidCoralSoft else MaterialTheme.colorScheme.secondaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(
                        enabled = uiState.isConfigured && !uiState.isStarting,
                        interactionSource = micInteractionSource,
                        indication = LocalIndication.current,
                    ) {
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
internal fun BetterSaidBottomNavigation() {
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
internal fun Modifier.betterSaidPaperPattern(): Modifier {
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
internal fun SessionSetupCard(
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
