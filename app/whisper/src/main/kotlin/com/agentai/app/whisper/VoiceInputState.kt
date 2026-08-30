package com.agentai.app.whisper

/**
 * Voice input lifecycle (spec 0010 V6).
 *
 * Idle → Recording → Transcribing → Idle | Error
 *             ↑                              │
 *             └────── (retry: start again) ───┘
 */
sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    data object Recording : VoiceInputState
    data object Transcribing : VoiceInputState
    data class Error(val message: String) : VoiceInputState
}