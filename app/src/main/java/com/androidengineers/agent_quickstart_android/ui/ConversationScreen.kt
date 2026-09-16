package com.androidengineers.agent_quickstart_android.ui

import android.content.res.Configuration
import android.os.SystemClock
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.delay
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
import com.androidengineers.agent_quickstart_android.ui.theme.AgentquickstartandroidTheme

private object VoiceAiLayout {
    val ScreenPadding = 20.dp
    val SectionSpacing = 18.dp
    val CardSpacing = 16.dp
    val ContentMaxWidth = 980.dp
    val BottomBarHeight = 112.dp
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

private data class LiveLensPrompt(
    val label: String,
    val helper: String,
)

private const val TAG = "LiveLensScreen"
private const val AUTO_CAMERA_CONTEXT_INTERVAL_MS = 12_000L

@Composable
fun ConversationScreen(
    uiState: ConversationUiState,
    rtcEngine: RtcEngine? = null,
    onStartRequested: () -> Unit,
    onEndConversation: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleTheme: () -> Unit,
    onDismissMessages: () -> Unit,
    onTextChanged: (String) -> Unit = {},
    onSendText: (Boolean, Boolean) -> Unit = { _, _ -> },
    onAnalyzeCameraFrame: () -> Unit = {},
) {
    VoiceAiAppScreen(
        uiState = uiState,
        rtcEngine = rtcEngine,
        onStartRequested = onStartRequested,
        onEndConversation = onEndConversation,
        onToggleMicrophone = onToggleMicrophone,
        onToggleTheme = onToggleTheme,
        onDismissMessages = onDismissMessages,
        onTextChanged = onTextChanged,
        onSendText = onSendText,
        onAnalyzeCameraFrame = onAnalyzeCameraFrame,
    )
}

@Composable
fun VoiceAiAppScreen(
    uiState: ConversationUiState,
    rtcEngine: RtcEngine? = null,
    onStartRequested: () -> Unit,
    onEndConversation: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleTheme: () -> Unit,
    onDismissMessages: () -> Unit,
    onTextChanged: (String) -> Unit = {},
    onSendText: (Boolean, Boolean) -> Unit = { _, _ -> },
    onAnalyzeCameraFrame: () -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (uiState.inConversation) WindowInsets(0.dp) else WindowInsets.safeDrawing,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                if (uiState.inConversation) {
                    LiveLensCameraListeningScreen(
                        uiState = uiState,
                        rtcEngine = rtcEngine,
                        onBack = onEndConversation,
                        onEndConversation = onEndConversation,
                        onToggleMicrophone = onToggleMicrophone,
                        onAnalyzeCameraFrame = onAnalyzeCameraFrame,
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = VoiceAiLayout.ContentMaxWidth)
                            .align(Alignment.TopCenter),
                        color = Color.Transparent,
                    ) {
                        PreSessionScreen(
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
private fun LiveLensCameraListeningScreen(
    uiState: ConversationUiState,
    rtcEngine: RtcEngine?,
    onBack: () -> Unit,
    onEndConversation: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onAnalyzeCameraFrame: () -> Unit,
) {
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var zoomRatio by rememberSaveable { mutableStateOf(1f) }
    var zoomAvailable by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var maxZoomRatio by remember { mutableStateOf(1f) }
    val elapsedTime = rememberSessionElapsedTime()
    val focusLabel = uiState.liveTranscript?.text
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(36)
        ?: uiState.agentStateLabel

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF11100D)),
    ) {
        if (uiState.cameraPermissionGranted && rtcEngine != null) {
            AgoraLocalVideoPreview(
                rtcEngine = rtcEngine,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            CameraPermissionFallback(
                waitingForRtc = uiState.cameraPermissionGranted && rtcEngine == null,
                modifier = Modifier.matchParentSize(),
            )
        }
        LiveLensCameraScrim(modifier = Modifier.matchParentSize())

        LaunchedEffect(rtcEngine) {
            if (rtcEngine == null) {
                zoomAvailable = false
                torchAvailable = false
                maxZoomRatio = 1f
                torchEnabled = false
                zoomRatio = 1f
            } else {
                zoomAvailable = runCatching { rtcEngine.isCameraZoomSupported }.getOrDefault(false)
                torchAvailable = runCatching { rtcEngine.isCameraTorchSupported }.getOrDefault(false)
                maxZoomRatio = if (zoomAvailable) {
                    runCatching { rtcEngine.cameraMaxZoomFactor }.getOrDefault(1f).coerceAtLeast(1f)
                } else {
                    1f
                }
                zoomRatio = zoomRatio.coerceIn(1f, maxZoomRatio)
                if (!torchAvailable) {
                    torchEnabled = false
                }
            }
        }


        LaunchedEffect(rtcEngine, uiState.inConversation) {
            if (rtcEngine == null || !uiState.inConversation) return@LaunchedEffect
            delay(2_000L)
            while (true) {
                if (!uiState.isAnalyzingVision && !uiState.isStopping) {
                    onAnalyzeCameraFrame()
                }
                delay(AUTO_CAMERA_CONTEXT_INTERVAL_MS)
            }
        }

        CameraRoundButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 52.dp, top = 64.dp),
            onClick = onBack,
        )

        LiveTimerPill(
            elapsedTime = elapsedTime,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp),
        )

