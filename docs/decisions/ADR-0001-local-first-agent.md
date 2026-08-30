# ADR-0001 — Local-First Agent Runtime

## Status

Approved.

## Context

The product is a native Android AI-agent project written exclusively in Kotlin. Its core objective is converting natural-language instructions into safe, structured tool calls using extremely small on-device models (Needle ~26M, FunctionGemma 270M). The application is not primarily a chatbot; it is a **local Android tool-calling agent runtime**.

The team must decide whether the core agent loop may depend on a cloud model, a cloud backend, or network connectivity. Cloud models offer higher capability but introduce availability, privacy, latency, and cost concerns for a device that should function as a dependable personal tool.

Decision priority (AGENTS.md): Security > Determinism > Correct tool execution > Model independence > Local execution > Testability > Maintainability > Performance > UI polish. Local execution ranks above testability and performance, and the product charter explicitly names a local-first runtime.

## Decision

The runtime SHALL be **local-first**: the core agent loop (User → AgentRuntime → ModelAdapter → ToolCall → ToolRegistry → validation → policy → Tool → ToolResult → AgentResponse) MUST NOT require a cloud model, a cloud backend, or network access for correct operation. All inference runs on-device through the model abstraction; optional network features are future work and must not become dependencies of the core loop.

## Consequences

- The core loop works offline and on-device; privacy of prompts is preserved locally.
- Model capability is bounded by what small on-device models can do; the tool contract and evaluation methodology (specs 0004, 0009) are designed around that constraint.
- The model abstraction (ADR-0002) is a hard requirement — no single on-device model may become the application.
- Model independence (ADR-0002) and the tool boundary (ADR-0003) follow from this decision.