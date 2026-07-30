package com.androidengineers.agent_quickstart_android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.PracticeMode


@Composable
fun ConversationScreen(
    uiState: ConversationUiState,
    onStartRequested: () -> Unit,
    onEndConversation: () -> Unit,
    onDoneSpeaking: () -> Unit,
    onAskCoach: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleTheme: () -> Unit,
    onPracticeModeSelected: (PracticeMode) -> Unit,
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
        onPracticeModeSelected = onPracticeModeSelected,
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
    onPracticeModeSelected: (PracticeMode) -> Unit,
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
            Box(
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
                            onToggleMicrophone = onToggleMicrophone,
                        )
                    } else {
                        HomeSpeakScreen(
                            uiState = uiState,
                            onStartRequested = onStartRequested,
                            onPracticeModeSelected = onPracticeModeSelected,
                            onDismissMessages = onDismissMessages,
                        )
                    }
                }
            }
        }
    }
}