        SceneFocusIndicator(
            label = focusLabel,
            modifier = Modifier.align(Alignment.Center),
        )

        CameraSideControls(
            zoomRatio = zoomRatio,
            torchEnabled = torchEnabled,
            zoomAvailable = zoomAvailable,
            torchAvailable = torchAvailable,
            canSwitchCamera = rtcEngine != null,
            canAnalyzeFrame = rtcEngine != null && !uiState.isAnalyzingVision,
            analyzingFrame = uiState.isAnalyzingVision,
            onAnalyzeFrame = onAnalyzeCameraFrame,
            onZoomClick = {
                zoomRatio = nextZoomRatio(
                    currentZoomRatio = zoomRatio,
                    minZoomRatio = 1f,
                    maxZoomRatio = maxZoomRatio,
                )
                rtcEngine?.setCameraZoomFactor(zoomRatio)
            },
            onToggleTorch = {
                if (torchAvailable) {
                    torchEnabled = !torchEnabled
                    rtcEngine?.setCameraTorchOn(torchEnabled)
                }
            },
            onSwitchCamera = {
                rtcEngine?.let { engine ->
                    engine.switchCamera()
                    torchEnabled = false
                    engine.setCameraTorchOn(false)
                    zoomRatio = 1f
                    engine.setCameraZoomFactor(1f)
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp),
        )

        if (uiState.visualContextStatus != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 44.dp, vertical = 150.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.92f),
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = uiState.visualContextStatus,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF20242A),
                    textAlign = TextAlign.Center,
                )
            }
        }

        ListeningControlTray(
            isMuted = !uiState.micRequestedEnabled,
            isStopping = uiState.isStopping,
            onDismiss = onBack,
            onToggleMicrophone = onToggleMicrophone,
            onStop = onEndConversation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 44.dp, vertical = 34.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .width(128.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        )
    }
}

@Composable
private fun AgoraLocalVideoPreview(
    rtcEngine: RtcEngine,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isInspectionMode = LocalInspectionMode.current

    if (isInspectionMode) {
        CameraPermissionFallback(waitingForRtc = false, modifier = modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                val renderView = SurfaceView(viewContext)
                addView(
                    renderView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                )
                rtcEngine.setupLocalVideo(
                    VideoCanvas(
                        renderView,
                        VideoCanvas.RENDER_MODE_HIDDEN,
                        0,
                    )
                )
                rtcEngine.startPreview()
            }
        },
    )

    DisposableEffect(rtcEngine) {
        onDispose {
            runCatching { rtcEngine.stopPreview() }
        }
    }
}

@Composable
private fun CameraPermissionFallback(
    waitingForRtc: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF334155),
                        Color(0xFF0F172A),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = if (waitingForRtc) "Starting live video" else "Camera permission needed",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (waitingForRtc) {
                    "Connecting the camera preview. Tap the eye button to share a frame with Gemini."
                } else {
                    "Live Lens needs camera access to analyze frames from the phone."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LiveLensCameraScrim(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.26f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.34f),
                        ),
                    )
                ),
        )
    }
}

