package com.agentai.core.model.evaluation

import kotlinx.serialization.json.Json

/**
 * JSONL dataset I/O (spec 0009 E2/E5).
 *
 * On-disk schema per line (one JSON object per line, UTF-8):
 * `{"id","category","prompt","expected_tool"|null,"expected_args"|null,"expected_rejection"|null,"notes"|null}`
 *
 * - [write] serializes records deterministically (stable field order, no pretty
 *   printing) so dataset files are diffable and version-controllable (E1).
 * - [read] parses exactly the same schema; a malformed line throws.
 *
 * Benchmark results are stored SEPARATELY from the architecture (E5) — this
 * file is the dataset format, not the results format (see EvaluationReport).
 */
object EvaluationDatasetIO {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun write(records: List<EvaluationRecord>): String =
        records.joinToString(separator = "\n") { json.encodeToString(EvaluationRecord.serializer(), it) } + "\n"

    fun read(content: String): List<EvaluationRecord> =
        content.lineSequence()
            .filter { it.isNotBlank() }
            .map { json.decodeFromString(EvaluationRecord.serializer(), it) }
            .toList()
}