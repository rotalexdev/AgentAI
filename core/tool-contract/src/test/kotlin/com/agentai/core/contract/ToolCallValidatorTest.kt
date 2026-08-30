package com.agentai.core.contract

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED tests for spec 0004 scenarios — to be executed in Phase 1 Step 0
 * once the JVM test runner exists (ADR-0005 strict_tdd flip).
 */
class ToolCallValidatorTest {

    private val setBrightness = ToolDefinition(
        name = "set_brightness",
        description = "Set the device screen brightness",
        parameters = JsonSchemaType.ObjectType(
            properties = mapOf(
                "value" to JsonSchemaType.IntegerType(minimum = 0, maximum = 100),
            ),
            required = listOf("value"),
        ),
    )

    private fun call(name: String, args: JsonObject) = ToolCall(id = "c1", name = name, arguments = args)

    @Test
    fun `valid integer passes`() {
        val result = ToolCallValidator.validate(
            call("set_brightness", buildJsonObject { put("value", 50) }),
            setBrightness,
        )
        assertEquals(ToolCallValidator.Result.Valid, result)
    }

    @Test
    fun `string value is rejected - no coercion`() {
        // spec 0004: GIVEN "value":"50" (string) against IntegerType, THEN rejected
        val result = ToolCallValidator.validate(
            call("set_brightness", buildJsonObject { put("value", "50") }),
            setBrightness,
        )
        assertTrue(result is ToolCallValidator.Result.Invalid, "string must not be coerced to int")
    }

    @Test
    fun `unknown additional property is rejected`() {
        // spec 0004: GIVEN unknown additionalProperties, THEN rejected
        val result = ToolCallValidator.validate(
            call("set_brightness", buildJsonObject { put("value", 50); put("evil", 1) }),
            setBrightness,
        )
        assertTrue(result is ToolCallValidator.Result.Invalid, "additionalProperties must be false")
    }

    @Test
    fun `missing required property is rejected`() {
        val result = ToolCallValidator.validate(
            call("set_brightness", buildJsonObject {}),
            setBrightness,
        )
        assertTrue(result is ToolCallValidator.Result.Invalid)
    }

    @Test
    fun `out of bounds integer is rejected`() {
        val result = ToolCallValidator.validate(
            call("set_brightness", buildJsonObject { put("value", 500) }),
            setBrightness,
        )
        assertTrue(result is ToolCallValidator.Result.Invalid)
    }

    @Test
    fun `string enum is enforced`() {
        val tool = ToolDefinition(
            name = "pick_color",
            description = "Pick a color",
            parameters = JsonSchemaType.ObjectType(
                properties = mapOf("color" to JsonSchemaType.StringType(enum = listOf("red", "green"))),
                required = listOf("color"),
            ),
        )
        val valid = ToolCallValidator.validate(
            call("pick_color", buildJsonObject { put("color", "red") }),
            tool,
        )
        val invalid = ToolCallValidator.validate(
            call("pick_color", buildJsonObject { put("color", "blue") }),
            tool,
        )
        assertEquals(ToolCallValidator.Result.Valid, valid)
        assertTrue(invalid is ToolCallValidator.Result.Invalid)
    }

    @Test
    fun `array of primitives is accepted but nested arrays rejected`() {
        val tool = ToolDefinition(
            name = "tags",
            description = "Set tags",
            parameters = JsonSchemaType.ObjectType(
                properties = mapOf("tags" to JsonSchemaType.ArrayType(JsonSchemaType.StringType())),
                required = listOf("tags"),
            ),
        )
        val valid = ToolCallValidator.validate(
            call("tags", buildJsonObject { put("tags", listOf("a", "b")) }),
            tool,
        )
        assertEquals(ToolCallValidator.Result.Valid, valid)
    }
}