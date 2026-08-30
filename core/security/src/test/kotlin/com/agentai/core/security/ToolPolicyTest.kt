package com.agentai.core.security

import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolResult
import com.agentai.core.contract.ToolError
import com.agentai.core.registry.Tool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED tests for spec 0006 scenarios — executed in Phase 1 Step 0 (ADR-0005 flip).
 */
class ToolPolicyTest {

    private fun tool(
        name: String,
        sideEffect: SideEffect = SideEffect.NONE,
        piiExposure: PiiExposure = PiiExposure.NONE,
    ): Tool = object : Tool {
        override val definition = ToolDefinition(
            name = name,
            description = name,
            parameters = JsonSchemaType.ObjectType(properties = emptyMap(), required = emptyList()),
        )
        override val sideEffect: SideEffect = sideEffect
        override val piiExposure: PiiExposure = piiExposure
        override suspend fun run(arguments: JsonObject): ToolResult = ToolResult.Success(name, buildJsonObject {})
    }

    private fun call(name: String, id: String = "c1") = ToolCall(id, name, buildJsonObject {})

    @Test
    fun `SAFE tool with no side effect is allowed without confirmation`() = runTest {
        val policy = DefaultToolPolicy(permissionEvaluator = PermissionEvaluator { true })
        assertEquals(PolicyDecision.Allow, policy.evaluate(tool("get_battery_status"), call("get_battery_status")))
    }

    @Test
    fun `side effect yields NeedsConfirmation - gate decides`() = runTest {
        // spec 0006 S3: CONFIRMATION_REQUIRED => NEEDS_CONFIRMATION, resolved by the runtime gate
        val policy = DefaultToolPolicy(permissionEvaluator = PermissionEvaluator { true })
        assertEquals(
            PolicyDecision.NeedsConfirmation,
            policy.evaluate(tool("set_volume", SideEffect.MUTATES_SYSTEM), call("set_volume")),
        )
    }

    @Test
    fun `permission preflight denies with PERMISSION_DENIED`() = runTest {
        // spec 0006: GIVEN set_brightness without WRITE_SETTINGS, THEN denied
        val policy = DefaultToolPolicy(
            permissionEvaluator = PermissionEvaluator { false },
            requiredPermissions = mapOf("set_brightness" to AndroidPermission.WRITE_SETTINGS),
        )
        val decision = policy.evaluate(
            tool("set_brightness", SideEffect.MUTATES_SYSTEM),
            call("set_brightness"),
        )
        assertTrue(decision is PolicyDecision.Deny)
        assertEquals(ToolError.PERMISSION_DENIED, (decision as PolicyDecision.Deny).error)
    }

    @Test
    fun `permission granted then side effect yields NeedsConfirmation`() = runTest {
        val policy = DefaultToolPolicy(
            permissionEvaluator = PermissionEvaluator { true },
            requiredPermissions = mapOf("set_brightness" to AndroidPermission.WRITE_SETTINGS),
        )
        assertEquals(
            PolicyDecision.NeedsConfirmation,
            policy.evaluate(tool("set_brightness", SideEffect.MUTATES_SYSTEM), call("set_brightness")),
        )
    }

    @Test
    fun `FORBIDDEN capability cannot be classified as safe`() {
        assertTrue(SecurityClassifier.isForbiddenCapability("shell"))
        assertTrue(SecurityClassifier.isForbiddenCapability("credential_access"))
        assertTrue(!SecurityClassifier.isForbiddenCapability("get_battery_status"))
    }
}