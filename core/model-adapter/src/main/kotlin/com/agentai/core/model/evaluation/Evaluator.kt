package com.agentai.core.model.evaluation

import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.model.AgentModel
import com.agentai.core.model.ModelRequest
import com.agentai.core.model.ModelResponse

/**
 * Adapter-agnostic evaluation harness (spec 0009 E3).
 *
 * `evaluate(model, definitions, dataset)` runs every record through the SAME
 * [AgentModel] interface and scores the outcome. It contains NO model-specific
 * logic; any adapter (MockModel, Needle, FunctionGemma, LFM) can be evaluated
 * against the identical dataset and tool definitions (E5).
 */
class Evaluator(
    private val modelId: String,
    private val datasetVersion: String,
    private val toolDefinitionVersion: String,
) {

    /**
     * @param measurePerformance when true, rough latency is measured per record.
     * Memory/model-size/tokens-per-sec are environment-specific and reported
     * by the caller via [extraMetrics] (they cannot be measured in pure JVM).
     */
    suspend fun evaluate(
        model: AgentModel,
        definitions: List<ToolDefinition>,
        dataset: List<EvaluationRecord>,
        extraMetrics: ExtraMetrics = ExtraMetrics(),
    ): EvaluationReport {
        val measured = dataset.map { record ->
            val start = System.nanoTime()
            val response = model.generate(
                ModelRequest(
                    userText = record.prompt,
                    toolDefinitions = definitions,
                    maxToolCalls = 1,
                ),
            )
            val latencyMs = (System.nanoTime() - start) / 1_000_000.0
            record to (response to latencyMs)
        }

        val recordResults = measured.map { (record, pair) ->
            val (response, latencyMs) = pair
            val observed = describe(response)
            val passed = score(record, response)
            RecordResult(record.id, passed, observed, record.notes, latencyMs)
        }

        val passedCount = recordResults.count { it.passed }
        val toolCallRecords = dataset.filter { it.expectedTool != null }
        val rejectionRecords = dataset.filter { it.expectedRejection != null }

        val toolSelectionAccuracy = if (toolCallRecords.isEmpty()) 1.0 else
            recordResults.filterIndexed { i, r -> dataset[i].expectedTool != null && r.passed }.size.toDouble() / toolCallRecords.size

        val argumentAccuracy = if (toolCallRecords.isEmpty()) 1.0 else
            recordResults.filterIndexed { i, r ->
                dataset[i].expectedArgs != null && dataset[i].expectedTool != null && r.passed
            }.size.toDouble() / toolCallRecords.size

        val invalidCallRejection = if (rejectionRecords.isEmpty()) 1.0 else
            recordResults.filterIndexed { i, r -> dataset[i].expectedRejection != null && r.passed }.size.toDouble() / rejectionRecords.size

        val falsePositives = recordResults.filterIndexed { i, r ->
            dataset[i].expectedRejection != null && !r.passed
        }.size
        val falsePositiveRate = if (rejectionRecords.isEmpty()) 0.0 else falsePositives.toDouble() / rejectionRecords.size

        val metrics = Metrics(
            toolSelectionAccuracy = toolSelectionAccuracy,
            argumentAccuracy = argumentAccuracy,
            invalidCallRejection = invalidCallRejection,
            falsePositiveRate = falsePositiveRate,
            latencyMs = if (recordResults.isEmpty()) 0.0 else recordResults.sumOf { it.latencyMs } / recordResults.size,
            memoryKb = extraMetrics.memoryKb,
            modelSizeMb = extraMetrics.modelSizeMb,
            tokensPerSec = extraMetrics.tokensPerSec,
        )

        return EvaluationReport(
            modelId = modelId,
            datasetVersion = datasetVersion,
            toolDefinitionVersion = toolDefinitionVersion,
            metrics = metrics,
            records = recordResults,
        )
    }

    private fun score(record: EvaluationRecord, response: ModelResponse): Boolean = when {
        record.expectedRejection != null -> response is ModelResponse.Refused ||
            (response is ModelResponse.Answer) ||
            (response is ModelResponse.ToolCalls && response.calls.isEmpty())

        record.expectedTool != null -> {
            val calls = (response as? ModelResponse.ToolCalls)?.calls ?: return false
            val first = calls.firstOrNull() ?: return false
            if (first.name != record.expectedTool) return false
            if (record.expectedArgs != null) {
                argsMatch(first, record.expectedArgs)
            } else true
        }

        else -> response is ModelResponse.Answer || response is ModelResponse.Refused
    }

    private fun argsMatch(call: ToolCall, expected: Map<String, Any?>): Boolean {
        val actual = call.arguments
        return expected.all { (key, value) ->
            val element = actual[key] ?: return false
            element.toString().trim('"') == value?.toString()
        }
    }

    private fun describe(response: ModelResponse): String = when (response) {
        is ModelResponse.Answer -> "answer: ${response.text.take(60)}"
        is ModelResponse.Refused -> "refused: ${response.reason.take(60)}"
        is ModelResponse.ToolCalls -> "toolcalls: ${response.calls.joinToString { it.name + it.arguments }}"
    }

    data class ExtraMetrics(
        val memoryKb: Double = 0.0,
        val modelSizeMb: Double = 0.0,
        val tokensPerSec: Double = 0.0,
    )
}