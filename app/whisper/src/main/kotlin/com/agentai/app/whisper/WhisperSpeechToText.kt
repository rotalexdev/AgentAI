package com.agentai.app.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whisper-backed [SpeechToText] (spec 0010 V2).
 *
 * Flow per utterance: ensure verified model → init native context → transcribe
 * (auto-detect language + translate to English) → free context. The native
 * call is blocking, so it runs on [Dispatchers.Default].
 */
class WhisperSpeechToText(
    private val modelRepository: ModelRepository,
    private val model: ModelEntry = ModelCatalog.default,
    private val numThreads: Int = 4,
) : SpeechToText {

    override suspend fun transcribe(pcm: FloatArray): SpeechResult = withContext(Dispatchers.Default) {
        val modelFile = modelRepository.obtain(model)
        val context = WhisperLib.initContext(modelFile.absolutePath)
        check(context != 0L) { "Whisper failed to load model ${model.id}" }
        try {
            val text = WhisperLib.transcribe(context, pcm, numThreads).trim()
            val language = WhisperLib.getDetectedLanguage(context)
            SpeechResult(text = text, detectedLanguage = language)
        } finally {
            WhisperLib.freeContext(context)
        }
    }
}