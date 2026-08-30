package com.agentai.app.tools

import android.content.Context
import android.provider.Settings
import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolResult
import com.agentai.core.contract.ToolError
import com.agentai.core.registry.Tool
import com.agentai.core.security.PiiExposure
import com.agentai.core.security.SideEffect
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * CONFIRMATION_REQUIRED tool (spec 0007 T3): sets screen brightness.
 * Requires WRITE_SETTINGS permission (preflight in ToolPolicy, spec 0006 S3)
 * and a confirmed call. Value is Int 0..100 — validated by the registry's
 * ToolCallValidator (no coercion) before this tool ever runs.
 */
class SetBrightnessTool(
    private val context: Context,
) : Tool {

    override val definition = ToolDefinition(
        name = "set_brightness",
        description = "Set the device screen brightness (0-100)",
        parameters = JsonSchemaType.ObjectType(
            properties = mapOf(
                "value" to JsonSchemaType.IntegerType(minimum = BrightnessBounds.MIN, maximum = BrightnessBounds.MAX),
            ),
            required = listOf("value"),
        ),
    )

    override val sideEffect = SideEffect.MUTATES_SYSTEM
    override val piiExposure = PiiExposure.NONE

    override suspend fun run(arguments: JsonObject): ToolResult {
        val raw = arguments["value"]
        val value = (raw as? JsonPrimitive)?.content?.toIntOrNull()
        if (value == null || !BrightnessBounds.isValid(value)) {
            return ToolResult.Failure(definition.name, ToolError.MALFORMED_ARGUMENTS, "value must be int 0..100")
        }

        val resolver = context.contentResolver
        val writeable = Settings.System.canWrite(context)
        if (!writeable) {
            return ToolResult.Failure(definition.name, ToolError.PERMISSION_DENIED, "WRITE_SETTINGS not granted")
        }

        val brightness = BrightnessBounds.toScreenScale(value)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightness)

        return ToolResult.Success(definition.name, buildJsonObject { put("value", value) })
    }
}