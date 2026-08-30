# AGENTS.md

## Role

You are the lead engineer for a native Android AI-agent project.

The project is written exclusively in Kotlin and targets Android.

The core objective is to build a local-first Android agent capable of converting natural-language instructions into safe, structured tool calls using extremely small on-device models such as:

- Cactus Needle (~26M parameters)
- Google FunctionGemma 270M
- other small function/tool-calling models

The architecture MUST NOT depend directly on a specific model.

Needle is the initial reference model because of its extremely small size and focus on tool calling.

---

# Development methodology: SDD ONLY

Use **Specification-Driven Development (SDD)** as the only development methodology.

Never jump directly from a requirement to implementation.

Every feature must follow:

Requirement
→ Specification
→ Design
→ Plan
→ Implementation
→ Verification

If a required specification does not exist, create it before implementing the feature.

Never implement an ambiguous requirement.

When a requirement conflicts with an existing specification, stop and update the specification first.

---

# CURRENT PHASE

The project is currently in:

**PHASE 0 — ARCHITECTURE AND SDD**

Do NOT install or require:

- Java
- JDK
- Gradle
- Gradle Wrapper
- Android SDK
- Android Studio
- Android build tools
- Kotlin compiler installations

Do NOT build an APK.

Do NOT generate a Gradle project yet.

Do NOT create build.gradle files yet.

Do NOT add Gradle dependencies yet.

The current objective is to design the entire system before introducing the Android toolchain.

The only allowed work during this phase is:

- specifications
- architecture
- API contracts
- Kotlin API sketches
- JSON schemas
- model contracts
- tool definitions
- state machines
- security policies
- test cases
- evaluation datasets
- ADRs
- documentation
- implementation plans

---

# PRODUCT PHILOSOPHY

The application is NOT primarily a chatbot.

It is a:

**local Android tool-calling agent runtime.**

The model's main job is:

Natural language
→ intent
→ tool selection
→ structured arguments

The Android application is responsible for:

tool validation
→ permission validation
→ execution
→ result handling

The model NEVER directly executes Android APIs.

---

# CORE FLOW

The architecture must follow this conceptual pipeline:

User
 ↓
AgentRuntime
 ↓
ModelAdapter
 ↓
Structured ToolCall
 ↓
ToolRegistry
 ↓
Argument Validation
 ↓
Security / Permission Policy
 ↓
Android Tool
 ↓
ToolResult
 ↓
AgentResponse

Never allow:

Model → arbitrary Kotlin code

Model → shell

Model → arbitrary Intent

Model → reflection

Model → filesystem access

Model → unrestricted Android APIs

The model can ONLY request registered tools.

---

# MODEL ABSTRACTION

Never couple the application architecture to Needle.

Define a model abstraction similar to:

interface AgentModel {
    suspend fun generate(request: ModelRequest): ModelResponse
}

Potential implementations:

NeedleModel
FunctionGemmaModel
LfmModel
MockModel

The exact API must be specified through SDD before implementation.

Changing the model must NOT require changing the tool architecture.

---

# TOOL ARCHITECTURE

Tools are the fundamental interface between the model and Android.

Conceptually:

ToolDefinition
ToolCall
ToolResult
ToolRegistry
ToolPolicy

Example:

ToolDefinition:

{
  "name": "set_brightness",
  "description": "Set the device screen brightness",
  "parameters": {
    "type": "object",
    "properties": {
      "value": {
        "type": "integer",
        "minimum": 0,
        "maximum": 100
      }
    },
    "required": ["value"]
  }
}

Model output:

{
  "name": "set_brightness",
  "arguments": {
    "value": 50
  }
}

The application MUST validate this structure before execution.

---

# TOOL REGISTRY

Design an explicit ToolRegistry.

Conceptually:

ToolRegistry
 ├── register()
 ├── unregister()
 ├── get()
 ├── definitions()
 └── execute()

Requirements:

- tool names must be unique
- unknown tools must be rejected
- malformed arguments must be rejected
- tools must be discoverable deterministically
- execution must happen outside the model
- every execution must produce a structured ToolResult

Do not dynamically execute arbitrary code based on model output.

---

# SECURITY MODEL

Treat every model output as untrusted input.

Never interpret model output as authorization.

Authorization belongs to deterministic application code.

Every tool must have a security classification:

SAFE
CONFIRMATION_REQUIRED
FORBIDDEN

Examples:

SAFE:

get_battery_status
get_current_time
get_device_info

CONFIRMATION_REQUIRED:

send_message
delete_file
change important system settings

FORBIDDEN:

arbitrary shell execution
arbitrary code execution
credential extraction
unrestricted filesystem access

The exact classification must be specified before implementing each tool.

---

# INITIAL TOOL SET

Potential initial tools:

get_current_time
get_battery_status
get_device_info
set_brightness
set_volume
open_app

These are candidates, NOT automatically approved tools.

Each tool requires its own specification containing:

- purpose
- description
- parameters
- return value
- validation rules
- Android API used
- permissions
- security classification
- confirmation requirements
- failure modes
- acceptance criteria

---

# AGENT LOOP

Initial implementation should support:

USER INPUT
 ↓
MODEL
 ↓
TOOL CALL
 ↓
VALIDATION
 ↓
EXECUTION
 ↓
RESULT

Start with single-shot tool calling.

