package icu.aetherland.dynamicd.util

import org.bukkit.plugin.PluginManager

data class IntegrationStatus(
    val name: String,
    val available: Boolean,
)

class IntegrationDetector(private val pluginManager: PluginManager) {
    fun detect(names: List<String>): List<IntegrationStatus> {
        return names.map { name ->
            IntegrationStatus(name = name, available = pluginManager.getPlugin(name) != null)
        }
    }
}
