package com.androidengineers.agent_quickstart_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.androidengineers.agent_quickstart_android.ui.ConversationScreen
import com.androidengineers.agent_quickstart_android.ui.ConversationViewModel
import com.androidengineers.agent_quickstart_android.ui.theme.AgentquickstartandroidTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<ConversationViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AgentquickstartandroidTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                    onToggleMicrophone = viewModel::toggleMicrophone,
                    onDismissMessages = viewModel::clearTransientMessages,
                )
            }
        }
    }
}
