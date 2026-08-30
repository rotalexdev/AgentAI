# 0010 — Voice Input via On-Device Whisper

## Status

Draft

## Context

AgentAI input is text-only (spec 0008 U1: OutlinedTextField). Users want to
speak a request in any language and have the agent act on it. Local-first
mandate (ADR-0001) requires on-device transcription: audio must never leave
the device.

Whisper is **one multilingual model** — there is no per-language model to
download. Source language is auto-detected from audio (`language=""`), and
`translate=true` makes the output English regardless of the source. The
"download the correct model for that language" mental model is incorrect;
the only real variant is the smaller English-only `.en` family, which we do
not use in v1.

## Goals

- G1: Hold-to-talk recording (press starts, release stops).
- G2: On-device transcription of any language to **English** (auto-detect +
  translate).
- G3: Feed the English text into the existing `AgentRuntime.submit()` path
  unchanged — the runtime must not know the input was spoken.
- G4: One-time model download with SHA-256 pinning; verified cache for
  offline reuse.
- G5: Deterministic, testable voice state machine.

## Non-goals

- No cloud STT; no VAD/auto-stop; no streaming; no GPU/Vulkan; no
  multi-utterance; no per-language model variants; no changes to the core
  agent pipeline.

## Requirements

- V1: New Android library module `app/whisper` builds whisper.cpp v1.7.4 via
  CMake FetchContent + JNI (`libwhisper-jni.so`), ABIs `arm64-v8a` + `x86_64`.
- V2: `SpeechToText` interface (`transcribe(pcm: FloatArray): SpeechResult`)
  with `WhisperSpeechToText` implementation. JNI bridge exposes
  `initContext`, `transcribe` (auto language + translate), `getDetectedLanguage`,
  `freeContext`.
- V3: `ModelRepository.obtain(entry)` downloads once to cache, verifies
  SHA-256, deletes corrupt files and re-downloads; valid cache = offline reuse.
  Downloader is injected for testability.
- V4: `ModelCatalog.default` = `ggml-base-q5_1.bin` (~60 MB), URL
  `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin`,
  SHA-256 `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`
  (verified 2026-08-17 against HF LFS metadata).
- V5: `AndroidAudioRecorder` captures 16 kHz mono PCM16 via
  `AudioRecord(VOICE_RECOGNITION)`; `start()`/`stop()` hold-to-talk semantics.
- V6: `VoiceInputController` state machine
  `Idle → Recording → Transcribing → Idle | Error`; `startRecording` no-ops
  outside Idle/Error; callback fires only for non-blank text; errors surface
  in state.
- V7: UI: hold-to-talk surface (`detectTapGestures` onPress/onRelease), voice
  state text, RECORD_AUDIO runtime permission requested before enabling.
- V8: Wiring in `AgentApp` composition root — no DI framework.

## Architecture

```
AudioRecorder ── FloatArray PCM ──> SpeechToText (WhisperSpeechToText)
                                        │
                                        ├─ ModelRepository (SHA-256 pinned cache)
                                        └─ WhisperLib (JNI → whisper.cpp)
VoiceInputController ── state ──> AgentViewModel ── submit(text) ──> AgentRuntime (unchanged)
```

Speech-to-text is behind an interface (V2) exactly like the AgentModel
abstraction from spec 0003: Whisper is one possible engine, not a hard
dependency of the rest of the system.

## Data Contracts

```kotlin
data class SpeechResult(val text: String, val detectedLanguage: String)
interface SpeechToText { suspend fun transcribe(pcm: FloatArray): SpeechResult }

data class ModelEntry(val id: String, val url: String, val sha256: String, val sizeBytes: Long)
object ModelCatalog { val default: ModelEntry }
```

## Security

- Model bytes are **untrusted** until SHA-256 verified (V3) — a mismatched
  file is deleted, never loaded.
- RECORD_AUDIO is a runtime-dangerous permission: denied → Error state, the
  mic surface stays disabled.
- Audio never leaves the device; the only network traffic is the one-time
  HTTPS model download.
- No new capabilities are exposed to the model: the voice path produces plain
  text that flows through the existing tool registry + policy.

## Error Handling

| Failure | Behavior |
|---|---|
| RECORD_AUDIO denied | `VoiceInputState.Error`, mic surface disabled |
| Model download / checksum mismatch | `VoiceInputState.Error`, no model loaded |
| AudioRecord init failure | `VoiceInputState.Error` |
| Transcription failure | `VoiceInputState.Error` |
| Blank transcription | stay `Idle`, no callback, nothing submitted |

## Acceptance Criteria

- A1: `VoiceInputControllerTest` covers transitions, no-op guards, error
  paths, blank-text skip, reset.
- A2: `ModelRepositoryTest` covers fresh download, cache reuse, corrupt
  re-download, checksum fail-closed, undersized fail-closed.
- A3: `ModelCatalogTest` validates URL/sha256/id/size.
- A4: AgentScreen compiles with hold-to-talk surface + voice state;
  MainActivity requests RECORD_AUDIO.
- A5: No changes to `AgentRuntime`, `ToolRegistry`, `ToolPolicy` or the
  `core:*` modules.

## Verification

- JVM unit tests (`:app:whisper:test`, `:app:ui:test`) once the toolchain runs.
- Native build: CI `assembleDebug` must compile `libwhisper-jni.so`.
- Manual device check: grant RECORD_AUDIO, hold-to-talk, speak in en/es/de
  ("set brightness to 50"), verify English text reaches the runtime and the
  tool executes (or asks confirmation).