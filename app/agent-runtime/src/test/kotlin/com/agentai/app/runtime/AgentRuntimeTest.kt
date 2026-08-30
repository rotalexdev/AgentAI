package com.agentai.app.runtime

import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolResult
import com.agentai.core.contract.ToolError
import com.agentai.core.model.AgentModel
import com.agentai.core.model.ModelRequest
import com.agentai.core.model.ModelResponse
import com.agentai.core.registry.InMemoryToolRegistry
import com.agentai.core.registry.Tool
import com.agentai.core.registry.ToolRegistry
import com.agentai.core.security.Approval
import com.agentai.core.security.ConfirmationGate
import com.agentai.core.security.DefaultToolPolicy
import com.agentai.core.security.PermissionEvaluator
import com.agentai.core.security.PiiExposure
import com.agentai.core.security.SideEffect
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED tests for spec 0002 scenarios — executed in Phase 1 Step 0 (ADR-0005 flip).
 */
class AgentRuntimeTest {

    private fun noopTool(name: String, sideEffect: SideEffect = SideEffect.NONE): Tool =
        object : Tool {
            override val definition = ToolDefinition(
                name = name,
                description = name,
                parameters = JsonSchemaType.ObjectType(properties = emptyMap(), required = emptyList()),
            )
            override val sideEffect = sideEffect
            override val piiExposure = PiiExposure.NONE
            override suspend fun run(arguments: JsonObject): ToolResult =
                ToolResult.Success(name, buildJsonObject { put("ok", true) })
        }

    private fun brightTool(): Tool = object : Tool {
        override val definition = ToolDefinition(
            name = "set_brightness",
            description = "Set brightness",
            parameters = JsonSchemaType.ObjectType(
                properties = mapOf("value" to JsonSchemaType.IntegerType(minimum = 0, maximum = 100)),
                required = listOf("value"),
            ),
        )
        override val sideEffect = SideEffect.MUTATES_SYSTEM
        override val piiExposure = PiiExposure.NONE
        override suspend fun run(arguments: JsonObject): ToolResult =
            ToolResult.Success("set_brightness", buildJsonObject { put("value", 50) })
    }

    private fun registry(vararg tools: Tool): ToolRegistry = InMemoryToolRegistry().also { r ->
        tools.forEach { r.register(it) }
    }

    private fun policy(): com.agentai.core.security.ToolPolicy = DefaultToolPolicy(
        permissionEvaluator = PermissionEvaluator { true },
    )

    private fun singleToolModel(): AgentModel = object : AgentModel {
        override suspend fun generate(request: ModelRequest): ModelResponse =
            ModelResponse.ToolCalls(
                listOf(ToolCall(id = "c1", name = "set_brightness", arguments = buildJsonObject { put("value", 50) })),
            )
    }

    @Test
    fun `single-shot happy path completes with result`() = runTest {
        val runtime = AgentRuntime(
            model = singleToolModel(),
            registry = registry(brightTool()),
            policy = policy(),
            confirmationGate = ConfirmationGate { Approval.Granted },
        )
        runtime.submit("set brightness to 50")
        val state = runtime.state.value
        assertEquals(AgentStatus.COMPLETE, state.status)
        assertTrue(state.result is ToolResult.Success)
    }

    @Test
    fun `n greater than 1 tool calls are capped to first`() = runTest {
        // spec 0002: GIVEN ModelResponse.ToolCalls with n>1, THEN only the first executes
        val model = object : AgentModel {
            override suspend fun generate(request: ModelRequest): ModelResponse =
                ModelResponse.ToolCalls(
                    listOf(
                        ToolCall("a", "set_brightness", buildJsonObject { put("value", 10) }),
                        ToolCall("b", "set_brightness", buildJsonObject { put("value", 90) }),
                    ),
                )
        }
        var executions = 0
        val tool = object : Tool {
            override val definition = brightTool().definition
            override val sideEffect = SideEffect.MUTATES_SYSTEM
            override val piiExposure = PiiExposure.NONE
            override suspend fun run(arguments: JsonObject): ToolResult {
                executions++
                return ToolResult.Success("set_brightness", arguments)
            }
        }
        val runtime = AgentRuntime(
            model = model,
            registry = registry(tool),
            policy = policy(),
            confirmationGate = ConfirmationGate { Approval.Granted },
        )
        runtime.submit("set brightness twice")
        assertEquals(1, executions)
        assertEquals(AgentStatus.COMPLETE, runtime.state.value.status)
    }

    @Test
    fun `confirmation required transitions to AWAITING_CONFIRMATION then denied fail-closed`() = runTest {
        // spec 0002 R3 + spec 0006 S5: no gate approval => deny (fail-closed), never executed
        var executed = false
        val tool = object : Tool {
            override val definition = brightTool().definition
            override val sideEffect = SideEffect.MUTATES_SYSTEM
            override val piiExposure = PiiExposure.NONE
            override suspend fun run(arguments: JsonObject): ToolResult {
                executed = true
                return ToolResult.Success("set_brightness", arguments)
            }
        }
        val runtime = AgentRuntime(
            model = singleToolModel(),
            registry = registry(tool),
            policy = policy(),
            confirmationGate = ConfirmationGate { Approval.Denied("user said no") },
        )
        runtime.submit("set brightness to 50")
        assertEquals(AgentStatus.COMPLETE, runtime.state.value.status)
        val result = runtime.state.value.result
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.CONFIRMATION_REQUIRED, (result as ToolResult.Failure).error)
        assertEquals(false, executed)
    }

    @Test
    fun `SAFE tool runs without confirmation`() = runTest {
        val runtime = AgentRuntime(
            model = object : AgentModel {
                override suspend fun generate(request: ModelRequest): ModelResponse =
                    ModelResponse.ToolCalls(listOf(ToolCall("c1", "get_battery_status", buildJsonObject {})))
            },
            registry = registry(noopTool("get_battery_status")),
            policy = policy(),
            confirmationGate = ConfirmationGate { Approval.Denied("should never be asked") },
        )
        runtime.submit("battery?")
        assertEquals(AgentStatus.COMPLETE, runtime.state.value.status)
        assertTrue(runtime.state.value.result is ToolResult.Success)
    }
}