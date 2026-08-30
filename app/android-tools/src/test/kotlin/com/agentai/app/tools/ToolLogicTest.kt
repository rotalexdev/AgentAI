package com.agentai.app.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED tests for the pure JVM-testable tool logic (spec 0007 T2/T3/T4/T5).
 * Android-boundary tools (Context/Build/AudioManager) are tested on device
 * in Phase 1 Step 9 instrumented tests.
 */
class ToolLogicTest {

    @Test
    fun `device info PII allowlist contains exactly the 4 fields`() {
        assertEquals(
            setOf("model", "manufacturer", "os_release", "api_level"),
            DeviceInfoFields.ALLOWLISTED_FIELDS,
        )
        assertTrue(DeviceInfoFields.isAllowlisted("model"))
        assertFalse(DeviceInfoFields.isAllowlisted("serial_number"))
        assertFalse(DeviceInfoFields.isAllowlisted("imei"))
    }

    @Test
    fun `brightness bounds are 0..100`() {
        assertTrue(BrightnessBounds.isValid(0))
        assertTrue(BrightnessBounds.isValid(100))
        assertFalse(BrightnessBounds.isValid(-1))
        assertFalse(BrightnessBounds.isValid(101))
    }

    @Test
    fun `brightness scale maps 0..100 contract to 0..255 android`() {
        assertEquals(0, BrightnessBounds.toScreenScale(0))
        assertEquals(128, BrightnessBounds.toScreenScale(50))
        assertEquals(255, BrightnessBounds.toScreenScale(100))
        assertEquals(-1, BrightnessBounds.toScreenScale(101))
    }

    @Test
    fun `volume bounds are 0..100`() {
        assertTrue(VolumeBounds.isValid(50))
        assertFalse(VolumeBounds.isValid(150))
    }

    @Test
    fun `open_app allowlist maps canonical keys only`() {
        val allowlist = OpenAppAllowlist(
            mapOf(
                "settings" to "com.android.settings",
                "camera" to "com.android.camera",
            ),
        )
        assertTrue(allowlist.isAllowed("settings"))
        assertFalse(allowlist.isAllowed("com.android.settings")) // raw package is NOT a key
        assertNull(allowlist.packageFor("com.android.settings"))
        assertEquals("com.android.camera", allowlist.packageFor("camera"))
        assertFalse(allowlist.isEmpty)
    }

    @Test
    fun `empty open_app allowlist is FORBIDDEN fallback`() {
        val allowlist = OpenAppAllowlist(emptyMap())
        assertTrue(allowlist.isEmpty)
        assertFalse(allowlist.isAllowed("anything"))
        assertNull(allowlist.packageFor("anything"))
    }
}