# AgentAI

A **local-first Android tool-calling agent runtime** in Kotlin. Converts natural-language instructions into safe, structured tool calls using very small on-device models (e.g. Cactus Needle ~26M, FunctionGemma 270M).

The architecture is **model-independent**: the application never couples to a specific model, and the model can only request registered tools — it never executes Android APIs directly.

## Status

- **Phase 0 (Architecture & SDD)** — complete, archived. Docs, specs, ADRs and the Phase 1 plan live in `docs/`.
- **Phase 1 (Implementation)** — source written, toolchain skeleton in place. **No build has been executed yet**: install JDK 17 + Gradle and run `./gradlew test` (ADR-0005 flips `strict_tdd` on).

## Core Flow

```
User
  ↓
AgentRuntime          state machine, orchestrates via interfaces only
  ↓
AgentModel            model adapter (MockModel today; Needle/FunctionGemma later)
  ↓
Structured ToolCall   typed, validated
  ↓
ToolRegistry          registered tools only, deterministic
  ↓
Argument Validation   no-coercion JSON Schema subset
  ↓
Security / Policy     classification + permissions + confirmation gate
  ↓
Android Tool          executes; model never touches Android APIs
  ↓
ToolResult            structured
  ↓
AgentResponse
```

The model can ONLY request registered tools. No arbitrary code, shell, Intent, reflection, filesystem, or unrestricted Android API access.

## Modules

| Module | Kind | Role |
|---|---|---|
| `core:tool-contract` | JVM | `ToolDefinition`, `ToolCall`, `ToolResult`, JSON Schema subset, `ToolCallValidator` |
| `core:tool-registry` | JVM | `ToolRegistry` — register/get/definitions/execute, deterministic |
| `core:security` | JVM | `SecurityClassifier`, `ToolPolicy`, `PermissionEvaluator`, `ConfirmationGate` |
| `core:model-adapter` | JVM | `AgentModel`, `ModelRequest/Response`, `MockModel`, evaluation harness |
| `app:agent-runtime` | JVM | `AgentRuntime` state machine (single-shot) |
| `app:android-tools` | Android | 6 tool implementations (Android APIs) |
| `app:whisper` | Android | On-device speech-to-text: whisper.cpp JNI bridge, model repo (SHA-256 pinned), recorder, voice state machine |
| `app:ui` | Android | Compose surface + confirmation dialog + ViewModel + hold-to-talk |
| `app:app` | Android | Application wiring (manual DI) |

Dependency direction: `ui → agent-runtime → {registry, security, model-adapter} → tool-contract`. `android-tools → tool-contract` only. Adapters never call Android APIs; tools never contain UI; UI never executes tools. The voice path (`whisper → ui`) feeds plain text into the same runtime.

## Security Model

Every model output is untrusted input. Authorization belongs to deterministic application code.

Each tool carries a classification:

- `SAFE` — e.g. `get_current_time`, `get_battery_status`, `get_device_info`
- `CONFIRMATION_REQUIRED` — e.g. `set_brightness`, `set_volume`, `open_app`
- `FORBIDDEN` — shell, code execution, credential extraction, unrestricted filesystem

Permission preflight runs before execution; confirmation gates are fail-closed (`DenyByDefaultGate`). The UI confirmation dialog implements the same `ConfirmationGate` interface as the headless gate.

## Evaluation

`core:model-adapter` ships a model-agnostic evaluation harness (spec 0009): 10 categories × 12 records = **120 deterministic records** (`SeedDataset`, fixed seed). Metrics focus on **intent → correct tool → correct arguments**, not conversational quality:

- tool selection accuracy, argument accuracy, invalid-call rejection, false-positive tool calls, latency

`MockModel` is a deterministic test oracle — not a product inference engine.

## Voice Input

Hold-to-talk voice input (spec 0010): press to record in **any language**, release to transcribe **on-device** with whisper.cpp (`app:whisper`). Language is auto-detected and output is always English, then fed through the same `AgentRuntime.submit()` path.

- One multilingual model — there are no per-language Whisper models. Default: `ggml-base-q5_1.bin` (~60 MB), downloaded once and **SHA-256 pinned**.
- The model is loaded from `filesDir/models` and cached for offline reuse; a corrupt file is deleted and re-downloaded.
- Requires `RECORD_AUDIO` runtime permission (requested on first launch; mic disabled until granted).
- Native build: whisper.cpp v1.7.4 via CMake FetchContent → `libwhisper-jni.so` (arm64-v8a + x86_64). NDK required.

## Build & Test

Requirements: JDK 17, Android SDK (compileSdk 35, minSdk 26). Gradle 8.11.1 (wrapper properties provided; generate the wrapper jar with `gradle wrapper` if missing). The whisper module additionally needs the Android **NDK + CMake** for the native build.

```bash
./gradlew test                    # all JVM tests (core + agent-runtime + whisper + Robolectric tool tests)
./gradlew assembleDebug           # Android modules (compiles libwhisper-jni.so)
./gradlew lint                    # Android lint
```

Android-boundary tools (`set_brightness`, `set_volume`, `open_app`, …) are exercised in the JVM with **Robolectric** shadows; device-only behavior is covered by Phase 1 Step 9 instrumented tests.

Strict TDD mode activates the moment the test runner exists (ADR-0005): all tests are written RED first.

## API Documentation

API docs are generated with **Dokka 2.1.0** (multi-module). Only the public API modules are documented — `android-tools`, `ui` and `app` are internal layers and intentionally excluded.

```bash
./gradlew dokkaHtmlMultiModule    # output: build/docs/api
```

Sources carry KDoc; contracts are `@Serializable` for the JSON boundary.

## CI & Dependency Updates

- **GitHub Actions** (`.github/workflows/ci.yml`): runs all JVM tests, assembles Android modules, and publishes Dokka API docs on every push/PR.
- **Dependabot** (`.github/dependabot.yml`): tracks `gradle/libs.versions.toml` and opens a PR per dependency update. Patch/minor bumps are grouped; Compose + Kotlin move as one unit; major bumps get individual review. Every PR is validated by CI before merge.

## Documentation

- `docs/specs/` — SDD specifications (0001–0010)
- `docs/decisions/` — ADRs (0001–0006)
- `docs/plans/` — implementation plans

## License

Proprietary / not yet specified.