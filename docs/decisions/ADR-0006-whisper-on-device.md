# ADR-0006 — On-Device Whisper for Voice Input

- Status: Accepted
- Date: 2026-08-17
- Deciders: lead engineer (user-directed), user confirmed product choices
- Related: ADR-0001 (local-first), spec 0003 (model abstraction), spec 0010 (voice input)

## Context

Voice input requires speech-to-text. Options:

1. **Cloud STT API** (OpenAI Whisper API, Google Speech, etc.) — trivial
   integration, high accuracy, but audio leaves the device and inference is
   not local.
2. **On-device whisper.cpp** — local-first, offline after model download,
   works with small quantized models (tiny 31 MB / base 60 MB / small 190 MB),
   mature Android JNI support, ~8× realtime on CPU for base.
3. **Hybrid** — cloud fallback when the local model is unavailable.

The project's charter prioritizes **Local execution (5)** above all but
security/determinism/correctness/model-independence, and ADR-0001 mandates a
local-first agent. Sending every voice request to a server contradicts the
core product identity.

## Decision

Use **on-device whisper.cpp v1.7.4** via a new `app/whisper` Android library
module with a CMake FetchContent native build and a thin JNI bridge. Default
model: `ggml-base-q5_1.bin` (~60 MB) with SHA-256 pinning. Language handling
is **auto-detect + translate-to-English** with a single multilingual model —
not per-language models (they do not exist; Whisper is one model).

Speech-to-text is exposed behind a `SpeechToText` interface so Whisper can be
swapped for any other engine without touching the agent pipeline, mirroring
the AgentModel abstraction (spec 0003). The voice path feeds plain English
text into the existing `AgentRuntime.submit()` — no runtime changes.

## Consequences

Positive:
- Local-first honored: audio never leaves the device.
- Works offline after one-time model download (60 MB, cached + verified).
- Additive: no changes to core modules, tool registry, or security policy.
- Deterministic state machine over a non-deterministic ASR layer.

Negative:
- Requires NDK + CMake toolchain in the build (Phase 1 tooling).
- 60 MB first-run download (mitigated by pinned checksum + cache).
- ASR accuracy varies by language/accent/background noise — base-q5_1 is a
  deliberate quality/size tradeoff; small-q5_1 (190 MB) is the upgrade path.
- JNI layer is a new native surface (kept minimal: 4 functions).

## Alternatives considered

- Cloud API: rejected — violates ADR-0001 local-first.
- Hybrid: deferred — revisit if device quality proves insufficient.
- English-only `.en` model when English is detected: deferred — complexity
  without product need in v1; revisit when battery/latency matters.