@Composable
private fun CameraRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(50.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.34f),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun LiveTimerPill(
    elapsedTime: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFFEF3B3B),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = elapsedTime,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SceneFocusIndicator(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 2.dp,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF3E3A36),
            )
        }
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(38.dp)
                .background(Color.White.copy(alpha = 0.9f)),
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
        )
    }
}

@Composable
private fun CameraSideControls(
    zoomRatio: Float,
    torchEnabled: Boolean,
    zoomAvailable: Boolean,
    torchAvailable: Boolean,
    canSwitchCamera: Boolean,
    canAnalyzeFrame: Boolean,
    analyzingFrame: Boolean,
    onAnalyzeFrame: () -> Unit,
    onZoomClick: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CameraTextControlButton(
            text = "${formatZoomLabel(zoomRatio)}x",
            contentDescription = "Change zoom",
            enabled = zoomAvailable,
            onClick = onZoomClick,
        )
        CameraIconControlButton(
            icon = Icons.Outlined.Bolt,
            contentDescription = if (torchEnabled) "Turn torch off" else "Turn torch on",
            active = torchEnabled,
            enabled = torchAvailable,
            onClick = onToggleTorch,
        )
        CameraIconControlButton(
            icon = Icons.Outlined.Visibility,
            contentDescription = if (analyzingFrame) "Refreshing visual context" else "Refresh visual context now",
            active = analyzingFrame,
            enabled = canAnalyzeFrame,
            onClick = onAnalyzeFrame,
        )
        CameraIconControlButton(
            icon = Icons.Outlined.Cached,
            contentDescription = "Switch camera",
            enabled = canSwitchCamera,
            onClick = onSwitchCamera,
        )
    }
}

@Composable
private fun CameraTextControlButton(
    text: String,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = if (enabled) Color.Black.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.18f),
        enabled = enabled,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CameraIconControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = when {
            active -> Color.White.copy(alpha = 0.94f)
            enabled -> Color.Black.copy(alpha = 0.38f)
            else -> Color.Black.copy(alpha = 0.18f)
        },
        enabled = enabled,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    active -> Color(0xFF1A73E8)
                    enabled -> Color.White
                    else -> Color.White.copy(alpha = 0.42f)
                },
            )
        }
    }
}

@Composable
private fun rememberSessionElapsedTime(): String {
    val startedAt = remember { SystemClock.elapsedRealtime() }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsedSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1_000L
            delay(1_000L)
        }
    }
    return formatElapsedTime(elapsedSeconds)
}

private fun formatElapsedTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun formatZoomLabel(zoomRatio: Float): String {
    return if (zoomRatio >= 10f) {
        String.format(Locale.getDefault(), "%.0f", zoomRatio)
    } else {
        String.format(Locale.getDefault(), "%.1f", zoomRatio).trimEnd('0').trimEnd('.')
    }
}

private fun nextZoomRatio(
    currentZoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
): Float {
    val zoomStops = listOf(1f, 1.5f, 2f, 3f, 5f)
        .filter { it in minZoomRatio..maxZoomRatio }
        .ifEmpty { listOf(minZoomRatio, maxZoomRatio).distinct() }
    val next = zoomStops.firstOrNull { it > currentZoomRatio + 0.05f } ?: zoomStops.first()
    return next.coerceIn(minZoomRatio, maxZoomRatio)
}

