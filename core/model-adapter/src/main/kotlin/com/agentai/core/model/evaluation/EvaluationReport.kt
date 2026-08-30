package com.agentai.core.model.evaluation

/**
 * Evaluation metrics (spec 0009 E4). The harness computes exactly these;
 * results are stored separately, never hard-coded into source (E5).
 */
data class Metrics(
    val toolSelectionAccuracy: Double,
    val argumentAccuracy: Double,
    val invalidCallRejection: Double,
    val falsePositiveRate: Double,
    val latencyMs: Double,
    val memoryKb: Double,
    val modelSizeMb: Double,
    val tokensPerSec: Double,
)

/**
 * Per-record outcome.
 */
data class RecordResult(
    val recordId: String,
    val passed: Boolean,
    val observed: String,
    val notes: String? = null,
    val latencyMs: Double = 0.0,
)

/**
 * Full evaluation report (spec 0009 E3). Versioned so results are comparable
 * across model adapters and dataset versions.
 */
data class EvaluationReport(
    val modelId: String,
    val datasetVersion: String,
    val toolDefinitionVersion: String,
    val metrics: Metrics,
    val records: List<RecordResult>,
)