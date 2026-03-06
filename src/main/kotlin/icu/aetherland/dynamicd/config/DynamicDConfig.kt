package icu.aetherland.dynamicd.config

import icu.aetherland.dynamicd.security.SandboxLevel
import org.bukkit.configuration.file.FileConfiguration

enum class IntegrationMode {
    AUTO,
    OFF,
    REQUIRED,
}

data class RuntimeConfig(
    val autoLoadModules: Boolean,
    val compileOnStartup: Boolean,
)

data class AgentConfig(
    val enabled: Boolean,
    val provider: String,
    val endpoint: String,
    val model: String,
    val apiKey: String,
    val maxIterations: Int,
    val requireConfirmForDangerous: Boolean,
)

data class ReplConfig(
    val enabled: Boolean,
    val inGame: Boolean,
    val timeoutSeconds: Int,
)

data class IntegrationConfig(
    val papi: IntegrationMode,
    val luckPerms: IntegrationMode,
    val vault: IntegrationMode,
)

data class SecurityConfig(
    val defaultSandboxLevel: SandboxLevel,
)

data class DynamicDConfig(
    val runtime: RuntimeConfig,
    val agent: AgentConfig,
    val repl: ReplConfig,
    val integrations: IntegrationConfig,
    val security: SecurityConfig,
) {
    companion object {
        fun load(config: FileConfiguration): DynamicDConfig {
            return DynamicDConfig(
                runtime = RuntimeConfig(
                    autoLoadModules = config.getBoolean("runtime.auto-load-modules", true),
                    compileOnStartup = config.getBoolean("runtime.compile-on-startup", true),
                ),
                agent = AgentConfig(
                    enabled = config.getBoolean("agent.enabled", true),
                    provider = config.getString("agent.provider", "openai-compatible").orEmpty(),
                    endpoint = config.getString("agent.endpoint", "").orEmpty(),
                    model = config.getString("agent.model", "").orEmpty(),
                    apiKey = config.getString("agent.api-key", "").orEmpty(),
                    maxIterations = config.getInt("agent.max-iterations", 8),
                    requireConfirmForDangerous = config.getBoolean("agent.require-confirm-for-dangerous", true),
                ),
                repl = ReplConfig(
                    enabled = config.getBoolean("repl.enabled", true),
                    inGame = config.getBoolean("repl.in-game", true),
                    timeoutSeconds = config.getInt("repl.timeout-seconds", 15),
                ),
                integrations = IntegrationConfig(
                    papi = parseMode(config.getString("integrations.papi", "auto").orEmpty()),
                    luckPerms = parseMode(config.getString("integrations.luckperms", "auto").orEmpty()),
                    vault = parseMode(config.getString("integrations.vault", "auto").orEmpty()),
                ),
                security = SecurityConfig(
                    defaultSandboxLevel = parseSandbox(
                        config.getString("security.default-sandbox-level", "TRUSTED").orEmpty(),
                    ),
                ),
            )
        }

        private fun parseMode(value: String): IntegrationMode {
            return when (value.trim().uppercase()) {
                "OFF" -> IntegrationMode.OFF
                "REQUIRED" -> IntegrationMode.REQUIRED
                else -> IntegrationMode.AUTO
            }
        }

        private fun parseSandbox(value: String): SandboxLevel {
            return try {
                SandboxLevel.valueOf(value.trim().uppercase())
            } catch (_: IllegalArgumentException) {
                SandboxLevel.TRUSTED
            }
        }
    }
}
