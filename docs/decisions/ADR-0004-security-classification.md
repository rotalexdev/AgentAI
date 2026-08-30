# ADR-0004 — Security Classification

## Status

Approved.

## Context

Every tool must carry a security classification (SAFE / CONFIRMATION_REQUIRED / FORBIDDEN). The team considered two approaches: attaching a classification label to each tool definition, or deriving the classification from rule-based properties of the tool's effect. Per-tool labels drift: a developer can mark a dangerous tool SAFE by mistake, and labels are not uniformly auditable.

Decision priority (AGENTS.md): Security is the highest-ranked goal; determinism comes second.

## Decision

Classification SHALL be **rule-based**, derived from the tool's declared side effects and PII exposure via `SecurityClassifier.classify(sideEffect, piiExposure)` (spec 0006 S2, design D3):

- No side effect + no PII exposure ⇒ **SAFE** (e.g. `get_current_time`, `get_battery_status`).
- Any side effect, system mutation, or action that leaves the app ⇒ **CONFIRMATION_REQUIRED** (e.g. `set_brightness`, `set_volume`, `open_app`).
- Shell, code execution, credential extraction, filesystem access, reflection, or arbitrary Intent ⇒ **FORBIDDEN** (cannot be registered at all).

`ToolPolicy.evaluate(tool, call)` returns `ALLOW | DENY | NEEDS_CONFIRMATION` and performs permission preflight (e.g. `WRITE_SETTINGS` → `set_brightness`, `MODIFY_AUDIO_SETTINGS` → `set_volume`); preflight failure yields `ToolResult.Failure(PERMISSION_DENIED)` (spec 0006 S3).

## Consequences

- Classification is deterministic and auditable: the same side-effect declaration always yields the same class.
- FORBIDDEN tools cannot be registered or executed — enforcement is architectural, not advisory (spec 0006 S4).
- Confirmation flows through an injectable `ConfirmationGate`; the headless `DenyByDefaultGate` fails closed (spec 0006 S5, design D6).
- The initial tool set (spec 0007) follows this scheme: three SAFE read-only tools and three CONFIRMATION_REQUIRED tools with explicit permission preflight.