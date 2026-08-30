# 0004 — Tool Contract: ToolDefinition, ToolCall, ToolResult and Bounded JSON Schema

## Status

Approved (blueprint; implementation scheduled for Phase 1).

## Context

Tools are the fundamental interface between the model and Android. The contract binds tool description (what the model sees), tool invocation (what the model requests), and tool result (what the platform returns). Argument types are validated against a deliberately bounded JSON Schema subset so that: the model cannot smuggle arbitrary structures, `additionalProperties` are rejected, and type coercion is forbidden (a string `"50"` is not an integer `50`). This contract is the single source of truth consumed by specs 0005, 0006, and 0009.

## Goals

- C1. Define `@Serializable` Kotlin data classes for `ToolDefinition`, `ToolCall`, `ToolResult` (sealed `Success`/`Failure`), and `ToolError`.
- C2. Define the bounded JSON Schema subset (primitives + arrays of primitives) with `additionalProperties: false`.
- C3. Require exact-type validation; reject malformed arguments with `MALFORMED_ARGUMENTS`.
- C4. Treat `ToolCall.arguments` as UNTRUSTED until validated.

## Non-goals

- Full JSON Schema support, `$ref`, `oneOf/anyOf/allOf`, `$schema` documents, or nested object schemas beyond the bounded subset.
- Any coercion, defaulting, or tolerance of type mismatches.
- Model-driven schema definitions (schemas are authored by deterministic application code).
- Network serialization or schema exchange at runtime.

## Requirements

- C1 (RFC 2119 — SHALL): Contracts **SHALL** be `@Serializable` Kotlin data classes using Kotlin Serialization:
  - `ToolDefinition(name, description, parameters: JsonSchemaObject)`
  - `ToolCall(id, name, arguments: JsonObject)`
  - `sealed interface ToolResult` with `Success(toolName, output: JsonObject)` and `Failure(toolName, error: ToolError, message)`
  - `enum class ToolError { UNKNOWN_TOOL, MALFORMED_ARGUMENTS, PERMISSION_DENIED, CONFIRMATION_REQUIRED, EXECUTION_ERROR }`
- C2 (RFC 2119 — SHALL): The JSON Schema subset **SHALL** be bounded (D4): `StringType` (with optional `enum`), `IntegerType`/`NumberType` (with optional `min`/`max`), `BooleanType`, `ArrayType` (items: primitives only), `ObjectType` (properties + `required`). `ObjectType.additionalProperties` **SHALL** always be `false`.
- C3 (RFC 2119 — SHALL): Validation **SHALL** require exact types and exact enums. Malformed arguments **MUST** be rejected with `MALFORMED_ARGUMENTS`. No coercion of any kind.
- C4 (RFC 2119 — SHALL): `ToolCall.arguments` **SHALL** be treated as UNTRUSTED until validated by the no-coercion validator.

### Scenarios (GIVEN/WHEN/THEN)

- GIVEN the schema `"value": {"type": "integer", "minimum": 0, "maximum": 100}` and the argument `"value": "50"` (a string), WHEN validated, THEN rejected with `MALFORMED_ARGUMENTS` (no coercion).
- GIVEN an argument object containing an unknown property not listed in the schema, WHEN validated, THEN rejected (additionalProperties is false).
- GIVEN `"value": 150` against `maximum: 100`, WHEN validated, THEN rejected.
- GIVEN `"value": 50` against `IntegerType(0, 100)`, WHEN validated, THEN accepted.

## Architecture

```
core:tool-contract
  ToolDefinition  → description/parameters shown to model
  ToolCall        → model request (UNTRUSTED)
  JsonSchema subset + Validator → rejects everything not exactly typed
  ToolResult      → Success | Failure (structured, never thrown)
```

Dependency direction: consumed by `core:tool-registry`, `core:security`, `core:model-adapter`, and `app:android-tools`. `android-tools` depends on this contract only.

## Data Contracts

Kotlin API sketch (design artifact — not implementation):

