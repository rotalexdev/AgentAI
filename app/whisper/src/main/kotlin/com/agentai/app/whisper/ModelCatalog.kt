package com.agentai.app.whisper

/**
 * A downloadable on-device Whisper model (spec 0010 V4).
 *
 * @param id cache file name on device.
 * @param url HTTPS source (HuggingFace `ggerganov/whisper.cpp`).
 * @param sha256 pinned SHA-256 of the file bytes — the model is UNTRUSTED
 *   until it matches this digest.
 * @param sizeBytes expected file size in bytes (informational, sanity check).
 */
data class ModelEntry(
    val id: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
)

/**
 * Static catalog of supported models (spec 0010 V4).
 *
 * Default: ggml-base-q5_1.bin — multilingual 60 MB model, ~8x realtime on CPU.
 * One multilingual model handles ALL languages with auto-detect + translate;
 * there are no per-language Whisper models.
 */
object ModelCatalog {

    /** Checksum verified 2026-08-17 against HuggingFace LFS metadata. */
    val default: ModelEntry = ModelEntry(
        id = "ggml-base-q5_1.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        sizeBytes = 62_600_000, // ~59.7 MB
    )
}