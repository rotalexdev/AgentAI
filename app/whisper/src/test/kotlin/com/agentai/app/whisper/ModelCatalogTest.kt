package com.agentai.app.whisper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelCatalogTest {

    @Test
    fun `default model uses HTTPS source`() {
        assertTrue(ModelCatalog.default.url.startsWith("https://"))
        assertTrue(ModelCatalog.default.url.contains("huggingface.co"))
    }

    @Test
    fun `default model has pinned 64-hex sha256`() {
        val sha = ModelCatalog.default.sha256
        assertEquals(64, sha.length)
        assertTrue(sha.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
    }

    @Test
    fun `default model id and size are sane`() {
        assertTrue(ModelCatalog.default.id.endsWith(".bin"))
        assertFalse(ModelCatalog.default.id.isBlank())
        assertTrue(ModelCatalog.default.sizeBytes > 10_000_000) // base-q5_1 ~60MB
    }
}