@Composable
private fun ListeningControlTray(
    isMuted: Boolean,
    isStopping: Boolean,
    onDismiss: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = Color(0xFFF1F3F6),
                onClick = onDismiss,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close live camera",
                        tint = Color(0xFF34383F),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ListeningWaveform(
                    isMuted = isMuted,
                    onToggleMicrophone = onToggleMicrophone,
                )
                Text(
                    text = if (isMuted) "Muted" else "Listening...",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF20242A),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = Color(0xFFEF3B3B),
                onClick = onStop,
                enabled = !isStopping,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = "Stop Live Lens",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningWaveform(
    isMuted: Boolean,
    onToggleMicrophone: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        onClick = onToggleMicrophone,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMuted) {
                Icon(
                    imageVector = Icons.Outlined.MicOff,
                    contentDescription = "Unmute microphone",
                    tint = Color(0xFF7C8798),
                    modifier = Modifier.size(28.dp),
                )
            } else {
                listOf(16.dp, 24.dp, 13.dp, 28.dp, 19.dp, 31.dp, 21.dp, 27.dp, 15.dp).forEach { height ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(height)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6)),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceAiTopBar(
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
                .padding(horizontal = VoiceAiLayout.ScreenPadding, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(
                    text = "Gemini Live MLLM",
                    highlighted = true,
                    accentColor = MaterialTheme.colorScheme.primary,
                )
                AgentIconControlButton(
                    icon = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme",
                    active = isDarkTheme,
                    onClick = onToggleTheme,
                )
            }
            Text(
                text = "Live Lens",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Set the phone in front of your work. Live Lens refreshes visual context while you talk.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun PreSessionScreen(
    uiState: ConversationUiState,
    onStartRequested: () -> Unit,
    onDismissMessages: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Live ",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1F2328),
            )
            Text(
                text = "Lens",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A73E8),
            )
        }

        Text(
            text = "See. Ask. Solve.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2D3138),
        )

        Spacer(modifier = Modifier.weight(0.92f))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF8B5CF6),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(end = 160.dp, bottom = 92.dp)
                    .size(38.dp),
            )
            LiveLensLensMark()
        }

        Spacer(modifier = Modifier.weight(0.75f))

        Text(
            text = "Understand the world with your camera\nand voice.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF2E333A),
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
        )

        Spacer(modifier = Modifier.height(22.dp))

        LiveLensStartButton(
            text = if (uiState.isStarting) "Starting Live Session..." else "Start Live Session",
            enabled = uiState.isConfigured && !uiState.isStarting,
            onClick = onStartRequested,
        )

        if (!uiState.isConfigured && uiState.configMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            InlineNoticeCard(
                title = "Configuration needed",
                message = uiState.configMessage,
                accentColor = MaterialTheme.colorScheme.error,
                icon = Icons.Outlined.ErrorOutline,
            )
        }

        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            DismissibleMessageCard(
                title = "Action needed",
                message = uiState.errorMessage,
                accentColor = MaterialTheme.colorScheme.error,
                icon = Icons.Outlined.ErrorOutline,
                onDismiss = onDismissMessages,
            )
        } else if (uiState.warningMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            DismissibleMessageCard(
                title = "Heads up",
                message = uiState.warningMessage,
                accentColor = MaterialTheme.colorScheme.tertiary,
                icon = Icons.Outlined.WarningAmber,
                onDismiss = onDismissMessages,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiveLensScenarioRow(
                icon = Icons.Outlined.CenterFocusStrong,
                title = "Debug a device",
                subtitle = "Find and fix issues",
            )
            LiveLensScenarioRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "Understand an object",
                subtitle = "Get instant explanations",
            )
            LiveLensScenarioRow(
                icon = Icons.Outlined.Visibility,
                title = "Explore what's around you",
                subtitle = "Discover and learn",
            )
        }

        Spacer(modifier = Modifier.weight(0.2f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Powered by Gemini Live",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF777A80),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LiveLensLensMark() {
    Box(
        modifier = Modifier.size(132.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(Color.White)
                .border(8.dp, Color.White, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Color(0xFF121C2E)),
        )
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0xFF31547C), CircleShape)
                .background(Color(0xFF1B2E4A).copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF172B9B),
                            Color(0xFF130B45),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF27A7C8).copy(alpha = 0.72f)),
        )
    }
}

@Composable
private fun LiveLensStartButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = if (enabled) Color(0xFF1A73E8) else Color(0xFFB7C7E6),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "→",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun LiveLensScenarioRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF0F4FA),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0EBFF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF20242A),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF515760),
                )
            }
        }
    }
}

