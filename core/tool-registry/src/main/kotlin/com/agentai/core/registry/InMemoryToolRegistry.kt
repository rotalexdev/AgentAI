package com.agentai.core.registry

import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolCallValidator
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolError
import com.agentai.core.contract.ToolResult

/**
 * In-memory [ToolRegistry] implementation (spec 0005 G1-G4).
 *
 * Determinism:
 * - insertion order is irrelevant; [definitions] is always sorted by name.
 * - [execute] maps failures to structured [ToolResult.Failure] values and
 *   never throws for unknown tools, malformed arguments, or tool errors.
 */
class InMemoryToolRegistry : ToolRegistry {

    private val toolsByName = LinkedHashMap<String, Tool>()

    override fun register(tool: Tool) {
        val name = tool.definition.name
        require(name !in toolsByName) { "Tool '$name' is already registered" }
        toolsByName[name] = tool
    }

    override fun unregister(name: String): Boolean = toolsByName.remove(name) != null

    override fun get(name: String): Tool? = toolsByName[name]

    override fun definitions(): List<ToolDefinition> =
        toolsByName.values.map { it.definition }.sortedBy { it.name }

    override suspend fun execute(call: ToolCall): ToolResult {
        val tool = toolsByName[call.name]
            ?: return ToolResult.Failure(call.name, ToolError.UNKNOWN_TOOL, "Unknown tool '${call.name}'")

        val validation = ToolCallValidator.validate(call, tool.definition)
        if (validation is ToolCallValidator.Result.Invalid) {
            return ToolResult.Failure(call.name, ToolError.MALFORMED_ARGUMENTS, validation.reason)
        }

        return try {
            tool.run(call.arguments)
        } catch (e: Exception) {
            ToolResult.Failure(call.name, ToolError.EXECUTION_ERROR, "Execution error: ${e.message}")
        }
    }
}