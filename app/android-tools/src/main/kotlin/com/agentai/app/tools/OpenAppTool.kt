package com.agentai.app.tools

import android.content.Context
import android.content.Intent
import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.contract.ToolResult
import com.agentai.core.contract.ToolError
import com.agentai.core.registry.Tool
import com.agentai.core.security.PiiExposure
import com.agentai.core.security.SideEffect
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * CONFIRMATION_REQUIRED tool (spec 0007 T5): opens an allowlisted app.
 *
 * Security model:
 * - The model can only supply a CANONICAL KEY from the allowlist
 *   (e.g. "settings") — never a raw package name and never Intent extras.
 * - The Intent is built by this tool from the allowlisted package; any
 *   model-supplied extras are rejected (no model extras).
 * - An EMPTY allowlist means the tool is FORBIDDEN (cannot launch anything).
 */
class OpenAppTool(
    private val context: Context,
    private val allowlist: OpenAppAllowlist,
) : Tool {

    override val definition = ToolDefinition(
        name = "open_app",
        description = "Open an allowlisted app by canonical key",
        parameters = JsonSchemaType.ObjectType(
            properties = mapOf(
                "app" to JsonSchemaType.StringType(
                    enum = allowlist.keys.toList().ifEmpty { listOf("<empty>") },
                ),
            ),
            required = listOf("app"),
        ),
    )

    override val sideEffect = SideEffect.MUTATES_SYSTEM
    override val piiExposure = PiiExposure.NONE

    override suspend fun run(arguments: JsonObject): ToolResult {
        // Empty allowlist ⇒ FORBIDDEN fallback (spec 0007 T5, design D1).
        if (allowlist.isEmpty) {
            return ToolResult.Failure(definition.name, ToolError.PERMISSION_DENIED, "open_app allowlist is empty (FORBIDDEN)")
        }

        val key = (arguments["app"] as? JsonPrimitive)?.content
        if (key == null || !allowlist.isAllowed(key)) {
            return ToolResult.Failure(definition.name, ToolError.MALFORMED_ARGUMENTS, "unknown app key")
        }

        // Reject any model-supplied extras: the Intent is built entirely here.
        if (arguments.keys.any { it != "app" }) {
            return ToolResult.Failure(definition.name, ToolError.MALFORMED_ARGUMENTS, "model extras are not allowed")
        }

        val packageName = allowlist.packageFor(key) ?: return ToolResult.Failure(
            definition.name,
            ToolError.EXECUTION_ERROR,
            "allowlisted package missing",
        )

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ToolResult.Failure(definition.name, ToolError.EXECUTION_ERROR, "no launch intent for '$packageName'")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ToolResult.Success(definition.name, kotlinx.serialization.json.buildJsonObject { put("opened", key) })
    }
}