@Composable
private fun LiveLensHomeHero(
    uiState: ConversationUiState,
    onStartRequested: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LiveLensPreviewSurface(uiState = uiState)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgentButton(
                text = if (uiState.isStarting) "Starting Live Lens..." else "Start Live Lens",
                modifier = Modifier.weight(1f),
                enabled = uiState.isConfigured && !uiState.isStarting,
                onClick = onStartRequested,
            )
            AgentIconControlButton(
                icon = Icons.Outlined.Mic,
                contentDescription = "Voice input readiness",
                active = uiState.microphonePermissionGranted,
                onClick = onStartRequested,
            )
        }

        if (!uiState.isConfigured && uiState.configMessage != null) {
            InlineNoticeCard(
                title = "Configuration needed",
                message = uiState.configMessage,
                accentColor = MaterialTheme.colorScheme.error,
                icon = Icons.Outlined.ErrorOutline,
            )
        }
    }
}

@Composable
private fun LiveLensPreviewSurface(
    uiState: ConversationUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    )
                )
                .padding(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(22.dp),
                    ),
            )

            CameraScanFrame(
                modifier = Modifier
                    .matchParentSize()
                    .padding(22.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(
                    text = "Live camera",
                    highlighted = true,
                    accentColor = MaterialTheme.colorScheme.secondary,
                )
                StatusChip(
                    text = "Gemini 3.8 Live MLLM",
                    highlighted = true,
                    accentColor = MaterialTheme.colorScheme.primary,
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CenterFocusStrong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    text = "Frame an object or scene",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Live Lens watches your workspace at a steady pace while you talk through the task.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LensOverlayPill(
                    label = if (uiState.isConfigured) "Backend ready" else "Setup needed",
                    icon = Icons.Outlined.CloudDone,
                    active = uiState.isConfigured,
                )
                LensOverlayPill(
                    label = if (uiState.microphonePermissionGranted) "Voice ready" else "Mic needed",
                    icon = Icons.Outlined.Mic,
                    active = uiState.microphonePermissionGranted,
                )
            }
        }
    }
}

@Composable
private fun CameraScanFrame(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        listOf(
            Alignment.TopStart,
            Alignment.TopEnd,
            Alignment.BottomStart,
            Alignment.BottomEnd,
        ).forEach { alignment ->
            Box(
                modifier = Modifier
                    .align(alignment)
                    .size(42.dp)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                    ),
            )
        }
    }
}

