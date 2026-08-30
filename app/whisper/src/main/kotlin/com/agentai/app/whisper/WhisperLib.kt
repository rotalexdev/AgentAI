package com.agentai.app.whisper

/**
 * JNI bridge to whisper.cpp (spec 0010 V2).
 *
 * The native symbols live in `libwhisper-jni.so` built by CMake (FetchContent
 * pulls whisper.cpp v1.7.4). The companion functions map 1:1 to the C bridge
 * in `src/main/cpp/whisper_jni.c`.
 *
 * Security note: the caller MUST only pass a model file that was verified by
 * [ModelRepository] (SHA-256 pinned); the native layer treats the model bytes
 * as trusted input but the Kotlin boundary never does.
 */
class WhisperLib {

    companion object {
        init {
            System.loadLibrary("whisper-jni")
        }

        /** Initialize a whisper context from a model file; returns an opaque handle. */
        external fun initContext(modelPath: String): Long

        /**
         * Transcribe 16 kHz mono float PCM (values in -1..1). Auto-detects the
         * source language and returns ENGLISH text (translate=true).
         */
        external fun transcribe(context: Long, audioData: FloatArray, numThreads: Int): String

        /** Detected source language (ISO 639-1) of the last transcription. */
        external fun getDetectedLanguage(context: Long): String

        /** Release the context. MUST be called in a finally block. */
        external fun freeContext(context: Long)
    }
}