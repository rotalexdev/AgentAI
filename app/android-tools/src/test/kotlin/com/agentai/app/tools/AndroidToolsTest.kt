package com.agentai.app.tools

import android.app.Application
import android.app.AppOpsManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.agentai.core.contract.ToolError
import com.agentai.core.contract.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * RED tests for spec 0007 — tools execute against Robolectric's shadow
 * Android framework. Runs in the JVM (Phase 1 Step 0, ADR-0005 flip).
 */
@RunWith(RobolectricTestRunner::class)
class AndroidToolsTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `get_current_time succeeds with iso8601`() = runTest {
        val result = GetCurrentTimeTool().run(buildJsonObject {})
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data
        assertTrue(data["iso8601"] != null)
    }

    @Test
    fun `get_battery_status succeeds with percent and charging`() = runTest {
        val result = GetBatteryStatusTool(context).run(buildJsonObject {})
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data
        assertTrue(data["percent"] != null)
        assertTrue(data["charging"] != null)
    }

    @Test
    fun `get_device_info returns allowlisted fields only`() = runTest {
        val result = GetDeviceInfoTool().run(buildJsonObject {})
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data
        // spec 0007 T2 / design D2: ONLY model, manufacturer, os_release, api_level.
        assertEquals(setOf("model", "manufacturer", "os_release", "api_level"), data.keys)
    }

    @Test
    fun `set_brightness succeeds when WRITE_SETTINGS is granted`() = runTest {
        // Deterministic: fix the AppOps mode instead of trusting the shadow default.
        grantWriteSettings()
        val result = SetBrightnessTool(context).run(buildJsonObject { put("value", 50) })
        assertTrue("expected Success, got $result", result is ToolResult.Success)
        assertEquals(50, (result as ToolResult.Success).data["value"]?.toString()?.toInt())
    }

    @Test
    fun `set_brightness fails PERMISSION_DENIED when WRITE_SETTINGS denied`() = runTest {
        revokeWriteSettings()
        val result = SetBrightnessTool(context).run(buildJsonObject { put("value", 50) })
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.PERMISSION_DENIED, (result as ToolResult.Failure).error)
    }

    private fun grantWriteSettings() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOps).setMode(
            AppOpsManager.OP_WRITE_SETTINGS,
            android.os.Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED,
        )
    }

    private fun revokeWriteSettings() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOps).setMode(
            AppOpsManager.OP_WRITE_SETTINGS,
            android.os.Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ERRORED,
        )
    }

    /** Makes getLaunchIntentForPackage resolve for [packageName] (deterministic). */
    private fun installLaunchablePackage(packageName: String) {
        val component = android.content.ComponentName(packageName, "$packageName.MainActivity")
        shadowOf(context.packageManager).addActivityIfNotPresent(component)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            component,
            android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            },
        )
    }

    @Test
    fun `set_brightness rejects out-of-range value - no coercion`() = runTest {
        val result = SetBrightnessTool(context).run(buildJsonObject { put("value", 101) })
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.MALFORMED_ARGUMENTS, (result as ToolResult.Failure).error)
    }

    @Test
    fun `set_volume succeeds when AudioManager is available`() = runTest {
        val result = SetVolumeTool(context).run(buildJsonObject { put("value", 30) })
        assertTrue("expected Success, got $result", result is ToolResult.Success)
        assertEquals(30, (result as ToolResult.Success).data["value"]?.toString()?.toInt())
    }

    @Test
    fun `set_volume rejects non-numeric value`() = runTest {
        val result = SetVolumeTool(context).run(buildJsonObject { put("value", "loud") })
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.MALFORMED_ARGUMENTS, (result as ToolResult.Failure).error)
    }

    @Test
    fun `open_app opens allowlisted app`() = runTest {
        installLaunchablePackage("com.android.settings")
        val allowlist = OpenAppAllowlist(mapOf("settings" to "com.android.settings"))
        val result = OpenAppTool(context, allowlist).run(buildJsonObject { put("app", "settings") })
        assertTrue("expected Success, got $result", result is ToolResult.Success)
        // Robolectric captured the started activity.
        val started = shadowOf(context).nextStartedActivity
        assertEquals("com.android.settings", started?.component?.packageName)
    }

    @Test
    fun `open_app rejects unknown key`() = runTest {
        val allowlist = OpenAppAllowlist(mapOf("settings" to "com.android.settings"))
        val result = OpenAppTool(context, allowlist).run(buildJsonObject { put("app", "camera") })
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.MALFORMED_ARGUMENTS, (result as ToolResult.Failure).error)
    }

    @Test
    fun `open_app with empty allowlist is FORBIDDEN`() = runTest {
        // spec 0007 T5 / design D1: empty allowlist ⇒ PERMISSION_DENIED.
        val result = OpenAppTool(context, OpenAppAllowlist(emptyMap())).run(buildJsonObject { put("app", "settings") })
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.PERMISSION_DENIED, (result as ToolResult.Failure).error)
    }

    @Test
    fun `open_app rejects model-supplied extras`() = runTest {
        val allowlist = OpenAppAllowlist(mapOf("settings" to "com.android.settings"))
        val args = buildJsonObject {
            put("app", "settings")
            put("extra_intent", "evil")
        }
        val result = OpenAppTool(context, allowlist).run(args)
        assertTrue(result is ToolResult.Failure)
        assertEquals(ToolError.MALFORMED_ARGUMENTS, (result as ToolResult.Failure).error)
    }
}