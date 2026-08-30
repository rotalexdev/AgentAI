package com.agentai.core.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Structured outcome of a tool execution (spec 0004 C1). Every execution
 * produces a [ToolResult]; tools never throw for user-facing failures.
 */
@Serializable
sealed interface ToolResult {
    val toolName: String

    @Serializable
    data class Success(
        override val toolName: String,
        val output: JsonObject,
    ) : ToolResult

    @Serializable
    data class Failure(
        override val toolName: String,
        val error: ToolError,
        val message: String,
    ) : ToolResult
}

/**
 * Canonical failure taxonomy (spec 0004 C1). Mapped deterministically by the
 * registry, validator, and policy chain; never derived from model text.
 */
enum class ToolError {
    UNKNOWN_TOOL,
    MALFORMED_ARGUMENTS,
    PERMISSION_DENIED,
    CONFIRMATION_REQUIRED,
    EXECUTION_ERROR,
}