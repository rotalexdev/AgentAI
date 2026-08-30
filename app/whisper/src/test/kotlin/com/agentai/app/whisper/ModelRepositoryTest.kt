package com.agentai.app.whisper

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Fake downloader writing a fixed payload (no network). */
private class FakeDownloader(private val payload: ByteArray) : ModelDownloader {
    var downloads = 0

    override suspend fun download(url: String, target: File): File {
        downloads++
        target.writeBytes(payload)
        return target
    }
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

class ModelRepositoryTest {

    @TempDir
    lateinit var cacheDir: File

    private val payload = "whisper-model-bytes".toByteArray()
    private val entry = ModelEntry(
        id = "ggml-base-q5_1.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        sha256 = sha256Hex(payload),
        sizeBytes = payload.size.toLong(),
    )

    @Test
    fun `fresh download writes verified file`() = runTest {
        val downloader = FakeDownloader(payload)
        val repo = ModelRepository(cacheDir, downloader)
        val file = repo.obtain(entry)
        assertEquals(entry.id, file.name)
        assertEquals(payload.toList(), file.readBytes().toList())
        assertEquals(1, downloader.downloads)
        assertTrue(cacheDir.listFiles().any { it.name == entry.id })
    }

    @Test
    fun `valid cached file skips download`() = runTest {
        val cached = File(cacheDir, entry.id)
        cached.writeBytes(payload)
        val downloader = FakeDownloader(payload)
        val repo = ModelRepository(cacheDir, downloader)
        val file = repo.obtain(entry)
        assertEquals(cached.absolutePath, file.absolutePath)
        assertEquals(0, downloader.downloads) // offline reuse
    }

    @Test
    fun `corrupt cached file is re-downloaded`() = runTest {
        val cached = File(cacheDir, entry.id)
        cached.writeBytes("corrupt".toByteArray())
        val downloader = FakeDownloader(payload)
        val repo = ModelRepository(cacheDir, downloader)
        val file = repo.obtain(entry)
        assertEquals(payload.toList(), file.readBytes().toList())
        assertEquals(1, downloader.downloads)
    }

    @Test
    fun `checksum mismatch fails closed`() = runTest {
        val badPayload = "different-bytes".toByteArray()
        val downloader = FakeDownloader(badPayload)
        val repo = ModelRepository(cacheDir, downloader)
        assertThrows(IllegalStateException::class.java) {
            repo.obtain(entry)
        }
        // failed download must NOT leave a usable model file
        assertTrue(cacheDir.listFiles().orEmpty().none { it.name == entry.id })
    }

    @Test
    fun `undersized file fails closed`() = runTest {
        val downloader = FakeDownloader("tiny".toByteArray())
        val repo = ModelRepository(cacheDir, downloader)
        assertThrows(IllegalStateException::class.java) {
            repo.obtain(entry)
        }
    }
}