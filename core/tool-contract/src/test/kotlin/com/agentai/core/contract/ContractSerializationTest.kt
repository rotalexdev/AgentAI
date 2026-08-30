package com.agentai.core.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED tests for spec 0004 C2/C3: contracts serialize deterministically and
 * round-trip losslessly (model adapter boundary contract).
 */
class ContractSerializationTest {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    @Test
    fun `ToolDefinition round-trips losslessly`() {
        val definition = ToolDefinition(
            name = "set_brightness",
            description = "Set the device screen brightness",
            parameters = JsonSchemaType.ObjectType(
                properties = mapOf("value" to JsonSchemaType.IntegerType(minimum = 0, maximum = 100)),
                required = listOf("value"),
            ),
        )
        val restored = json.decodeFromString<ToolDefinition>(json.encodeToString(definition))
        assertEquals(definition, restored)
    }

    @Test
    fun `ToolCall round-trips losslessly`() {
        val call = ToolCall(
            id = "c1",
            name = "set_brightness",
            arguments = buildJsonObject { put("value", 50) },
        )
        val restored = json.decodeFromString<ToolCall>(json.encodeToString(call))
        assertEquals(call, restored)
    }

    @Test
    fun `ToolResult Success round-trips losslessly`() {
        val result = ToolResult.Success(
            toolName = "set_brightness",
            data = buildJsonObject { put("value", 50) },
        )
        val restored = json.decodeFromString<ToolResult>(json.encodeToString(result))
        assertEquals(result, restored)
    }

    @Test
    fun `ToolResult Failure round-trips losslessly`() {
        val result = ToolResult.Failure(
            toolName = "set_brightness",
            error = ToolError.MALFORMED_ARGUMENTS,
            message = "value must be an integer 0..100",
        )
        val restored = json.decodeFromString<ToolResult>(json.encodeToString(result))
        assertEquals(result, restored)
    }

    @Test
    fun `serialization is deterministic`() {
        val call = ToolCall(
            id = "c1",
            name = "set_brightness",
            arguments = buildJsonObject { put("value", 50) },
        )
        assertEquals(json.encodeToString(call), json.encodeToString(call))
    }

    @Test
    fun `ToolCallValidator rejects string against IntegerType - no coercion`() {
        // spec 0004 C3: `"value":"50"` against IntegerType MUST be rejected.
        val definition = ToolDefinition(
            name = "set_brightness",
            description = "Set brightness",
            parameters = JsonSchemaType.ObjectType(
                properties = mapOf("value" to JsonSchemaType.IntegerType(minimum = 0, maximum = 100)),
                required = listOf("value"),
            ),
        )
        val call = ToolCall(
            id = "c1",
            name = "set_brightness",
            arguments = buildJsonObject { put("value", "50") },
        )
        val result = ToolCallValidator.validate(definition, call)
        assertTrue(result is ToolCallValidator.Result.Invalid)
    }
}