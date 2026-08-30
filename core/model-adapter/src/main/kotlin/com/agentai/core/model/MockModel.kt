package com.agentai.core.model

import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Random

/**
 * Deterministic model for tests and evaluation (spec 0003 A4, A5).
 *
 * Behavior:
 * - With a fixed [ModelRequest.seed], two calls return identical [ModelResponse]
 *   (spec 0003: GIVEN MockModel with fixed seed, WHEN generate() called twice,
 *   THEN identical ModelResponse).
 * - Rejects malformed/injection-like prompts with [ModelResponse.Refused].
 * - Otherwise selects a tool deterministically and produces valid arguments.
 *
 * This model is a test oracle, NOT a product inference engine.
 */
class MockModel(
    private val toolSelector: (List<ToolDefinition>, String) -> Int =
        MockModel::selectToolByKeyword,
) : AgentModel {

    override suspend fun generate(request: ModelRequest): ModelResponse {
        val rng = Random(request.seed ?: 0L)

        // Malformed / injection-like input is never honored as a tool call.
        val text = request.userText.trim()
        if (looksMalicious(text)) {
            return ModelResponse.Refused("input rejected: injection or unsafe instruction")
        }

        val definitions = request.toolDefinitions
        if (definitions.isEmpty()) {
            return ModelResponse.Answer("I have no tools available.")
        }

        val index = toolSelector(definitions, text)
        val definition = definitions[index % definitions.size]
        val arguments = deterministicArguments(definition.parameters, rng)
        val call = ToolCall(id = "mock-$index", name = definition.name, arguments = arguments)

        // Single-shot loop: the model emits at most one tool call per turn.
        return if (request.maxToolCalls >= 1) {
            ModelResponse.ToolCalls(listOf(call))
        } else {
            ModelResponse.Answer("no tool calls allowed")
        }
    }

    private fun looksMalicious(text: String): Boolean {
        val lower = text.lowercase()
        return FORBIDDEN_MARKERS.any { it in lower }
    }

    private fun deterministicArguments(
        schema: JsonSchemaType.ObjectType,
        rng: Random,
    ): JsonObject = buildJsonObject {
        for ((name, property) in schema.properties) {
            if (schema.required.isEmpty() || name in schema.required) {
                put(name, sampleValue(property, rng))
            }
        }
    }

    private fun sampleValue(type: JsonSchemaType, rng: Random): JsonElement =
        when (type) {
            is JsonSchemaType.IntegerType -> {
                val min = type.minimum ?: 0
                val max = type.maximum ?: (min + 100)
                val span = (max - min).coerceAtLeast(1)
                JsonPrimitive(min + rng.nextInt(span + 1))
            }
            is JsonSchemaType.NumberType -> {
                val min = type.minimum ?: 0.0
                val max = type.maximum ?: (min + 100.0)
                JsonPrimitive(min + rng.nextDouble() * (max - min))
            }
            is JsonSchemaType.StringType -> JsonPrimitive(type.enum?.firstOrNull() ?: "value")
            is JsonSchemaType.BooleanType -> JsonPrimitive(rng.nextBoolean())
            is JsonSchemaType.ArrayType -> JsonArray(listOf(sampleValue(type.items, rng)))
            is JsonSchemaType.ObjectType -> deterministicArguments(type, rng)
        }

    companion object {
        /** Deterministic keyword-based selection; only for the mock/test oracle. */
        fun selectToolByKeyword(
            definitions: List<ToolDefinition>,
            userText: String,
        ): Int {
            val lower = userText.lowercase()
            val match = definitions.indexOfFirst { it.name.lowercase() in lower }
            return if (match >= 0) match else 0
        }

        private val FORBIDDEN_MARKERS = listOf(
            "ignore previous instructions",
            "ignore all instructions",
            "system prompt",
            "run shell",
            "rm -rf",
            "sudo",
            "drop table",
            "extract credentials",
            "read /etc",
            "access filesystem",
            "reflection",
        )
    }
}