package com.agentai.app.whisper

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Android [AudioRecorder] via AudioRecord (spec 0010 V5).
 *
 * Captures 16 kHz mono PCM16 from VOICE_RECOGNITION and converts to float PCM
 * (-1..1) for Whisper. Runs the read loop on a background thread while
 * [stop] concatenates the chunks synchronously.
 */
class AndroidAudioRecorder(
    private val sampleRate: Int = 16_000,
) : AudioRecorder {

    private var recorder: AudioRecord? = null
    private val chunks = mutableListOf<ShortArray>()
    @Volatile private var capturing = false
    private var readThread: Thread? = null

    override fun start() {
        if (capturing) return
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuffer > 0) { "AudioRecord.getMinBufferSize returned invalid size" }
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord init failed — RECORD_AUDIO permission denied?"
        }
        recorder = record
        chunks.clear()
        capturing = true
        record.startRecording()
        readThread = Thread {
            val buffer = ShortArray(minBuffer / 2)
            while (capturing) {
                val n = record.read(buffer, 0, buffer.size)
                if (n > 0) {
                    synchronized(chunks) { chunks.add(buffer.copyOf(n)) }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    override fun stop(): FloatArray {
        if (!capturing) return FloatArray(0)
        capturing = false
        readThread?.join(2_000)
        val record = recorder
        recorder = null
        try {
            record?.stop()
        } catch (_: IllegalStateException) {
            // already stopped
        }
        record?.release()

        val shorts: ShortArray = synchronized(chunks) {
            val total = chunks.sumOf { it.size }
            ShortArray(total).also { out ->
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(out, offset)
                    offset += chunk.size
                }
            }.also { chunks.clear() }
        }
        return FloatArray(shorts.size) { shorts[it] / 32768f }
    }
}