```kotlin
// core:tool-contract
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonSchemaObject,             // subset; additionalProperties always false
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,                    // UNTRUSTED until validated
)

@Serializable
sealed interface ToolResult {
    val toolName: String
    @Serializable data class Success(val toolName: String, val output: JsonObject) : ToolResult
    @Serializable data class Failure(val toolName: String, val error: ToolError, val message: String) : ToolResult
}

@Serializable
enum class ToolError { UNKNOWN_TOOL, MALFORMED_ARGUMENTS, PERMISSION_DENIED, CONFIRMATION_REQUIRED, EXECUTION_ERROR }

// JsonSchema bounded subset (D4)
sealed interface JsonSchemaObject {
    val type: String
    @Serializable data class StringType(val description: String? = null, val enum: List<String>? = null) : JsonSchemaObject
    @Serializable data class IntegerType(val description: String? = null, val minimum: Int? = null, val maximum: Int? = null) : JsonSchemaObject
    @Serializable data class NumberType(val description: String? = null, val minimum: Double? = null, val maximum: Double? = null) : JsonSchemaObject
    @Serializable data class BooleanType(val description: String? = null) : JsonSchemaObject
    @Serializable data class ArrayType(val description: String? = null, val items: JsonSchemaObject /* primitive only */) : JsonSchemaObject
    @Serializable data class ObjectType(
        val description: String? = null,
        val properties: Map<String, JsonSchemaObject>,
        val required: List<String> = emptyList(),
        val additionalProperties: Boolean = false,   // MUST always be false
    ) : JsonSchemaObject
}

interface ToolCallValidator {
    /** Exact types, exact enums, additionalProperties=false; no coercion.
     *  Returns Unit on success, or a validation error. */
    fun validate(call: ToolCall): Unit  // throws/reports MALFORMED_ARGUMENTS on mismatch
}
```

Serialization policy: `ModelRequest.toolDefinitions` and `ToolCall.arguments` serialize/deserialize via Kotlin Serialization with strict unknown-key handling — unknown keys in `ToolCall.arguments` fail validation in the validator, not silently.

## Security

- `ToolCall.arguments` are UNTRUSTED; the validator is the defeat barrier between the model and the tool.
- `additionalProperties: false` prevents property-sneaking into tools.
- Exact-type validation prevents type-confusion attacks (e.g. numeric `value` replaced by object string).
- `ToolDefinition.name` is validated as a stable `[a-z][a-z0-9_]*` identifier before registration to keep prompts and lookups unambiguous.
- Definitions are generated by application code, never by model output.

## Error Handling

| Failure | Handling |
|---|---|
| Type mismatch (string vs integer, etc.) | `MALFORMED_ARGUMENTS` (no coercion attempt). |
| Enum mismatch | `MALFORMED_ARGUMENTS`. |
| Unknown property in object | `MALFORMED_ARGUMENTS` (additionalProperties false). |
| Required property missing | `MALFORMED_ARGUMENTS`. |
| Out-of-range numeric | `MALFORMED_ARGUMENTS` (bounds enforced). |
| Non-primitive inside array | `MALFORMED_ARGUMENTS` (items primitive only). |
| Foreign/unparseable JSON | `MALFORMED_ARGUMENTS` (or caller-level parse failure). |

All validation failures are deterministic and never mutate tool state.

## Acceptance Criteria

- AC1. `"value": "50"` against `IntegerType` is rejected (no coercion).
- AC2. Unknown additional properties are rejected.
- AC3. `"value": 150` against `maximum:100` is rejected.
- AC4. `"value": 50` against `IntegerType(0,100)` passes.
- AC5. `ToolResult` is exclusively `Success` or `Failure`; `ToolError` enumerates all five codes.
- AC6. All contract types are Kotlin-Serialization-annotated and JVM-testable without Android.

## Verification

- V1. Unit tests (Phase 1, strict TDD after ADR-0005) covering AC1–AC4 and each `ToolError`.
- V2. Round-trip serialization tests for `ToolDefinition`, `ToolCall`, `ToolResult` using Kotlin Serialization.
- V3. Grep the implementation of `core:tool-contract` for `coerce`/`toInt`/`asInt`-style coercion functions: none may exist.
- V4. A doc-level drift check: the contracts in this spec must match the design record's `core:tool-contract` sketch verbatim.