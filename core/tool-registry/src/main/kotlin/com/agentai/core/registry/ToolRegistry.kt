package com.agentai.core.registry

import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolResult

/**
 * Registry of registered tools (spec 0005 G1).
 *
 * - Tool names are unique: duplicate [register] throws [IllegalArgumentException].
 * - [definitions] returns a sorted-by-name, stable order (deterministic prompts).
 * - [execute] NEVER throws: unknown/malformed/denied calls become [ToolResult.Failure].
 */
interface ToolRegistry {
    fun register(tool: Tool)

    fun unregister(name: String): Boolean

    fun get(name: String): Tool?

    fun definitions(): List<ToolDefinition>

    suspend fun execute(call: ToolCall): ToolResult
}