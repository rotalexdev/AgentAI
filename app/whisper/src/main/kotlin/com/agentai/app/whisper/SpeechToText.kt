package com.agentai.app.whisper

/**
 * Result of a speech-to-text call (spec 0010 V2).
 *
 * @param text English text produced by the transcription (already translated
 *   from the source language; empty string when nothing was recognized).
 * @param detectedLanguage ISO 639-1 code of the source language, e.g. "en",
 *   "es", "de" — detected automatically by Whisper.
 */
data class SpeechResult(
    val text: String,
    val detectedLanguage: String,
)

/**
 * Model-independent speech-to-text abstraction (spec 0010 V2).
 *
 * Mirrors the AgentModel philosophy from spec 0003: the rest of the system
 * depends on this interface, NOT on Whisper directly. Whisper is only one
 * possible speech engine.
 */
interface SpeechToText {
    /** Transcribe 16 kHz mono float PCM (values in -1..1) to English text. */
    suspend fun transcribe(pcm: FloatArray): SpeechResult
}