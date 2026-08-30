package com.agentai.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentai.app.runtime.AgentRuntime
import com.agentai.app.runtime.AgentState
import com.agentai.app.whisper.VoiceInputController
import com.agentai.app.whisper.VoiceInputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI-facing ViewModel (spec 0008 U2): exposes the runtime state as
 * [StateFlow] to Compose. The UI NEVER executes tools directly (U3);
 * it only calls [submit] / [confirm] on the runtime.
 *
 * Voice input (spec 0010 V7): [voiceState] mirrors the voice pipeline and
 * [startVoice]/[stopVoice] bridge hold-to-talk presses to the controller.
 * The transcribed text is submitted through the SAME [submit] path — the
 * runtime never knows the input was spoken.
 */
class AgentViewModel(
    private val runtime: AgentRuntime,
    private val voiceController: VoiceInputController? = null,
) : ViewModel() {

    val state: StateFlow<AgentState> = runtime.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = runtime.state.value,
    )

    val voiceState: StateFlow<VoiceInputState> =
        voiceController?.state ?: MutableStateFlow(VoiceInputState.Idle).asStateFlow()

    fun submit(text: String) {
        viewModelScope.launch { runtime.submit(text) }
    }

    fun confirm(approved: Boolean) {
        val pending = state.value.pendingCall ?: return
        viewModelScope.launch { runtime.confirm(pending.id, approved) }
    }

    fun reset() {
        runtime.reset()
    }

    /** Hold-to-talk press. */
    fun startVoice() {
        voiceController?.startRecording()
    }

    /** Hold-to-talk release: transcribes and submits non-blank text to the runtime. */
    fun stopVoice() {
        voiceController?.stopRecordingAndTranscribe { text -> submit(text) }
    }
}