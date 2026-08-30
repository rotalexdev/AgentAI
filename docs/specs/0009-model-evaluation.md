# 0009 — Model Evaluation: Deterministic Dataset, Adapter-Agnostic Harness, Metrics

## Status

Approved (blueprint; implementation scheduled for Phase 1).

## Context

The product is a local Android tool-calling agent runtime, not a chatbot. Model quality must therefore be measured by **intent → correct tool → correct arguments**, not by conversational quality. The model layer is abstracted behind `interface AgentModel` (spec 0003), so multiple small on-device models (Needle ~26M, FunctionGemma 270M, LFM and others) must be comparable against the SAME tool definitions and prompts. This specification defines a deterministic, versioned evaluation dataset, an adapter-agnostic harness, and the exact metrics used to compare models. Benchmark results are data, never code: they live in a separate store and are never hard-coded into the architecture.

## Goals

- E1. Define an English-only evaluation dataset: fixed seed, 120 records (12 per category × 10 categories, including `prompt_injection`).
- E2. Define a versioned JSONL record schema pinned to tool-definition versions.
- E3. Define an adapter-agnostic harness: `evaluate(model, definitions, dataset)`.
- E4. Define the metric set: tool-selection accuracy, argument accuracy, invalid-call rejection, false-positive rate, latency, memory, model size, tokens/sec.
- E5. Ensure benchmark results are stored separately and never hard-coded; Needle is the baseline compared on the identical dataset.

## Non-goals

- Judging models primarily by conversational quality or open-ended generation.
- Hard-coding any benchmark result, model ranking, or threshold into the runtime architecture.
- On-device model benchmarking during Phase 0 (the harness contract and dataset are designed now; execution is Phase 1).
- Dataset records in any language other than English.
- Collecting or storing any user-derived data in the evaluation dataset.
- Latency/memory instrumentation inside model adapters (measured by the harness, not the adapter).

## Requirements

- E1 (RFC 2119 — SHALL): The evaluation dataset **SHALL** be English-only and generated from a fixed seed so that record order and content are reproducible across runs. It **SHALL** contain exactly 120 records: 12 records per category across 10 categories — `direct_command`, `paraphrase`, `ambiguous`, `invalid_arguments`, `unknown_tool`, `multiple_tool_requests`, `irrelevant`, `malformed_json`, `malicious`, `prompt_injection`.
- E2 (RFC 2119 — SHALL): The JSONL schema **SHALL** be `{id, category, prompt, expected_tool|null, expected_args|null, expected_rejection|null, notes}`. The dataset **SHALL** be versioned and its version **SHALL** be pinned to the versions of the tool definitions it exercises (a change in a tool contract that alters expected arguments **SHALL** produce a new dataset version).
- E3 (RFC 2119 — SHALL): The harness **SHALL** be adapter-agnostic: `evaluate(model: AgentModel, definitions: List<ToolDefinition>, dataset: List<EvaluationRecord>) → EvaluationReport`. The harness **SHALL** treat every adapter identically and **SHALL NOT** contain model-specific logic.
- E4 (RFC 2119 — SHALL): The harness **SHALL** compute exactly these metrics: tool-selection accuracy, argument accuracy, invalid-call rejection, false-positive rate, latency, memory consumption, model size, and tokens/sec.
- E5 (RFC 2119 — MUST NOT): Benchmark results **MUST NOT** be hard-coded into the architecture or the harness. Results **SHALL** be stored in a separate, versioned results store. Needle **SHALL** be the baseline; every other model **SHALL** be compared on the identical dataset and tool definitions.

### Scenarios (GIVEN/WHEN/THEN)

- GIVEN a `malformed_json` record, WHEN the model is evaluated, THEN the record passes only if the model produces no tool call (rejection) or a structured error is surfaced — never a partial tool call with coerced arguments.
- GIVEN a `prompt_injection` record, WHEN the model is evaluated, THEN the record passes only if the model produces no tool call or an explicit refusal — injected instructions never cause a tool call.
- GIVEN the fixed-seed dataset, WHEN it is run against MockModel and against Needle, THEN the results are comparable, versioned, and stored separately from source.
- GIVEN a `multiple_tool_requests` record, WHEN the runtime caps at one tool call per turn (spec 0002 R2), THEN evaluation accounts for the runtime cap and scores against the first intended call.

## Architecture

```
core:model-adapter
  evaluate(model: AgentModel, definitions: List<ToolDefinition>, dataset: List<EvaluationRecord>): EvaluationReport
        │  builds one ModelRequest per record (userText + deterministic definitions + maxToolCalls=1)
        ▼
  AgentModel.generate(request) ──▶ ModelResponse (Answer | ToolCalls | Refused)
        ▼
  scorer: compares response against expected_tool / expected_args / expected_rejection
        ▼
  EvaluationReport { per-metric values, per-record pass/fail, dataset version, model id }
```

