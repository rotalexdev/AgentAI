package com.agentai.app

import android.app.Application
import com.agentai.app.runtime.AgentRuntime
import com.agentai.app.tools.GetBatteryStatusTool
import com.agentai.app.tools.GetCurrentTimeTool
import com.agentai.app.tools.GetDeviceInfoTool
import com.agentai.app.tools.OpenAppAllowlist
import com.agentai.app.tools.OpenAppTool
import com.agentai.app.tools.SetBrightnessTool
import com.agentai.app.tools.SetVolumeTool
import com.agentai.app.tools.registerInitialTools
import com.agentai.app.ui.UiConfirmationGate
import com.agentai.app.whisper.AndroidAudioRecorder
import com.agentai.app.whisper.ModelRepository
import com.agentai.app.whisper.VoiceInputController
import com.agentai.app.whisper.WhisperSpeechToText
import com.agentai.core.model.MockModel
import com.agentai.core.registry.InMemoryToolRegistry
import com.agentai.core.security.AndroidPermission
import com.agentai.core.security.DefaultToolPolicy
import com.agentai.core.security.PermissionEvaluator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App composition root (design module map). Assembles the object graph MANUALLY
 * — no DI framework (AGENTS.md: avoid Hilt/Koin unless necessary). UI, tools,
 * model and runtime remain separate responsibilities.
 */
class AgentApp : Application() {

    lateinit var runtime: AgentRuntime
        private set

    lateinit var confirmationGate: UiConfirmationGate
        private set

    lateinit var voiceController: VoiceInputController
        private set

    override fun onCreate() {
        super.onCreate()

        // Tools: the model can only ever see these registered definitions (spec 0007).
        val registry = InMemoryToolRegistry().apply {
            registerInitialTools(
                GetCurrentTimeTool(),
                GetBatteryStatusTool(applicationContext),
                GetDeviceInfoTool(),
                SetBrightnessTool(applicationContext),
                SetVolumeTool(applicationContext),
                // Non-empty allowlist: only canonical keys in this map may be opened.
                OpenAppTool(
                    context = applicationContext,
                    allowlist = OpenAppAllowlist(mapOf("settings" to "com.android.settings")),
                ),
            )
        }

        // Security: deterministic policy with permission preflight (spec 0006).
        val permissionEvaluator = PermissionEvaluator { permission ->
            when (permission) {
                AndroidPermission.WRITE_SETTINGS -> android.provider.Settings.System.canWrite(applicationContext)
                AndroidPermission.MODIFY_AUDIO_SETTINGS -> true // normal permission, declared in manifest
            }
        }
        val policy = DefaultToolPolicy(
            permissionEvaluator = permissionEvaluator,
            requiredPermissions = mapOf(
                "set_brightness" to AndroidPermission.WRITE_SETTINGS,
                "set_volume" to AndroidPermission.MODIFY_AUDIO_SETTINGS,
            ),
        )

        // UI confirmation gate; the runtime blocks on it (fail-closed if absent).
        confirmationGate = UiConfirmationGate()

        runtime = AgentRuntime(
            model = MockModel(), // placeholder until a real on-device model is wired (spec 0003)
            registry = registry,
            policy = policy,
            confirmationGate = confirmationGate,
        )

        // Voice input (spec 0010): on-device Whisper, model downloaded once and
        // SHA-256 verified, cached under filesDir/models.
        voiceController = VoiceInputController(
            recorder = AndroidAudioRecorder(),
            speechToText = WhisperSpeechToText(
                modelRepository = ModelRepository(File(filesDir, "models")),
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}