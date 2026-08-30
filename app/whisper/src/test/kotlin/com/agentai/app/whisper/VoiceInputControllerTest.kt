package com.agentai.app.whisper

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fake recorder: captures start/stop and returns a fixed PCM clip. */
private class FakeRecorder(private val clip: FloatArray = FloatArray(0)) : AudioRecorder {
    var started = false
    var stopped = false
    var failStart = false

    override fun start() {
        if (failStart) throw IllegalStateException("boom")
        started = true
    }

    override fun stop(): FloatArray {
        stopped = true
        return clip
    }
}

/** Fake STT: returns fixed result or throws. */
private class FakeSpeechToText(
    private val text: String = "set brightness to 50",
    private val language: String = "en",
    private val fail: Boolean = false,
) : SpeechToText {
    override suspend fun transcribe(pcm: FloatArray): SpeechResult {
        if (fail) throw IllegalStateException("transcribe boom")
        return SpeechResult(text = text, detectedLanguage = language)
    }
}

class VoiceInputControllerTest {

    @Test
    fun `starts Idle`() {
        val controller = VoiceInputController(FakeRecorder(), FakeSpeechToText(), kotlinx.coroutines.test.TestScope())
        assertEquals(VoiceInputState.Idle, controller.state.value)
    }

    @Test
    fun `startRecording transitions Idle to Recording`() = runTest {
        val recorder = FakeRecorder()
        val controller = VoiceInputController(recorder, FakeSpeechToText(), this)
        controller.startRecording()
        assertEquals(VoiceInputState.Recording, controller.state.value)
        assertTrue(recorder.started)
    }

    @Test
    fun `startRecording is no-op while Recording`() = runTest {
        val recorder = FakeRecorder()
        val controller = VoiceInputController(recorder, FakeSpeechToText(), this)
        controller.startRecording()
        controller.startRecording() // second press while recording
        assertEquals(VoiceInputState.Recording, controller.state.value)
    }

    @Test
    fun `stopRecordingAndTranscribe runs callback with text`() = runTest {
        val recorder = FakeRecorder()
        val stt = FakeSpeechToText(text = "set volume to 30", language = "es")
        val controller = VoiceInputController(recorder, stt, this)
        var transcribed: String? = null
        controller.startRecording()
        controller.stopRecordingAndTranscribe { transcribed = it }
        testScheduler.advanceUntilIdle()
        assertEquals("set volume to 30", transcribed)
        assertEquals(VoiceInputState.Idle, controller.state.value)
        assertTrue(recorder.stopped)
    }

    @Test
    fun `blank text does not invoke callback`() = runTest {
        val controller = VoiceInputController(
            FakeRecorder(),
            FakeSpeechToText(text = "   "),
            this,
        )
        var called = false
        controller.startRecording()
        controller.stopRecordingAndTranscribe { called = true }
        testScheduler.advanceUntilIdle()
        assertFalse(called)
        assertEquals(VoiceInputState.Idle, controller.state.value)
    }

    @Test
    fun `STT failure surfaces Error`() = runTest {
        val controller = VoiceInputController(
            FakeRecorder(),
            FakeSpeechToText(fail = true),
            this,
        )
        controller.startRecording()
        controller.stopRecordingAndTranscribe {}
        testScheduler.advanceUntilIdle()
        assertTrue(controller.state.value is VoiceInputState.Error)
    }

    @Test
    fun `recorder start failure surfaces Error`() = runTest {
        val recorder = FakeRecorder().apply { failStart = true }
        val controller = VoiceInputController(recorder, FakeSpeechToText(), this)
        controller.startRecording()
        assertTrue(controller.state.value is VoiceInputState.Error)
    }

    @Test
    fun `stopRecording without recording is no-op`() = runTest {
        val recorder = FakeRecorder()
        val controller = VoiceInputController(recorder, FakeSpeechToText(), this)
        controller.stopRecordingAndTranscribe {}
        assertFalse(recorder.stopped)
        assertEquals(VoiceInputState.Idle, controller.state.value)
    }

    @Test
    fun `reset returns to Idle`() = runTest {
        val controller = VoiceInputController(
            FakeRecorder(),
            FakeSpeechToText(fail = true),
            this,
        )
        controller.startRecording()
        controller.stopRecordingAndTranscribe {}
        testScheduler.advanceUntilIdle()
        assertTrue(controller.state.value is VoiceInputState.Error)
        controller.reset()
        assertEquals(VoiceInputState.Idle, controller.state.value)
    }
}