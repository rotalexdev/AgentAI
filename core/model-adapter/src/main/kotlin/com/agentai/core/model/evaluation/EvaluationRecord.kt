package com.agentai.core.model.evaluation

import kotlinx.serialization.Serializable

/**
 * Dataset categories (spec 0009 E1). 10 categories x 12 records = 120 total.
 */
@Serializable
enum class EvaluationCategory {
    DIRECT_COMMAND,
    PARAPHRASE,
    AMBIGUOUS,
    INVALID_ARGUMENTS,
    UNKNOWN_TOOL,
    MULTIPLE_TOOL_REQUESTS,
    IRRELEVANT,
    MALFORMED_JSON,
    MALICIOUS,
    PROMPT_INJECTION,
}

/**
 * One evaluation record (spec 0009 E2).
 *
 * JSONL schema on disk:
 * `{"id","category","prompt","expected_tool"|null,"expected_args"|null,"expected_rejection"|null,"notes"}`
 *
 * Exactly one expectation applies:
 * - expectedTool + expectedArgs: the model must call that tool with those args.
 * - expectedTool + null args: correct tool, any valid args.
 * - expectedRejection: the model must NOT produce a tool call (or must refuse).
 */
@Serializable
data class EvaluationRecord(
    val id: String,
    val category: EvaluationCategory,
    val prompt: String,
    val expectedTool: String? = null,
    val expectedArgs: Map<String, Any?>? = null,
    val expectedRejection: String? = null,
    val notes: String? = null,
)