- The harness is a pure-Kotlin JVM component in `core:model-adapter`; it depends only on the tool contract and the model abstraction (no Android APIs).
- The dataset and results stores are data artifacts (JSONL / JSON), never compiled into the app.
- Every adapter (NeedleModel, FunctionGemmaModel, LfmModel, MockModel) is exercised through the identical harness code path.

## Data Contracts

```kotlin
// core:model-adapter — evaluation harness contracts
@Serializable data class EvaluationRecord(
    val id: String,                      // e.g. "direct_command-001"
    val category: EvaluationCategory,    // one of the 10 categories
    val prompt: String,
    val expectedTool: String? = null,    // null when no tool is expected
    val expectedArgs: JsonObject? = null,// null when no arguments are expected
    val expectedRejection: String? = null, // null when no rejection is expected
    val notes: String? = null,
)

enum class EvaluationCategory {
    DIRECT_COMMAND, PARAPHRASE, AMBIGUOUS, INVALID_ARGUMENTS, UNKNOWN_TOOL,
    MULTIPLE_TOOL_REQUESTS, IRRELEVANT, MALFORMED_JSON, MALICIOUS, PROMPT_INJECTION
}

data class EvaluationReport(
    val modelId: String,
    val datasetVersion: String,
    val toolDefinitionVersion: String,
    val metrics: Metrics,
    val records: List<RecordResult>,     // per-record pass/fail + observed response
)

data class Metrics(
    val toolSelectionAccuracy: Double,   // correct tool chosen / total applicable
    val argumentAccuracy: Double,        // correct arguments / correct-tool records
    val invalidCallRejection: Double,    // invalid/malicious/unknown correctly rejected
    val falsePositiveRate: Double,       // tool call produced when none expected
    val latencyMs: Double,               // mean latency per generate()
    val memoryKb: Double,                // peak memory observed
    val modelSizeMb: Double,             // reported model artifact size
    val tokensPerSec: Double,            // mean generation throughput
)
```

- JSONL record schema on disk: `{"id":"...","category":"direct_command","prompt":"...","expected_tool":"set_brightness","expected_args":{"value":50},"expected_rejection":null,"notes":"..."}` — the `expected_*` keys are omitted or null when not applicable.
- The dataset version string encodes the tool-definition contract version it was authored against (e.g. `dataset-v1@tooldef-v1`).

## Security

- The dataset contains only synthetic, authored prompts — never real user input, credentials, or device data.
- `prompt_injection` and `malicious` categories are security-critical: a model that follows injected instructions fails its evaluation. The harness treats all model output as untrusted data (spec 0006 S1).
- Dataset and results stores are data only; they are never loaded as executable code or configuration that could influence runtime authorization.

## Error Handling

- Records whose prompt is malformed or whose category is unknown are rejected at dataset load time with a structured error (never silently skipped).
- A model that throws during `generate()` for a record is scored as a failure for that record with the exception captured in `RecordResult`, never as a harness crash.
- A missing `expected_tool` with a non-null `expected_args`, or vice versa, is a dataset authoring error rejected at load time.
- Harness failures (load, schema, runner) are reported as structured failures; they do not produce a partial report that could be mistaken for a valid benchmark.

## Acceptance Criteria

- AC1. The dataset contains exactly 120 records, 12 per category, English-only, generated with a fixed seed (E1).
- AC2. Every record conforms to the JSONL schema; `expected_*` fields are consistent (E2).
- AC3. `evaluate(model, definitions, dataset)` compiles against any `AgentModel` implementation with no adapter-specific branches (E3).
- AC4. The report exposes all 8 metrics with defined semantics (E4).
- AC5. Results are stored in a separate versioned store; source code contains no hard-coded benchmark numbers (E5).
- AC6. MockModel with a fixed seed yields identical reports across two identical runs (determinism, spec 0003 A4/A5).
- AC7. `prompt_injection` and `malicious` records cannot produce a passing tool call.

## Verification

- V1. Load the dataset with a schema validator and assert 120 records and 12 per category.
- V2. Grep the runtime and harness sources for hard-coded metric values and assert none exist (results live in the results store only).
- V3. Run the harness twice against MockModel with the same seed and diff the reports (must be byte-identical).
- V4. Run the full dataset against MockModel and against Needle in Phase 1 and confirm comparable, versioned outputs.
- V5. Assert the dataset version string changes whenever a tool-definition contract that alters expected arguments changes.