package com.agentai.app.tools

import android.content.Context
import android.media.AudioManager
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
 * CONFIRMATION_REQUIRED tool (spec 0007 T4): sets media volume.
 * Requires MODIFY_AUDIO_SETTINGS permission (preflight in ToolPolicy).
 * Value is Int 0..100; validated with no coercion before execution.
 */
class SetVolumeTool(
    private val context: Context,
) : Tool {

    override val definition = ToolDefinition(
        name = "set_volume",
        description = "Set the media volume (0-100)",
        parameters = JsonSchemaType.ObjectType(
            properties = mapOf(
                "value" to JsonSchemaType.IntegerType(minimum = VolumeBounds.MIN, maximum = VolumeBounds.MAX),
            ),
            required = listOf("value"),
        ),
    )

    override val sideEffect = SideEffect.MUTATES_SYSTEM
    override val piiExposure = PiiExposure.NONE

    override suspend fun run(arguments: JsonObject): ToolResult {
        val raw = arguments["value"]
        val value = (raw as? JsonPrimitive)?.content?.toIntOrNull()
        if (value == null || !VolumeBounds.isValid(value)) {
            return ToolResult.Failure(definition.name, ToolError.MALFORMED_ARGUMENTS, "value must be int 0..100")
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult.Failure(definition.name, ToolError.EXECUTION_ERROR, "AudioManager unavailable")

        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) {
            return ToolResult.Failure(definition.name, ToolError.EXECUTION_ERROR, "No media volume stream")
        }
        val scaled = (value / 100f * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, scaled, 0)

        return ToolResult.Success(definition.name, buildJsonObject { put("value", value) })
    }
}