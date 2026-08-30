package com.agentai.core.security

import com.agentai.core.contract.ToolCall

/**
 * Result of a confirmation request (design D6, spec 0006 S5).
 */
sealed interface Approval {
    data object Granted : Approval
    data class Denied(val reason: String) : Approval
}

/**
 * Gate for CONFIRMATION_REQUIRED tools (spec 0006 S5).
 *
 * The runtime never interprets model output as authorization; a
 * CONFIRMATION_REQUIRED call only proceeds when this gate grants it.
 * Gate absence or failure MUST be treated as [Approval.Denied]
 * (fail-closed) by callers.
 */
interface ConfirmationGate {
    suspend fun requestApproval(call: ToolCall): Approval
}

/**
 * Headless, fail-closed gate (design D6). Denies unless [grant] granted
 * the exact [ToolCall.id]. This is the default for tests and headless runs;
 * the UI confirmation dialog is another [ConfirmationGate] implementation.
 */
class DenyByDefaultGate(
    private val grantedCallIds: Set<String> = emptySet(),
) : ConfirmationGate {

    override suspend fun requestApproval(call: ToolCall): Approval =
        if (call.id in grantedCallIds) Approval.Granted
        else Approval.Denied("Confirmation required (fail-closed gate)")
}