@Composable
private fun LensOverlayPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.32f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LiveLensReadinessGrid(
    uiState: ConversationUiState,
) {
    ResponsiveInfoGrid(
        items = listOf(
            InfoItemModel(
                label = "Camera",
                value = if (uiState.cameraPermissionGranted) "Permission granted" else "Permission required",
            ),
            InfoItemModel(
                label = "Microphone",
                value = if (uiState.microphonePermissionGranted) "Permission granted" else "Permission required",
            ),
            InfoItemModel(
                label = "Backend",
                value = if (uiState.isConfigured) "Server configured" else "Server URL needed",
            ),
            InfoItemModel(
                label = "Model",
                value = "Gemini 3.8 Live MLLM",
            ),
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveLensPromptSuggestions() {
    val prompts = listOf(
        LiveLensPrompt("Identify", "What am I looking at?"),
        LiveLensPrompt("Compare", "How are these different?"),
        LiveLensPrompt("Read", "Read the visible text."),
        LiveLensPrompt("Explain", "Walk me through this scene."),
    )

    AgentCard(
        title = "Try with the camera",
        subtitle = "Prompt starters for the first Live MLLM pass.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            prompts.forEach { prompt ->
                LensPromptChip(prompt = prompt)
            }
        }
    }
}

@Composable
private fun LensPromptChip(
    prompt: LiveLensPrompt,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.TipsAndUpdates,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = prompt.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = prompt.helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ConnectedSessionScreen(
    uiState: ConversationUiState,
    bottomPadding: Dp,
    onDismissMessages: () -> Unit,
    onTextChanged: (String) -> Unit = {},
    onSendText: (Boolean, Boolean) -> Unit = { _, _ -> },
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

        item {
            AgentTextControls(uiState, onTextChanged, onSendText)
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
private fun AgentTextControls(
    uiState: ConversationUiState,
    onTextChanged: (String) -> Unit,
    onSendText: (Boolean, Boolean) -> Unit,
) {
    var speak by rememberSaveable { mutableStateOf(false) }
    var append by rememberSaveable { mutableStateOf(true) }
    val enabled = uiState.canSendText && !uiState.isStopping && !uiState.isSendingText
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Send text to Ada", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !speak, onClick = { speak = false }, enabled = enabled, label = { Text("Ask Ada") })
                FilterChip(selected = speak, onClick = { speak = true }, enabled = enabled, label = { Text("Read aloud") })
            }
            Text(
                if (speak) "Ada speaks your text exactly as written." else "Ada processes your text and responds.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = uiState.textDraft,
                onValueChange = onTextChanged,
                enabled = enabled,
                label = { Text(if (speak) "Text to read" else "Your message") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = append, onCheckedChange = { append = it }, enabled = enabled)
                Text("Queue instead of interrupting", style = MaterialTheme.typography.bodyMedium)
            }
            AgentButton(
                text = if (uiState.isSendingText) "Sending…" else "Send",
                enabled = enabled && uiState.textDraft.isNotBlank(),
                onClick = { onSendText(speak, append) },
            )
            uiState.textActionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
        title = "Live call snapshot",
        subtitle = "A compact view of the channel, transport health, and microphone state.",
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
        title = "Agent presence",
        subtitle = "Current agent state without the extra decoration.",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AgentAvatarBadge(
                name = "Agora AI",
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
        title = "Transcript",
        subtitle = "Realtime user and agent turns for debugging, screenshots, and README demos.",
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
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
                            text = "Transcript appears here once the session is live.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "The panel keeps both completed turns and the currently streaming line.",
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
            shape = RoundedCornerShape(22.dp),
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
                    text = if (isUser) "You" else "Agent",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.76f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = turn.text.ifBlank { "..." },
                    style = MaterialTheme.typography.bodyLarge,
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
        title = "Session diagnostics",
        subtitle = "Recent warnings and runtime signals surfaced by the realtime layer.",
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
                text = if (micEnabled) "Mute mic" else "Unmute mic",
                modifier = Modifier.weight(1f),
                variant = AgentButtonVariant.Secondary,
                onClick = onToggleMicrophone,
            )
            AgentButton(
                text = if (isStopping) "Ending..." else "End session",
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
private fun connectedStatusChips(uiState: ConversationUiState): List<StatusChipModel> {
    val agentJoined = uiState.agentVisualState != AgentVisualState.WAITING &&
        uiState.agentVisualState != AgentVisualState.DISCONNECTED

    return listOf(
        StatusChipModel(
            label = "Backend active",
            highlighted = true,
            accent = MaterialTheme.colorScheme.primary,
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
            label = if (agentJoined) "Agent joined" else "Waiting for agent",
            highlighted = agentJoined,
            accent = uiState.agentVisualState.accentColor(),
        ),
    )
}

private fun connectedInfoItems(uiState: ConversationUiState): List<InfoItemModel> {
    return listOf(
        InfoItemModel("Channel", uiState.channelName ?: "Joining..."),
        InfoItemModel("Local UID", uiState.localUid ?: "Pending"),
        InfoItemModel("RTM status", uiState.rtmConnectionLabel),
        InfoItemModel("Backend latency", uiState.backendLatencyMs?.let { "$it ms" } ?: "Pending"),
        InfoItemModel("Last server response", uiState.lastServerResponse ?: "Pending"),
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
        TurnState.AGENT_THINKING -> "Agent thinking"
        TurnState.AGENT_SPEAKING -> "Agent speaking"
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
        channelName = "demo-voice-room",
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
                text = "Hi there. I am ready to help with your Android voice AI testing.",
                status = TranscriptTurnStatus.END,
                createdAtMillis = 0L,
            ),
            TranscriptTurn(
                key = "2",
                turnId = 2L,
                streamId = 1L,
                speaker = TranscriptSpeaker.USER,
                text = "Can you summarize the current session state?",
                status = TranscriptTurnStatus.END,
                createdAtMillis = 1L,
            ),
        ),
        liveTranscript = TranscriptTurn(
            key = "3",
            turnId = 3L,
            streamId = 2L,
            speaker = TranscriptSpeaker.AGENT,
            text = "Agora REST is active, the microphone is ready, and the agent is currently speaking.",
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
