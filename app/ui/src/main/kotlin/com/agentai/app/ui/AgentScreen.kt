package com.agentai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agentai.app.runtime.AgentStatus
import com.agentai.app.whisper.VoiceInputState

/**
 * Compose + Material 3 surface (spec 0008 U1). Observes the ViewModel's
 * StateFlow; renders the confirmation dialog as ONE ConfirmationGate
 * implementation ([UiConfirmationGate]) on AWAITING_CONFIRMATION (U4).
 *
 * Voice input (spec 0010 V7): a hold-to-talk surface uses
 * [detectTapGestures] — press starts recording, release stops and
 * transcribes. The mic surface is disabled until [micPermissionGranted].
 */
@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    confirmationGate: UiConfirmationGate,
    micPermissionGranted: Boolean = true,
) {
    val state by viewModel.state.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    var input by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Local agent", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Instruction") },
            )
            Button(
                onClick = {
                    viewModel.submit(input.trim())
                    input = ""
                },
                enabled = state.status == AgentStatus.IDLE || state.status == AgentStatus.COMPLETE,
            ) { Text("Run") }

            // Hold-to-talk voice input (spec 0010 V7).
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(micPermissionGranted) {
                        if (micPermissionGranted) {
                            detectTapGestures(
                                onPress = { viewModel.startVoice() },
                                onRelease = { viewModel.stopVoice() },
                            )
                        }
                    }
                    .background(voiceSurfaceColor(voiceState, micPermissionGranted)),
                color = Color.Transparent,
            ) {
                Text(
                    text = when (voiceState) {
                        VoiceInputState.Idle ->
                            if (micPermissionGranted) "Hold to talk"
                            else "Microphone permission required"
                        VoiceInputState.Recording -> "Recording… release to transcribe"
                        VoiceInputState.Transcribing -> "Transcribing…"
                        is VoiceInputState.Error -> "Voice error: ${voiceState.message}"
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }

            state.result?.let { result ->
                Text("Result: $result")
            }
            state.reply?.let { reply ->
                Text("Reply: $reply")
            }
        }
    }

    // Confirmation dialog: renders tool name + args, routes Approve/Deny
    // through the gate (spec 0008 U4).
    val pending = confirmationGate.request.collectAsState().value
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmationGate.deny("dismissed") },
            title = { Text("Confirm ${pending.name}") },
            text = { Text("Arguments: ${pending.arguments}") },
            confirmButton = {
                TextButton(onClick = { confirmationGate.grant() }) { Text("Approve") }
            },
            dismissButton = {
                TextButton(onClick = { confirmationGate.deny() }) { Text("Deny") }
            },
        )
    }
}

private fun voiceSurfaceColor(voiceState: VoiceInputState, permission: Boolean): Color =
    when {
        !permission -> MaterialTheme.colorScheme.surfaceVariant
        voiceState is VoiceInputState.Error -> MaterialTheme.colorScheme.errorContainer
        voiceState is VoiceInputState.Recording -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }