package com.agentai.core.security

import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolError
import com.agentai.core.registry.Tool

/**
 * Policy decision from the security chain (spec 0006 S3).
 */
sealed interface PolicyDecision {
    data object Allow : PolicyDecision

    /** Denied with the canonical [ToolError] mapped deterministically. */
    data class Deny(val reason: String, val error: ToolError) : PolicyDecision

    /** Tool requires user confirmation; the runtime consults the [ConfirmationGate]. */
    data object NeedsConfirmation : PolicyDecision
}

/**
 * Deterministic security policy gate (spec 0006 S3).
 *
 * [evaluate] runs the security preflight BEFORE any execution:
 * classification → permission preflight.
 *
 * - SAFE ⇒ [PolicyDecision.Allow]
 * - CONFIRMATION_REQUIRED ⇒ [PolicyDecision.NeedsConfirmation] — the runtime
 *   resolves the [ConfirmationGate] (headless deny-by-default, or the UI gate).
 * - FORBIDDEN / missing permission ⇒ [PolicyDecision.Deny] with a canonical
 *   [ToolError] (PERMISSION_DENIED); never executed.
 */
interface ToolPolicy {
    suspend fun evaluate(tool: Tool, call: ToolCall): PolicyDecision
}

/**
 * Default [ToolPolicy] implementation (spec 0006 S3-S5).
 *
 * Does NOT consult the confirmation gate itself: classification is rule-based
 * (spec 0006 S2) and CONFIRMATION_REQUIRED yields [PolicyDecision.NeedsConfirmation],
 * leaving gate resolution to the runtime (so headless and UI share the identical
 * path — spec 0008 U4).
 */
class DefaultToolPolicy(
    private val permissionEvaluator: PermissionEvaluator,
    private val requiredPermissions: Map<String, AndroidPermission> = emptyMap(),
) : ToolPolicy {

    override suspend fun evaluate(tool: Tool, call: ToolCall): PolicyDecision {
        // Permission preflight: deterministic, before anything else.
        requiredPermissions[call.name]?.let { permission ->
            if (!permissionEvaluator.hasPermission(permission)) {
                return PolicyDecision.Deny(
                    reason = "Missing runtime permission $permission",
                    error = ToolError.PERMISSION_DENIED,
                )
            }
        }

        // Classification drives confirmation; SAFE tools run directly.
        val classification = SecurityClassifier.classify(tool.sideEffect, tool.piiExposure)
        return when (classification) {
            SecurityClassification.SAFE -> PolicyDecision.Allow
            SecurityClassification.CONFIRMATION_REQUIRED -> PolicyDecision.NeedsConfirmation
            SecurityClassification.FORBIDDEN -> PolicyDecision.Deny(
                reason = "FORBIDDEN tool cannot execute",
                error = ToolError.PERMISSION_DENIED,
            )
        }
    }
}