package com.androidengineers.agent_quickstart_android.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.PracticeMode
import com.androidengineers.agent_quickstart_android.ui.components.AgentIconControlButton
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
                } else {
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
            item(key = "tagline") {
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

            item(key = "mode-chips") {
                BetterSaidModeChips(
                    selectedMode = uiState.practiceMode,
                    onModeSelected = onPracticeModeSelected,
                    modifier = Modifier.padding(top = BetterSaidSpacing.Sm),
                )
            }

            item(key = "mirror") {
                BetterSaidMirror(
                    mirrorSize = mirrorSize,
                    modifier = Modifier.padding(top = itemSpacing),
                )
            }

            item(key = "speak-control") {
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
                onClick = { onModeSelected(mode) },
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

    // Capture theme colors outside Canvas so they can be used in drawWithCache.
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inkStrokePx = remember(density) { with(density) { BetterSaidShapes.InkStroke.toPx() } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = mirrorSize)
            .aspectRatio(1f)
            .padding(horizontal = BetterSaidSpacing.Md),
        contentAlignment = Alignment.Center,
    ) {
        // One Canvas handles the shadow outline + rotated card — zero extra GPU layers.
        Canvas(modifier = Modifier.matchParentSize()) {
            val padding = 3.dp.toPx()
            val cardSize = androidx.compose.ui.geometry.Size(
                width = size.width - padding * 2,
                height = size.height - padding * 2,
            )
            val cardOffset = androidx.compose.ui.geometry.Offset(padding, padding)
            val cornerRadius = androidx.compose.ui.geometry.CornerRadius(72f, 72f)

            // Shadow outline — rotated -1.4°
            withTransform({ rotate(-1.4f) }) {
                drawRoundRect(
                    color = Color.Black,
                    style = Stroke(width = 4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(36f, 54f),
                )
            }
            // Card fill + border — rotated +0.8°
            withTransform({ rotate(0.8f, pivot = center) }) {
                drawRoundRect(
                    color = surfaceColor,
                    topLeft = cardOffset,
                    size = cardSize,
                    cornerRadius = cornerRadius,
                )
                drawRoundRect(
                    color = primaryColor,
                    topLeft = cardOffset,
                    size = cardSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = inkStrokePx),
                )
            }
        }

        Column(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(28.dp))
                .background(surfaceContainerLow)
                .border(
                    width = BetterSaidShapes.InkStroke,
                    color = outlineVariant,
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(BetterSaidSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                tint = primaryColor.copy(alpha = 0.86f),
                modifier = Modifier.size(iconSize),
            )
        }

        // Badge — one remaining composable layer, acceptable for a 52dp element.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-8).dp)
                .size(52.dp)
                .rotate(12f)
                .clip(CircleShape)
                .background(BetterSaidYellowSoft)
                .border(2.dp, primaryColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = primaryColor,
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
    val haptic = LocalHapticFeedback.current
    val micInteractionSource = remember { MutableInteractionSource() }
    val micPressed by micInteractionSource.collectIsPressedAsState()

    val snapSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )
    val snapDpSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )
    val micScale by animateFloatAsState(
        targetValue = if (micPressed) 0.90f else 1f,
        animationSpec = snapSpec,
        label = "home-mic-press-scale",
    )
    // Shadow compresses toward the button as it "presses down"
    val shadowOffset by animateDpAsState(
        targetValue = if (micPressed) 1.dp else 4.dp,
        animationSpec = snapDpSpec,
        label = "home-mic-shadow-offset",
    )
    val buttonLift by animateDpAsState(
        targetValue = if (micPressed) 3.dp else 0.dp,
        animationSpec = snapDpSpec,
        label = "home-mic-button-lift",
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
            color = if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Shadow — shrinks toward center when pressed
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .offset(x = shadowOffset, y = shadowOffset)
                    .clip(CircleShape)
                    .background(Color.Black),
            )
            // Button — moves toward shadow when pressed, bounces back on release
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .offset(x = buttonLift, y = buttonLift)
                    .scale(micScale)
                    .clip(CircleShape)
                    .background(
                        if (isListening) BetterSaidCoralSoft
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(
                        enabled = uiState.isConfigured && !uiState.isStarting,
                        interactionSource = micInteractionSource,
                        indication = LocalIndication.current,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
        .drawWithCache {
            // Build one Path with all dot ovals — computed once per size change,
            // then issued as a single draw call every frame instead of ~1 600 drawCircle calls.
            val spacing = 20.dp.toPx()
            val r = 0.55.dp.toPx()
            val halfSpacing = spacing / 2f
            val canvasWidth = size.width
            val canvasHeight = size.height
            val path = Path()
            var y = 0f
            while (y <= canvasHeight + spacing) {
                var x = 0f
                while (x <= canvasWidth + spacing) {
                    path.addOval(androidx.compose.ui.geometry.Rect(
                        left = x - r, top = y - r, right = x + r, bottom = y + r,
                    ))
                    path.addOval(androidx.compose.ui.geometry.Rect(
                        left = x + halfSpacing - r, top = y + halfSpacing - r,
                        right = x + halfSpacing + r, bottom = y + halfSpacing + r,
                    ))
                    x += spacing
                }
                y += spacing
            }
            onDrawBehind {
                drawPath(path = path, color = dotColor)
            }
        }
}
