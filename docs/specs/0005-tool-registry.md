# 0005 — Tool Registry: Deterministic Registration, Discovery, Execution

## Status

Approved (blueprint; implementation scheduled for Phase 1).

## Context

The ToolRegistry is the explicit, deterministic gateway between the model and the tool implementations. Every tool lives here; the runtime and model interact with tools only through this registry. It guarantees unique names, deterministic discovery order (stable, sorted), and fail-safe execution that never throws and never lets an unknown or malformed request reach an actual `Tool`.

## Goals

- G1. Expose `register`, `unregister`, `get`, `definitions`, and `execute`.
- G2. Enforce unique names: duplicate registration is rejected.
- G3. Produce deterministic, stable `definitions()` ordered by name (for stable prompts).
- G4. Make `execute` never throw: unknown tool → `UNKNOWN_TOOL`, malformed args → `MALFORMED_ARGUMENTS`, denied tools → the respective `ToolError`, all as `ToolResult.Failure`.

## Non-goals

- Dynamic discovery of tools from model output.
- Tools that load or are defined by the model/LLM at runtime.
- Arbitrary code execution based on registry content.
- Thread-affinity or lifecycle management of Android tool implementations (handled in `app:android-tools`).

## Requirements

- G1 (RFC 2119 — SHALL): The registry interface **SHALL** expose:
  - `fun register(tool: Tool)`
  - `fun unregister(name: String): Boolean`
  - `fun get(name: String): Tool?`
  - `fun definitions(): List<ToolDefinition>` — sorted by name, stable order
  - `suspend fun execute(call: ToolCall): ToolResult`
- G2 (RFC 2119 — MUST): Tool names **MUST** be unique. Registering a duplicate name **MUST** throw `IllegalArgumentException`.
- G3 (RFC 2119 — SHALL): `definitions()` **SHALL** return tools sorted by name, with a stable order across calls with identical registry contents.
- G4 (RFC 2119 — MUST NOT): `execute` **MUST NOT** throw. Unknown tool name → `Failure(UNKNOWN_TOOL)`; malformed arguments → `Failure(MALFORMED_ARGUMENTS)`; policy-denied tool → the respective `ToolError`; all represented as `ToolResult.Failure`.

### Scenarios (GIVEN/WHEN/THEN)

- GIVEN `set_brightness` already registered, WHEN `register(set_brightness)` is attempted again, THEN `IllegalArgumentException` is thrown and the registry content is unchanged.
- GIVEN a call to a name that was never registered, WHEN `execute(call)` runs, THEN `Failure(UNKNOWN_TOOL)` and no `Tool` is instantiated or invoked.
- GIVEN two identical registries, WHEN `definitions()` is called twice, THEN both results are identical byte-for-byte lists.

## Architecture

```
core:tool-registry
   ToolRegistry (interface) ──▶ in-memory map; deterministic sorted view
        register(tool)  → validates name (identifier + uniqueness)
        get(name)       → Tool? (null for unknown)
        definitions()   → sorted-by-name List<ToolDefinition>
        execute(call)   → ToolCallValidator → Tool.run(args) → ToolResult
```

Dependencies: `core:tool-registry` depends only on `core:tool-contract`. Concurrency: `register`/`unregister` are effectively single-writer during app composition; `execute` runs in the runtime's suspend context and must be safe for concurrent invocation of distinct tools (no shared mutable tool state).

## Data Contracts

```kotlin
// core:tool-contract / core:tool-registry
interface Tool {
    val definition: ToolDefinition
    suspend fun run(arguments: JsonObject): ToolResult
}

interface ToolRegistry {
    fun register(tool: Tool)                 // duplicate name → IllegalArgumentException
    fun unregister(name: String): Boolean
    fun get(name: String): Tool?
    fun definitions(): List<ToolDefinition>  // sorted by name, stable order
    suspend fun execute(call: ToolCall): ToolResult  // unknown/malformed/denied → Failure, never throws
}
```

Tool name validation rule (accepted by the registry): `^[a-z][a-z0-9_]*$`, length ≤ 64. Any other name is rejected at `register`.

## Security

- The registry performs the no-coercion argument validation (spec 0004) before tool execution.
- FORBIDDEN-classified tools cannot be registered (spec 0006 enforces this at registration via `SecurityClassifier`); the registry must surface a registration error for any tool whose classification is FORBIDDEN.
- Every execution goes through the registry so that execution happens outside the model and every execution produces a structured `ToolResult`.

## Error Handling

| Failure | Handling |
|---|---|
| Duplicate name at register | `IllegalArgumentException`; no mutation. |
| Invalid tool name | `IllegalArgumentException`; no mutation. |
| FORBIDDEN tool at register | Registration rejected (as per spec 0006); no mutation. |
| Unknown tool at execute | `Failure(UNKNOWN_TOOL)`. |
| Malformed arguments at execute | `Failure(MALFORMED_ARGUMENTS)`; tool never run. |
| Policy denial (permission/confirmation) | `Failure(PERMISSION_DENIED | CONFIRMATION_REQUIRED)` from the policy chain (spec 0006). |
| Tool throws at `run` | Wrapped as `Failure(EXECUTION_ERROR)`; never propagates. |

## Acceptance Criteria

- AC1. Registering the same name twice throws `IllegalArgumentException` and leaves the registry unchanged.
- AC2. `execute` on an unregistered name returns `Failure(UNKNOWN_TOOL)` without instantiating any tool.
- AC3. `definitions()` is sorted by name and identical for identical registry contents (deterministic prompts).
- AC4. `execute` on malformed arguments returns `Failure(MALFORMED_ARGUMENTS)` and never reaches the tool.
- AC5. A FORBIDDEN-classified tool cannot be registered.
- AC6. `execute` never throws for any input (all failures are `ToolResult.Failure`).

## Verification

- V1. Unit tests (Phase 1, strict TDD after ADR-0005): duplicate-name rejection, unknown-tool, malformed-arguments, determinism of `definitions()`, FORBIDDEN registration rejection (G1–G4).
- V2. Property-style determinism check: build the same registry twice, assert identical `definitions()` lists.
- V3. Execution never throws: run `execute` against a corpus of adversarial calls (unknown, malformed, denied) and assert all return `ToolResult.Failure`.
- V4. No Android imports in `core:tool-registry` sources (pure-Kotlin, JVM-testable).