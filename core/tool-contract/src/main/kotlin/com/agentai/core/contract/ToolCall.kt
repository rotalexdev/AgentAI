package com.agentai.core.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A structured tool-call request produced by a model (spec 0004 C1).
 *
 * [arguments] is UNTRUSTED data until it passes [ToolCallValidator]
 * against the tool's [ToolDefinition] (spec 0004 C4). Never execute a
 * [ToolCall] without validating it first.
 */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)