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
                viewModel.initializeTheme(false)
            }

            AgentquickstartandroidTheme(darkTheme = uiState.isDarkTheme) {
                val context = LocalContext.current
                val rtcEngine by viewModel.rtcEngineState.collectAsStateWithLifecycle()
                val currentViewModel by rememberUpdatedState(viewModel)
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    val cameraGranted = grants[Manifest.permission.CAMERA] == true
                    val microphoneGranted = grants[Manifest.permission.RECORD_AUDIO] == true
                    currentViewModel.updateCameraPermission(cameraGranted)
                    currentViewModel.updateMicrophonePermission(microphoneGranted)
                    if (cameraGranted && microphoneGranted) {
                        currentViewModel.startConversation()
                    }
                }

                LaunchedEffect(Unit) {
                    val cameraGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    val microphoneGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    currentViewModel.updateCameraPermission(cameraGranted)
                    currentViewModel.updateMicrophonePermission(microphoneGranted)
                }

                ConversationScreen(
                    uiState = uiState,
                    rtcEngine = rtcEngine,
                    onStartRequested = {
                        val cameraGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        val microphoneGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        currentViewModel.updateCameraPermission(cameraGranted)
                        currentViewModel.updateMicrophonePermission(microphoneGranted)
                        if (cameraGranted && microphoneGranted) {
                            currentViewModel.startConversation()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.RECORD_AUDIO,
                                )
                            )
                        }
                    },
                    onEndConversation = viewModel::endConversation,
                    onToggleMicrophone = viewModel::toggleMicrophone,
                    onToggleTheme = viewModel::toggleTheme,
                    onDismissMessages = viewModel::clearTransientMessages,
                    onTextChanged = viewModel::updateTextDraft,
                    onSendText = viewModel::sendText,
                    onAnalyzeCameraFrame = viewModel::analyzeCameraFrame,
                )
            }
        }
    }
}
