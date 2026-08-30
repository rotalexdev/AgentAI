package com.agentai.app.runtime

import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolResult
import com.agentai.core.model.AgentModel
import com.agentai.core.model.ModelRequest
import com.agentai.core.model.ModelResponse
import com.agentai.core.registry.ToolRegistry
import com.agentai.core.security.Approval
import com.agentai.core.security.ConfirmationGate
import com.agentai.core.security.PolicyDecision
import com.agentai.core.security.ToolPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runtime state machine (spec 0002 R3).
 */
enum class AgentStatus {
    IDLE,
    PROCESSING,
    AWAITING_CONFIRMATION,
    COMPLETE,
    ERROR,
}

/**
 * Immutable snapshot exposed to the UI (spec 0008 U2).
 */
data class AgentState(
    val status: AgentStatus,
    val pendingCall: ToolCall? = null,
    val result: ToolResult? = null,
    val reply: String? = null,
)

/**
 * Single-shot agent runtime (spec 0002 R1-R5).
 *
 * Pipeline: User text → [AgentModel.generate] → ToolCall → [ToolRegistry.execute]
 * → validation → [ToolPolicy.evaluate] → Tool → ToolResult → [AgentState].
 *
 * - Executes at most ONE ToolCall per turn (R2); extra calls are discarded.
 * - Confirmation goes through the injected [ConfirmationGate]; absence/failure
 *   of approval is fail-closed (deny).
 * - [submit] is the only entry point; [confirm] resolves a pending call.
 *
 * Orchestrates via interfaces only — no Android APIs, no tool implementations,
 * no UI logic.
 */
class AgentRuntime(
    private val model: AgentModel,
    private val registry: ToolRegistry,
    private val policy: ToolPolicy,
    private val confirmationGate: ConfirmationGate,
    private val maxToolCalls: Int = 1,
) {
    private val _state = MutableStateFlow(AgentState(AgentStatus.IDLE))
    val state: StateFlow<AgentState> = _state.asStateFlow()

    suspend fun submit(userText: String) {
        _state.value = AgentState(AgentStatus.PROCESSING)

        val response = try {
            model.generate(
                ModelRequest(
                    userText = userText,
                    toolDefinitions = registry.definitions(),
                    maxToolCalls = maxToolCalls,
                ),
            )
        } catch (e: Exception) {
            _state.value = AgentState(AgentStatus.ERROR, reply = "Model error: ${e.message}")
            return
        }

        when (response) {
            is ModelResponse.Answer -> {
                _state.value = AgentState(AgentStatus.COMPLETE, reply = response.text)
            }
            is ModelResponse.Refused -> {
                _state.value = AgentState(AgentStatus.COMPLETE, reply = "Refused: ${response.reason}")
            }
            is ModelResponse.ToolCalls -> {
                val call = response.calls.firstOrNull()
                if (call == null) {
                    _state.value = AgentState(AgentStatus.COMPLETE, reply = "No tool call requested.")
                    return
                }
                // R2: single-shot — even if the model emitted more, only the first executes.
                handleToolCall(call)
            }
        }
    }

    suspend fun confirm(callId: String, approved: Boolean) {
        val pending = _state.value.pendingCall ?: return
        if (pending.id != callId) return
        if (!approved) {
            // Fail-closed: denial is a structured error, never execution (spec 0006 S5).
            _state.value = AgentState(
                AgentStatus.COMPLETE,
                result = ToolResult.Failure(pending.name, com.agentai.core.contract.ToolError.CONFIRMATION_REQUIRED, "Confirmation denied"),
            )
            return
        }
        executePending(pending)
    }

    fun reset() {
        _state.value = AgentState(AgentStatus.IDLE)
    }

    private suspend fun handleToolCall(call: ToolCall) {
        val tool = registry.get(call.name)
        if (tool == null) {
            _state.value = AgentState(
                AgentStatus.COMPLETE,
                result = ToolResult.Failure(call.name, com.agentai.core.contract.ToolError.UNKNOWN_TOOL, "Unknown tool '${call.name}'"),
            )
            return
        }

        when (val decision = policy.evaluate(tool, call)) {
            is PolicyDecision.Allow -> executePending(call)
            is PolicyDecision.Deny -> {
                _state.value = AgentState(
                    AgentStatus.COMPLETE,
                    result = ToolResult.Failure(call.name, decision.error, decision.reason),
                )
            }
            PolicyDecision.NeedsConfirmation -> {
                // Expose pending call so the UI can render the confirmation dialog
                // (spec 0002 R3, spec 0008 U4) and await the gate.
                _state.value = AgentState(AgentStatus.AWAITING_CONFIRMATION, pendingCall = call)
                val approval = confirmationGate.requestApproval(call)
                when (approval) {
                    Approval.Granted -> executePending(call)
                    is Approval.Denied -> {
                        _state.value = AgentState(
                            AgentStatus.COMPLETE,
                            result = ToolResult.Failure(
                                call.name,
                                com.agentai.core.contract.ToolError.CONFIRMATION_REQUIRED,
                                approval.reason,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun executePending(call: ToolCall) {
        val result = registry.execute(call)
        _state.value = AgentState(AgentStatus.COMPLETE, result = result)
    }
}