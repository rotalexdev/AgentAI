# ADR-0002 — Model Abstraction

## Status

Approved.

## Context

The project targets extremely small on-device tool-calling models: Cactus Needle (~26M parameters, the initial reference), Google FunctionGemma 270M, and possibly others. The architecture MUST NOT depend directly on any specific model; changing the model must not change the tool architecture. Without an abstraction, adapter logic, prompt assembly, and tool execution would be coupled to one inference engine, making the runtime fragile and hard to evaluate.

Decision priority (AGENTS.md): Model independence ranks above local execution, testability, maintainability, and performance.

## Decision

Define a single model abstraction:

```kotlin
interface AgentModel {
    suspend fun generate(request: ModelRequest): ModelResponse
}
```

with:

```kotlin
data class ModelRequest(
    val userText: String,
    val toolDefinitions: List<ToolDefinition>,   // deterministic order
    val maxToolCalls: Int = 1,
    val seed: Long? = null,                      // MockModel determinism
)

sealed interface ModelResponse {
    data class Answer(val text: String) : ModelResponse
    data class ToolCalls(val calls: List<ToolCall>) : ModelResponse
    data class Refused(val reason: String) : ModelResponse
}
```

All concrete models are plug-ins behind this interface: `NeedleModel`, `FunctionGemmaModel`, `LfmModel`, and a seed-driven `MockModel` for deterministic tests. Adapters MUST NOT call Android APIs and MUST NOT contain tool implementations (spec 0003 A2/A3).

## Consequences

- Any model can be swapped without touching the tool architecture, registry, or policy chain.
- `MockModel` provides deterministic behavior for JVM unit tests and evaluation before any on-device model is integrated.
- A new model requires only a new adapter implementation and evaluation against the shared dataset (spec 0009).
- The tool contract (spec 0004), registry (spec 0005), and security policy (spec 0006) remain model-independent.
- Needle is a plug-in, never the application.