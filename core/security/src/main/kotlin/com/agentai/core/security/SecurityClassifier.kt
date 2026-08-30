package com.agentai.core.security

/**
 * Declared side-effect profile of a tool, used by the rule-based classifier.
 */
enum class SideEffect {
    /** Read-only; no observable system mutation. */
    NONE,

    /** Changes a system setting, sends data, or otherwise leaves the app. */
    MUTATES_SYSTEM,
}

/**
 * Declared PII exposure of a tool.
 */
enum class PiiExposure {
    NONE,
    EXPOSES_PII,
}

/**
 * Rule-based classifier (spec 0006 S2, design D3). Deterministic:
 * the same inputs always yield the same classification.
 */
object SecurityClassifier {

    fun classify(sideEffect: SideEffect, piiExposure: PiiExposure): SecurityClassification =
        when {
            sideEffect == SideEffect.NONE && piiExposure == PiiExposure.NONE -> SecurityClassification.SAFE
            else -> SecurityClassification.CONFIRMATION_REQUIRED
        }

    /**
     * FORBIDDEN is an explicit capability denial: any tool declaring one of
     * these capabilities can never be registered or executed (spec 0006 S4).
     */
    fun isForbiddenCapability(capability: String): Boolean =
        capability in FORBIDDEN_CAPABILITIES

    private val FORBIDDEN_CAPABILITIES = setOf(
        "shell",
        "code_execution",
        "credential_access",
        "filesystem",
        "reflection",
        "arbitrary_intent",
    )
}