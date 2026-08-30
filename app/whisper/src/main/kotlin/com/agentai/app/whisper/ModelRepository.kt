package com.agentai.app.whisper

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads a remote model to a target file. Injectable so JVM tests can
 * substitute a fake without touching the network (spec 0010 V3).
 */
fun interface ModelDownloader {
    suspend fun download(url: String, target: File): File
}

/** Production downloader: plain HttpURLConnection over HTTPS (no extra deps). */
object HttpModelDownloader : ModelDownloader {
    override suspend fun download(url: String, target: File): File = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode} for $url"
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        target
    }
}

/**
 * On-device model cache with SHA-256 pinning (spec 0010 V3).
 *
 * Security: the model bytes are UNTRUSTED until they match [ModelEntry.sha256].
 * A cached file whose digest does not match is deleted and re-downloaded.
 */
class ModelRepository(
    private val cacheDir: File,
    private val downloader: ModelDownloader = HttpModelDownloader,
) {

    /**
     * Returns a verified local file for [entry], downloading it first if needed.
     * @throws IllegalStateException on checksum mismatch or HTTP failure.
     */
    suspend fun obtain(entry: ModelEntry): File {
        cacheDir.mkdirs()
        val target = File(cacheDir, entry.id)
        if (target.isFile && target.sha256Hex() == entry.sha256) {
            return target // valid cached copy — offline reuse (V3)
        }
        target.delete()
        downloader.download(entry.url, target)
        val actual = target.sha256Hex()
        check(actual == entry.sha256) {
            "Model checksum mismatch for ${entry.id}: expected ${entry.sha256}, got $actual"
        }
        check(target.length() >= entry.sizeBytes * 0.9) {
            "Model file too small for ${entry.id}: ${target.length()} bytes"
        }
        return target
    }

    private fun File.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}