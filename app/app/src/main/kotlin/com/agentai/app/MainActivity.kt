package com.agentai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentai.app.ui.AgentScreen
import com.agentai.app.ui.AgentViewModel

/**
 * Entry activity (spec 0008 U1). No business logic; delegates to the UI surface.
 *
 * Requests RECORD_AUDIO at first composition (spec 0010 V7) and passes the
 * grant state to the voice UI so hold-to-talk stays disabled until granted.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AgentApp

        setContent {
            var micGranted by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED,
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> micGranted = granted }

            LaunchedEffect(Unit) {
                if (!micGranted) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            AgentScreen(
                viewModel = viewModel {
                    AgentViewModel(app.runtime, app.voiceController)
                },
                confirmationGate = app.confirmationGate,
                micPermissionGranted = micGranted,
            )
        }
    }
}