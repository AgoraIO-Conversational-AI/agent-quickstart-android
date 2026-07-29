package com.androidengineers.agent_quickstart_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidengineers.agent_quickstart_android.ui.ConversationScreen
import com.androidengineers.agent_quickstart_android.ui.ConversationViewModel
import com.androidengineers.agent_quickstart_android.ui.theme.AgentquickstartandroidTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<ConversationViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()

            LaunchedEffect(systemDarkTheme) {
                viewModel.initializeTheme(systemDarkTheme)
            }

            AgentquickstartandroidTheme(darkTheme = uiState.isDarkTheme) {
                val context = LocalContext.current
                val currentViewModel by rememberUpdatedState(viewModel)
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    currentViewModel.updateMicrophonePermission(granted)
                    if (granted) {
                        currentViewModel.startConversation()
                    }
                }

                LaunchedEffect(Unit) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    currentViewModel.updateMicrophonePermission(granted)
                }

                BackHandler(enabled = uiState.inConversation) {
                    currentViewModel.endConversation()
                }

                ConversationScreen(
                    uiState = uiState,
                    onStartRequested = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        currentViewModel.updateMicrophonePermission(granted)
                        if (granted) {
                            currentViewModel.startConversation()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onEndConversation = viewModel::endConversation,
                    onDoneSpeaking = viewModel::doneSpeakingForCorrection,
                    onAskCoach = viewModel::askCoachAboutMistakes,
                    onToggleMicrophone = viewModel::toggleMicrophone,
                    onToggleTheme = viewModel::toggleTheme,
                    onPracticeModeSelected = viewModel::selectPracticeMode,
                    onDismissMessages = viewModel::clearTransientMessages,
                )
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            viewModel.endConversationForLifecycle()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            viewModel.endConversationForLifecycle()
        }
        super.onDestroy()
    }
}
