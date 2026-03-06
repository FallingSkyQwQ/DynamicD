package icu.aetherland.dynamicd.integration

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap

data class PlaceholderSpec(
    val namespace: String,
    val key: String,
    val valueProvider: (OfflinePlayer?) -> String,
)

class PlaceholderBridge(private val plugin: JavaPlugin) : PlaceholderRegistrar {
    private val providers = ConcurrentHashMap<String, (OfflinePlayer?) -> String>()
    private var expansion: DynamicDPlaceholderExpansion? = null

    fun installIfAvailable(enabled: Boolean): IntegrationDiagnostic {
        val available = plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null
        if (!enabled) {
            return IntegrationDiagnostic("PlaceholderAPI", available, false, "PlaceholderAPI disabled by config")
        }
        if (!available) {
            return IntegrationDiagnostic("PlaceholderAPI", false, false, "PlaceholderAPI missing, degraded mode")
        }
        if (expansion == null) {
            expansion = DynamicDPlaceholderExpansion(plugin, providers).also { it.register() }
        }
        return IntegrationDiagnostic("PlaceholderAPI", true, true, "PlaceholderAPI bridge installed")
    }

    override fun register(spec: PlaceholderSpec) {
        providers["${spec.namespace}_${spec.key}".lowercase()] = spec.valueProvider
    }

    override fun listRegisteredKeys(): List<String> = providers.keys.sorted()
}

class InMemoryPlaceholderRegistrar : PlaceholderRegistrar {
    private val names = mutableListOf<String>()
    override fun register(spec: PlaceholderSpec) {
        names += "${spec.namespace}_${spec.key}".lowercase()
    }

    override fun listRegisteredKeys(): List<String> = names.sorted()
}

private class DynamicDPlaceholderExpansion(
    private val plugin: JavaPlugin,
    private val providers: Map<String, (OfflinePlayer?) -> String>,
) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "dd"

    override fun getAuthor(): String = "DynamicD"

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String {
        val normalized = "dd_${params.lowercase()}"
        return providers[normalized]?.invoke(player) ?: ""
    }
}
