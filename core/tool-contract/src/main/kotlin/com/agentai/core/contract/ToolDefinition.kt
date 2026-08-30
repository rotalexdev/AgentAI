package com.agentai.core.contract

import kotlinx.serialization.Serializable

/**
 * Declarative description of a tool exposed to the model (spec 0004 C1).
 *
 * `parameters` is the bounded JSON Schema object describing the expected
 * argument shape. The schema is the single source of truth used for
 * validation before execution; the model NEVER bypasses it.
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonSchemaType.ObjectType,
)