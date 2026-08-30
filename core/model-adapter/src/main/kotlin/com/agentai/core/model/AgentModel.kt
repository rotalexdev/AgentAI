package com.agentai.core.model

import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolDefinition
import kotlinx.serialization.Serializable

/**
 * Model abstraction (spec 0003 A1, design D5). The runtime depends on this
 * interface only; concrete models are plug-ins (Needle, FunctionGemma, LFM,
 * MockModel). Adapters MUST NOT call Android APIs and MUST NOT contain tool
 * implementations (spec 0003 A2/A3).
 */
interface AgentModel {
    suspend fun generate(request: ModelRequest): ModelResponse
}

/**
 * Immutable request to the model (spec 0003 A5).
 *
 * [toolDefinitions] is provided in deterministic sorted order so prompt
 * assembly is stable; [maxToolCalls] is 1 for the single-shot loop (spec 0002 R2);
 * [seed] enables deterministic MockModel output.
 */
@Serializable
data class ModelRequest(
    val userText: String,
    val toolDefinitions: List<ToolDefinition>,
    val maxToolCalls: Int = 1,
    val seed: Long? = null,
)

/**
 * Structured model response (spec 0003, design D5).
 *
 * The runtime caps execution at one ToolCall per turn regardless of
 * [ToolCalls] length (spec 0002 R2). A response is NEVER authorization;
 * every call still passes the registry → validator → policy chain.
 */
@Serializable
sealed interface ModelResponse {
    @Serializable
    data class Answer(val text: String) : ModelResponse

    @Serializable
    data class ToolCalls(val calls: List<ToolCall>) : ModelResponse

    @Serializable
    data class Refused(val reason: String) : ModelResponse
}