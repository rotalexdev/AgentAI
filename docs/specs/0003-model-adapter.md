# 0003 — Model Adapter: Model-Independent Model Abstraction

## Status

Approved (blueprint; implementation scheduled for Phase 1).

## Context

The application must never be coupled to a specific model. Needle (~26M parameters) is the initial reference model, but the architecture must support FunctionGemma (270M) and other small function/tool-calling models interchangeably. The model adapter defines a single, minimal, suspendable contract that every model implementation must satisfy, plus a deterministic `MockModel` used by tests and by the evaluation harness as the reference baseline.

## Goals

- A1. Define `interface AgentModel { suspend fun generate(request: ModelRequest): ModelResponse }`.
- A2. Guarantee adapters never call Android APIs.
- A3. Guarantee adapters never contain tool implementations.
- A4. Make models swappable without changing the tool architecture; provide `MockModel` (seed-driven) for deterministic tests.
- A5. Define `ModelRequest` to carry `userText`, deterministic `toolDefinitions`, `maxToolCalls` (=1), and optional `seed`.

## Non-goals

- Loading or executing any specific model runtime (GGUF/TFLite/etc.) in Phase 0.
- Tokenization, sampling parameter tuning, or per-model prompt templating as a contract concern.
- Model-provided tools, plugins, or code execution surfaces.
- Conversational quality as a requirement — the adapter's contract is intent → tool selection → structured arguments.
- Any network/cloud dependency in the model contract (models are local).

## Requirements

- A1 (RFC 2119 — SHALL): Every model implementation **SHALL** implement `interface AgentModel { suspend fun generate(request: ModelRequest): ModelResponse }`.
- A2 (RFC 2119 — MUST NOT): Adapters **MUST NOT** call Android APIs. An adapter may not import or reference Android framework types.
- A3 (RFC 2119 — MUST NOT): Adapters **MUST NOT** contain tool implementations. Tools live in the registry and Android-tools layer, never in a model adapter.
- A4 (RFC 2119 — SHALL): Models **SHALL** be swappable without changing the tool architecture. `NeedleModel`, `FunctionGemmaModel`, and `LfmModel` are plug-in implementations; `MockModel` (seed-driven) **SHALL** exist for deterministic tests and as the evaluation baseline.
- A5 (RFC 2119 — SHALL): `ModelRequest` **SHALL** carry `userText: String`, `toolDefinitions: List<ToolDefinition>` in deterministic (sorted) order, `maxToolCalls: Int = 1`, and `seed: Long? = null`.
- Every `ModelResponse` **MUST** be one of the sealed variants `Answer`, `ToolCalls`, or `Refused`.
- Deterministic adapters **SHALL** produce identical output for identical input + seed.

### Scenarios (GIVEN/WHEN/THEN)

- GIVEN `MockModel` with a fixed `seed`, WHEN `generate()` is called twice with the same request, THEN both responses are identical.
- GIVEN any adapter, WHEN inspected for Android framework imports or calls, THEN none exist (unit-enforced).
- GIVEN a `ModelRequest` built with sorted tool definitions from the same registry, WHEN the model sees the request, THEN the definitions order is stable across runs (deterministic prompt).

## Architecture

```
                     AgentModel (interface)
   ┌────────────┬───────────────┬──────────────┬──────────────┐
 NeedleModel  FunctionGemmaModel  LfmModel     MockModel(seed)
   └────────────┴───────────────┴──────────────┴──────────────┘
                          │  generate(request)
                          ▼
                   ModelResponse
                 Answer | ToolCalls | Refused
```

- Adapters depend only on `core:tool-contract` (contract types) and sit inside `core:model-adapter`.
- Adapters never see Android `Context`, never invoke `Tool.run`, and never hold a `ToolRegistry` reference.
- `MockModel` is the deterministic reference implementation: mapping a seed to a scripted `(prompt → expected response)` table used by tests and evaluation.

## Data Contracts

Kotlin API sketch (design artifact — not implementation):

```kotlin
// core:model-adapter
interface AgentModel {
    suspend fun generate(request: ModelRequest): ModelResponse
}

data class ModelRequest(
    val userText: String,
    val toolDefinitions: List<ToolDefinition>,   // deterministic sorted order
    val maxToolCalls: Int = 1,
    val seed: Long? = null,                       // MockModel determinism
)

sealed interface ModelResponse {
    data class Answer(val text: String) : ModelResponse
    data class ToolCalls(val calls: List<ToolCall>) : ModelResponse   // runtime caps at 1/turn
    data class Refused(val reason: String) : ModelResponse
}

// core:model-adapter :: MockModel
class MockModel(
    private val seed: Long,
    private val table: MockTable,                  // deterministic scripted responses
) : AgentModel {
    override suspend fun generate(request: ModelRequest): ModelResponse = /* seeded lookup */
}
```

`ToolDefinition` and `ToolCall` are defined in spec 0004. This spec only binds their usage.

## Security

- Model output is UNTRUSTED; adapters must not attach meaning to content beyond the structured `ModelResponse`. Model-provided instructions, "system override" text, or injected commands are irrelevant to the adapter contract.
- Adapters must not execute anything. They return structure; the runtime interprets it.
- Because adapters may not call Android APIs, model output can never reach platform surfaces through an adapter.

## Error Handling

| Failure | Handling |
|---|---|
| Model returns `Refused(reason)` | Propagated as-is to the runtime; no execution. |
| Model returns `Answer(text)` | Propagated to the runtime as a plain reply. |
| Model returns `ToolCalls(calls)` | Propagated; runtime caps execution at the first call. |
| Adapter throws (native loading, inference) | Propagated as an adapter error; runtime maps to `ERROR`; no tool executes. |
| `seed` mismatch for `MockModel` | Deterministic: same seed same table; seed absent → default stable seed. |

## Acceptance Criteria

- AC1. `NeedleModel`, `FunctionGemmaModel`, `LfmModel`, and `MockModel` all implement `AgentModel` conforming to A1.
- AC2. No adapter source file imports any Android framework package (enforced by unit test / lint rule) — A2.
- AC3. No adapter contains a tool implementation or imports `ToolRegistry` — A3.
- AC4. Swapping `MockModel` for `NeedleModel` in the runtime requires no change to the tool architecture — A4.
- AC5. `ModelRequest` conveys `userText`, sorted `toolDefinitions`, `maxToolCalls=1`, and optional `seed` — A5.
- AC6. `MockModel` returns identical `ModelResponse` for identical `(request, seed)` across repeated calls.

## Verification

- V1. Unit test: `MockModel(seed=42).generate(req)` twice → identical `ModelResponse`.
- V2. Static check over `core:model-adapter` sources: zero matches for Android framework package patterns (`android.`, `androidx.`) — CI task after ADR-0005.
- V3. Static check: zero `ToolRegistry` / `Tool` references in adapter sources.
- V4. Evaluation harness (spec 0009) runs the identical dataset against `MockModel` and `NeedleModel` and yields comparable metrics, proving swappability.