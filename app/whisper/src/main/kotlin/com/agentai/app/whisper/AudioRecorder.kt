package com.agentai.app.whisper

/**
 * Audio capture boundary (spec 0010 V5). Hold-to-talk semantics:
 * [start] begins capture, [stop] ends it and returns the full clip.
 */
interface AudioRecorder {
    /** Begin capturing. Throws if capture cannot start (e.g. no permission). */
    fun start()

    /** Stop capturing and return the recorded audio as 16 kHz mono float PCM in -1..1. */
    fun stop(): FloatArray
}