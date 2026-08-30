package com.agentai.core.registry

import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolResult
import com.agentai.core.security.PiiExposure
import com.agentai.core.security.SideEffect
import kotlinx.serialization.json.JsonObject

/**
 * A concrete tool implementation bound to platform APIs (spec 0007).
 *
 * [run] is the ONLY place Android/platform APIs are called for this tool.
 * Implementations must never contain UI logic or model adapter code.
 */
interface Tool {
    val definition: ToolDefinition

    /** Declared side-effect profile; drives rule-based classification (spec 0006 S2). */
    val sideEffect: SideEffect

    /** Declared PII exposure; drives rule-based classification (spec 0006 S2). */
    val piiExposure: PiiExposure

    /** Executes the tool with ALREADY-VALIDATED arguments and returns a structured result. */
    suspend fun run(arguments: JsonObject): ToolResult
}