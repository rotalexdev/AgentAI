package com.agentai.app.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolResult
import com.agentai.core.contract.ToolError
import com.agentai.core.registry.Tool
import com.agentai.core.security.PiiExposure
import com.agentai.core.security.SideEffect
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SAFE read-only tool (spec 0007 T1): returns battery level/status.
 * No side effects, no PII exposure.
 */
class GetBatteryStatusTool(
    private val context: Context,
) : Tool {

    override val definition = ToolDefinition(
        name = "get_battery_status",
        description = "Get the current battery level (0-100) and charging state",
        parameters = JsonSchemaType.ObjectType(properties = emptyMap(), required = emptyList()),
    )

    override val sideEffect = SideEffect.NONE
    override val piiExposure = PiiExposure.NONE

    override suspend fun run(arguments: JsonObject): ToolResult {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return ToolResult.Failure(
            definition.name,
            ToolError.EXECUTION_ERROR,
            "Unable to read battery state",
        )

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        val percent = if (scale > 0) ((level.toDouble() / scale) * 100).toInt().coerceIn(0, 100) else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return ToolResult.Success(
            definition.name,
            buildJsonObject {
                put("percent", percent)
                put("charging", charging)
            },
        )
    }
}