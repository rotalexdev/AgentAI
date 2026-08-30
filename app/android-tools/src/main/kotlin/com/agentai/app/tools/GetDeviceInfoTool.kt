package com.agentai.app.tools

import android.os.Build
import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolResult
import com.agentai.core.registry.Tool
import com.agentai.core.security.PiiExposure
import com.agentai.core.security.SideEffect
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SAFE read-only tool (spec 0007 T2): returns ONLY the 4 allowlisted PII
 * fields — model, manufacturer, OS release, API level (design D2). Any other
 * identifier (IMEI, serial, MAC, advertising ID) is out of scope and must
 * never be added here.
 */
class GetDeviceInfoTool : Tool {

    override val definition = ToolDefinition(
        name = "get_device_info",
        description = "Get device info (model, manufacturer, OS release, API level)",
        parameters = JsonSchemaType.ObjectType(properties = emptyMap(), required = emptyList()),
    )

    override val sideEffect = SideEffect.NONE
    override val piiExposure = PiiExposure.NONE

    override suspend fun run(arguments: JsonObject): ToolResult =
        ToolResult.Success(
            definition.name,
            buildJsonObject {
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("os_release", Build.VERSION.RELEASE)
                put("api_level", Build.VERSION.SDK_INT)
            },
        )
}