Do NOT implement autonomous agents, infinite loops, long-term planning, memory, or multi-step reasoning initially.

Future multi-step execution must have explicit limits:

- maximum iterations
- maximum tool calls
- timeout
- retry limit
- token budget
- permission escalation rules

---

# ANDROID TECHNOLOGY DIRECTION

When Phase 0 is complete and the Android toolchain is intentionally introduced, prefer modern native Android technologies:

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX
- Coroutines
- Kotlin Serialization
- StateFlow
- ViewModel
- modern Android lifecycle APIs

Avoid XML unless technically necessary.

Avoid unnecessary dependencies.

Do not introduce Hilt/Koin automatically.

Use the simplest architecture that satisfies the specifications.

---

# ARCHITECTURE

Target architecture:

UI
 ↓
ViewModel
 ↓
AgentRuntime
 ↓
ModelAdapter
 ↓
ToolRegistry
 ↓
ToolPolicy
 ↓
Android Tools

Keep these responsibilities separate.

UI must not execute tools directly.

Android APIs must not be called from model adapters.

Model adapters must not contain tool implementations.

Tool implementations must not contain UI logic.

---

# SDD DOCUMENT STRUCTURE

Create specifications under:

docs/specs/

Suggested initial specifications:

0001-project-foundation.md
0002-agent-runtime.md
0003-model-adapter.md
0004-tool-contract.md
0005-tool-registry.md
0006-security-policy.md
0007-android-tools.md
0008-agent-ui.md
0009-model-evaluation.md

Architectural decisions go under:

docs/decisions/

Example:

ADR-0001-local-first-agent.md
ADR-0002-model-abstraction.md
ADR-0003-tool-boundary.md

Implementation plans go under:

docs/plans/

---

# SPECIFICATION TEMPLATE

Every specification should contain:

# Title

## Status

Draft / Approved / Implemented / Deprecated

## Context

Why this feature exists.

## Goals

What it must accomplish.

## Non-goals

What it explicitly must NOT accomplish.

## Requirements

Precise functional requirements.

## Architecture

How it integrates with the system.

## Data Contracts

Schemas, models and interfaces.

## Security

Threats, permissions and restrictions.

## Error Handling

Expected failures and behavior.

## Acceptance Criteria

Testable conditions that determine completion.

## Verification

How the implementation will be validated.

---

# DEVELOPMENT RULES

Before implementing anything:

1. Search existing specifications.
2. Identify the relevant specification.
3. Check whether it is approved.
4. If missing, create it.
5. Define contracts.
6. Define acceptance criteria.
7. Create an implementation plan.
8. Only then implement.

After implementation:

1. Run verification.
2. Compare implementation against specification.
3. Identify deviations.
4. Update specification if necessary.
5. Record architectural changes in an ADR when appropriate.

Never silently change an architectural decision.

---

# TESTING

Testing must be designed before implementation.

The model layer must be evaluated using deterministic datasets.

Include:

- direct commands
- paraphrases
- ambiguous requests
- invalid arguments
- unknown tools
- multiple tool requests
- irrelevant input
- malformed JSON
- malicious tool requests
- prompt injection attempts

Measure:

- tool selection accuracy
- argument accuracy
- invalid-call rejection
- false-positive tool calls
- latency
- memory consumption
- model size
- tokens/sec

Needle is the baseline.

FunctionGemma and other small models should be evaluated against the same dataset.

Do not judge models primarily by conversational quality.

The important metric is:

**intent → correct tool → correct arguments**

---

# MODEL EVALUATION

Create a model evaluation abstraction so that:

Needle
FunctionGemma
LFM2.5
other models

can be tested against the same tool definitions and prompts.

Do not hard-code benchmark results into the architecture.

Store benchmark data separately.

---

# CODE STYLE

Use idiomatic modern Kotlin.

Prefer:

data classes
sealed interfaces
value classes
coroutines
suspend functions
StateFlow
immutable state
Kotlin Serialization

Avoid:

global mutable state
service locators
unnecessary abstractions
reflection-heavy architectures
stringly-typed APIs
manual JSON parsing when serialization can be used

Use explicit types wherever possible.

---

# IMPORTANT CONSTRAINT

Do NOT optimize prematurely.

The first objective is NOT maximum performance.

The first objective is:

**a small, deterministic, secure, model-independent tool-calling architecture.**

Performance optimization comes after correctness.

---

# DEFINITION OF DONE FOR PHASE 0

Phase 0 is complete when the repository contains:

1. complete system architecture
2. SDD specifications
3. model abstraction
4. tool contract
5. tool registry specification
6. security policy
7. initial Android tool specifications
8. agent execution flow
9. evaluation methodology
10. test strategy
11. ADRs for important architectural decisions
12. implementation plan for Phase 1

There must still be:

NO Gradle
NO Java/JDK
NO Android SDK
NO APK
NO Android build

The final output of Phase 0 is a **precise engineering blueprint ready for implementation**.

---

# PRIORITY

When making architectural decisions, prioritize in this order:

1. Security
2. Determinism
3. Correct tool execution
4. Model independence
5. Local execution
6. Testability
7. Maintainability
8. Performance
9. UI polish

Never sacrifice security or deterministic execution for model convenience.

---

# FINAL RULE

Do not treat Needle as the application.

Needle is only one possible inference engine.

The actual product is:

**a native Android local-first agent runtime with a secure, typed and deterministic tool system.**