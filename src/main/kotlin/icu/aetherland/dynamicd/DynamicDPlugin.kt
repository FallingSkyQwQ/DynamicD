package icu.aetherland.dynamicd

import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.audit.AuditLogger
import icu.aetherland.dynamicd.command.DynamicDCommand
import icu.aetherland.dynamicd.module.ModuleManager
import icu.aetherland.dynamicd.module.ModuleStateStore
import icu.aetherland.dynamicd.ops.SnapshotManager
import icu.aetherland.dynamicd.runtime.BukkitRuntimeBridge
import icu.aetherland.dynamicd.runtime.EventBridge
import icu.aetherland.dynamicd.security.DangerousActionGuard
import icu.aetherland.dynamicd.util.IntegrationDetector
import icu.aetherland.dynamicd.util.PaperVersionChecker
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class DynamicDPlugin : JavaPlugin() {
    private lateinit var moduleManager: ModuleManager
    private lateinit var moduleStateStore: ModuleStateStore

    override fun onLoad() {
        val minecraftVersion = Bukkit.getMinecraftVersion()
        if (!PaperVersionChecker.isSupportedVersion(minecraftVersion)) {
            logger.severe("event=startup phase=version-check status=failed version=$minecraftVersion min=1.21.11")
            server.pluginManager.disablePlugin(this)
            return
        }

        val initStart = System.nanoTime()
        val compilerStart = System.nanoTime()
        // compiler bootstrap placeholder
        logger.info("event=startup phase=compiler-init status=ok durationMs=${elapsedMs(compilerStart)}")

        val cacheStart = System.nanoTime()
        // cache bootstrap placeholder
        logger.info("event=startup phase=cache-init status=ok durationMs=${elapsedMs(cacheStart)}")

        val integrationStart = System.nanoTime()
        val detector = IntegrationDetector(server.pluginManager)
        val integrations = detector.detect(listOf("PlaceholderAPI", "LuckPerms", "Vault"))
        integrations.forEach { status ->
            logger.info("event=startup phase=integration-detect plugin=${status.name} available=${status.available}")
        }
        logger.info("event=startup phase=integration-detect status=ok durationMs=${elapsedMs(integrationStart)}")
        logger.info("event=startup phase=onLoad status=ok durationMs=${elapsedMs(initStart)}")
    }

    override fun onEnable() {
        val modulesRoot = File(dataFolder, "modules")
        val logsDir = File(dataFolder, "logs")
        val snapshotsDir = File(dataFolder, "data/snapshots")
        val stateFile = File(dataFolder, "data/enabled-modules.txt")
        modulesRoot.mkdirs()
        logsDir.mkdirs()
        snapshotsDir.mkdirs()

        val auditLogger = AuditLogger(File(logsDir, "agent-audit.log"))
        val agentToolchain = AgentToolchain(auditLogger)
        moduleStateStore = ModuleStateStore(stateFile)

        moduleManager = ModuleManager(
            modulesRoot = modulesRoot,
            runtimeBridge = BukkitRuntimeBridge(this, EventBridge(this)),
            snapshotManager = SnapshotManager(snapshotsDir),
            agentToolchain = agentToolchain,
            logger = { msg -> logger.warning(msg) },
        )

        val enabledBeforeRestart = moduleStateStore.loadEnabledModules()
        moduleManager.restoreModules(enabledBeforeRestart)

        val command = DynamicDCommand(moduleManager, DangerousActionGuard())
        getCommand("dd")?.setExecutor(command)
        getCommand("dd")?.tabCompleter = command

        logger.info("DynamicD enabled")
    }

    override fun onDisable() {
        if (this::moduleManager.isInitialized) {
            moduleStateStore.saveEnabledModules(moduleManager.enabledModuleIds())
            moduleManager.shutdown()
        }
        logger.info("DynamicD disabled")
    }

    private fun elapsedMs(start: Long): Long {
        return (System.nanoTime() - start) / 1_000_000
    }
}
