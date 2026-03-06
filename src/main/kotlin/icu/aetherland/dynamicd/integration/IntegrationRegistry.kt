package icu.aetherland.dynamicd.integration

import icu.aetherland.dynamicd.config.IntegrationMode
import org.bukkit.plugin.PluginManager

data class IntegrationDiagnostic(
    val integration: String,
    val available: Boolean,
    val enabled: Boolean,
    val message: String,
)

class IntegrationRegistry private constructor(
    private val pluginManager: PluginManager?,
    private val staticAvailability: Map<String, Boolean>?,
) {
    private val diagnostics = mutableMapOf<String, IntegrationDiagnostic>()

    constructor(pluginManager: PluginManager) : this(pluginManager, null)

    companion object {
        fun fromAvailability(values: Map<String, Boolean>): IntegrationRegistry {
            return IntegrationRegistry(null, values)
        }
    }

    fun resolve(name: String, mode: IntegrationMode): IntegrationDiagnostic {
        val available = staticAvailability?.get(name) ?: (pluginManager?.getPlugin(name) != null)
        val enabled = when (mode) {
            IntegrationMode.OFF -> false
            IntegrationMode.AUTO -> available
            IntegrationMode.REQUIRED -> available
        }
        val message = when {
            mode == IntegrationMode.OFF -> "$name disabled by config"
            mode == IntegrationMode.REQUIRED && !available -> "$name required but not installed"
            available -> "$name available"
            else -> "$name not installed, degraded mode"
        }
        return IntegrationDiagnostic(name, available, enabled, message).also {
            diagnostics[name] = it
        }
    }

    fun diagnostics(): List<IntegrationDiagnostic> = diagnostics.values.sortedBy { it.integration }
}
