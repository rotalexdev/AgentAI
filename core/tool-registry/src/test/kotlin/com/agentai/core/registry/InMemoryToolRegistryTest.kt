package com.agentai.core.registry

import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolError
import com.agentai.core.contract.ToolResult
import com.agentai.core.security.PiiExposure
import com.agentai.core.security.SideEffect
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED tests for spec 0005 scenarios — to be executed in Phase 1 Step 0
 * once the JVM test runner exists (ADR-0005 strict_tdd flip).
 */
class InMemoryToolRegistryTest {

    private fun tool(name: String): Tool =
        object : Tool {
            override val definition = ToolDefinition(
                name = name,
                description = "Tool $name",
                parameters = JsonSchemaType.ObjectType(
                    properties = mapOf("value" to JsonSchemaType.IntegerType(minimum = 0, maximum = 100)),
                    required = listOf("value"),
                ),
            )
            override val sideEffect = SideEffect.NONE
            override val piiExposure = PiiExposure.NONE
            override suspend fun run(arguments: JsonObject): ToolResult =
                ToolResult.Success(name, buildJsonObject { put("value", 42) })
        }

    @Test
    fun `duplicate registration is rejected`() {
        // spec 0005: GIVEN registered set_brightness, WHEN re-registered, THEN rejected
        val registry = InMemoryToolRegistry()
        registry.register(tool("set_brightness"))
        assertThrows(IllegalArgumentException::class.java) { registry.register(tool("set_brightness")) }
    }

    @Test
    fun `unknown tool execution returns Failure not throw`() = runTest {
        // spec 0005: GIVEN call to unregistered name, WHEN execute(), THEN Failure(UNKNOWN_TOOL)
        val registry = InMemoryToolRegistry()
        val result = registry.execute(ToolCall("c1", "ghost", buildJsonObject { put("value", 1) }))
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.UNKNOWN_TOOL, (result as ToolResult.Failure).error)
    }

    @Test
    fun `malformed arguments return Failure not throw`() = runTest {
        val registry = InMemoryToolRegistry()
        registry.register(tool("set_brightness"))
        val result = registry.execute(ToolCall("c1", "set_brightness", buildJsonObject { put("value", "nope") }))
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.MALFORMED_ARGUMENTS, (result as ToolResult.Failure).error)
    }

    @Test
    fun `execute never throws on tool failure`() = runTest {
        val registry = InMemoryToolRegistry()
        registry.register(object : Tool {
            override val definition = ToolDefinition(
                name = "boom",
                description = "Throws",
                parameters = JsonSchemaType.ObjectType(properties = emptyMap(), required = emptyList()),
            )
            override val sideEffect = SideEffect.NONE
            override val piiExposure = PiiExposure.NONE
            override suspend fun run(arguments: JsonObject): ToolResult = throw IllegalStateException("kaboom")
        })
        val result = registry.execute(ToolCall("c1", "boom", buildJsonObject {}))
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.EXECUTION_ERROR, (result as ToolResult.Failure).error)
    }

    @Test
    fun `definitions are sorted by name - deterministic`() {
        val registry = InMemoryToolRegistry()
        registry.register(tool("zeta"))
        registry.register(tool("alpha"))
        registry.register(tool("middle"))
        val names = registry.definitions().map { it.name }
        assertEquals(listOf("alpha", "middle", "zeta"), names)
        // identical registry contents -> identical lists
        assertEquals(registry.definitions(), registry.definitions())
    }

    @Test
    fun `unregister and get behave`() {
        val registry = InMemoryToolRegistry()
        registry.register(tool("alpha"))
        assertEquals("alpha", registry.get("alpha")?.definition?.name)
        assertTrue(registry.unregister("alpha"))
        assertFalse(registry.unregister("alpha"))
        assertNull(registry.get("alpha"))
    }
}