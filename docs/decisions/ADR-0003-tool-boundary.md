# ADR-0003 — Tool Boundary

## Status

Approved.

## Context

The core security property of the runtime: model output is untrusted data and can never be interpreted as authorization (spec 0006 S1). The model must be able to request *registered* tools only — never arbitrary Kotlin code, shell commands, arbitrary Intents, reflection, filesystem access, or unrestricted Android APIs. Without an explicit boundary, a small model's output could be naively mapped to dangerous execution surfaces.

The forbidden model surfaces (AGENTS.md, spec 0001 F6):

- Model → arbitrary Kotlin code
- Model → shell
- Model → arbitrary Intent
- Model → reflection
- Model → filesystem access
- Model → unrestricted Android APIs

Decision priority (AGENTS.md): Security is the highest-ranked goal.

## Decision

The model can only request registered tools. The only execution path from model output to platform effect is:

```
ModelResponse.ToolCalls ──▶ ToolRegistry.execute(call)
        ──▶ ToolCallValidator (exact types, no coercion)
        ──▶ ToolPolicy.evaluate (classification → permission preflight → confirmation gate)
        ──▶ Tool.run(arguments) ──▶ ToolResult
```

Every step between the model and a `Tool` is deterministic application code (registry, validator, policy). Unknown tools, malformed arguments, denied permissions, and unconfirmed calls produce structured `ToolResult.Failure` values and never reach a `Tool`. `Tool` implementations are the only place Android APIs are called, and they are registered explicitly at app composition (spec 0005, 0007).

## Consequences

- Security-critical enforcement lives in deterministic code, not in model behavior — a malicious or confused model cannot escalate beyond the registered tool set.
- Tools are validated, permission-checked, and confirmation-gated before execution; failures are structured (`ToolError`).
- The tool registry (spec 0005) and security policy (spec 0006) become the security boundary; both are JVM-testable with no Android dependencies.
- Adapters remain free of Android API calls and tool implementations (ADR-0002), keeping the boundary enforceable at every layer.