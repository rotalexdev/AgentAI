package com.agentai.app.whisper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Deterministic voice input state machine (spec 0010 V6).
 *
 * Owns the AudioRecorder + SpeechToText and exposes [state] to the UI. The
 * transcription callback is only invoked for non-blank text so empty
 * utterances never reach the agent.
 */
class VoiceInputController(
    private val recorder: AudioRecorder,
    private val speechToText: SpeechToText,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    /** Hold-to-talk press: Idle/Error → Recording. No-op while already active. */
    fun startRecording() {
        val current = _state.value
        if (current != VoiceInputState.Idle && current !is VoiceInputState.Error) return
        try {
            recorder.start()
            _state.value = VoiceInputState.Recording
        } catch (e: Exception) {
            _state.value = VoiceInputState.Error(e.message ?: "Failed to start recording")
        }
    }

    /** Hold-to-talk release: Recording → Transcribing → Idle, then [onTranscribed]. */
    fun stopRecordingAndTranscribe(onTranscribed: (String) -> Unit) {
        if (_state.value != VoiceInputState.Recording) return
        val pcm = try {
            recorder.stop()
        } catch (e: Exception) {
            _state.value = VoiceInputState.Error(e.message ?: "Failed to stop recording")
            return
        }
        _state.value = VoiceInputState.Transcribing
        scope.launch {
            try {
                val result = speechToText.transcribe(pcm)
                _state.value = VoiceInputState.Idle
                if (result.text.isNotBlank()) {
                    onTranscribed(result.text)
                }
            } catch (e: Exception) {
                _state.value = VoiceInputState.Error(e.message ?: "Transcription failed")
            }
        }
    }

    fun reset() {
        _state.value = VoiceInputState.Idle
    }
}