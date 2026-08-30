package com.agentai.app.tools

import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolResult
import com.agentai.core.contract.ToolError
import com.agentai.core.registry.Tool
import com.agentai.core.security.PiiExposure
import com.agentai.core.security.SideEffect
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SAFE read-only tool (spec 0007 T1): returns the current device time.
 * No side effects, no PII exposure.
 */
class GetCurrentTimeTool : Tool {

    override val definition = ToolDefinition(
        name = "get_current_time",
        description = "Get the current device time (RFC 3339, device local zone)",
        parameters = JsonSchemaType.ObjectType(properties = emptyMap(), required = emptyList()),
    )

    override val sideEffect = SideEffect.NONE
    override val piiExposure = PiiExposure.NONE

    override suspend fun run(arguments: JsonObject): ToolResult {
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val now = formatter.format(Instant.now().atZone(ZoneId.systemDefault()))
        return ToolResult.Success(definition.name, buildJsonObject { put("iso8601", now) })
    }
}