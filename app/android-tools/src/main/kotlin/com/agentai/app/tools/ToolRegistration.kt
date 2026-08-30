package com.agentai.app.tools

import com.agentai.core.registry.Tool
import com.agentai.core.registry.ToolRegistry

/**
 * Registers the initial tool set at app composition (spec 0007, design module map:
 * `android-tools → tool-contract` only). The registry is assembled once; the
 * model can only see and call these registered tools.
 */
fun ToolRegistry.registerInitialTools(vararg tools: Tool) {
    tools.forEach { register(it) }
}