package com.agentai.core.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED tests for spec 0006 S2/S4: classification is deterministic rule-based,
 * and FORBIDDEN capabilities can never be registered or executed.
 */
class SecurityClassifierTest {

    @Test
    fun `no side effect and no pii is SAFE`() {
        assertEquals(
            SecurityClassification.SAFE,
            SecurityClassifier.classify(SideEffect.NONE, PiiExposure.NONE),
        )
    }

    @Test
    fun `side effect forces CONFIRMATION_REQUIRED`() {
        assertEquals(
            SecurityClassification.CONFIRMATION_REQUIRED,
            SecurityClassifier.classify(SideEffect.MUTATES_SYSTEM, PiiExposure.NONE),
        )
    }

    @Test
    fun `pii exposure forces CONFIRMATION_REQUIRED`() {
        assertEquals(
            SecurityClassification.CONFIRMATION_REQUIRED,
            SecurityClassifier.classify(SideEffect.NONE, PiiExposure.EXPOSES_PII),
        )
    }

    @Test
    fun `classification is deterministic`() {
        val a = SecurityClassifier.classify(SideEffect.MUTATES_SYSTEM, PiiExposure.NONE)
        val b = SecurityClassifier.classify(SideEffect.MUTATES_SYSTEM, PiiExposure.NONE)
        assertEquals(a, b)
    }

    @Test
    fun `forbidden capabilities are denied`() {
        for (capability in listOf("shell", "code_execution", "credential_access", "filesystem", "reflection", "arbitrary_intent")) {
            assertTrue(SecurityClassifier.isForbiddenCapability(capability), "$capability must be forbidden")
        }
    }

    @Test
    fun `safe tool names are not forbidden capabilities`() {
        assertFalse(SecurityClassifier.isForbiddenCapability("get_battery_status"))
        assertFalse(SecurityClassifier.isForbiddenCapability("set_brightness"))
        assertFalse(SecurityClassifier.isForbiddenCapability("open_app"))
    }
}