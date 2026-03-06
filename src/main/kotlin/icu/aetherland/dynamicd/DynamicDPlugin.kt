package icu.aetherland.dynamicd

import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.audit.AuditLogger
import icu.aetherland.dynamicd.command.DynamicDCommand
import icu.aetherland.dynamicd.module.ModuleManager
import icu.aetherland.dynamicd.ops.SnapshotManager
import icu.aetherland.dynamicd.runtime.EventBridge
import icu.aetherland.dynamicd.security.DangerousActionGuard
import icu.aetherland.dynamicd.util.PaperVersionChecker
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class DynamicDPlugin : JavaPlugin() {
    private lateinit var moduleManager: ModuleManager

    override fun onLoad() {
        val minecraftVersion = Bukkit.getMinecraftVersion()
        if (!PaperVersionChecker.isSupportedVersion(minecraftVersion)) {
            logger.severe("Unsupported Paper version: $minecraftVersion, requires >= 1.21.11")
            server.pluginManager.disablePlugin(this)
            return
        }
        logger.info("DynamicD onLoad initialization complete")
    }

    override fun onEnable() {
        val modulesRoot = File(dataFolder, "modules")
        val logsDir = File(dataFolder, "logs")
        val snapshotsDir = File(dataFolder, "data/snapshots")
        modulesRoot.mkdirs()
        logsDir.mkdirs()
        snapshotsDir.mkdirs()

        val auditLogger = AuditLogger(File(logsDir, "agent-audit.log"))
        val agentToolchain = AgentToolchain(auditLogger)
        moduleManager = ModuleManager(
            plugin = this,
            modulesRoot = modulesRoot,
            eventBridge = EventBridge(this),
            snapshotManager = SnapshotManager(snapshotsDir),
            agentToolchain = agentToolchain,
        )
        moduleManager.restoreModules()

        val command = DynamicDCommand(moduleManager, DangerousActionGuard())
        getCommand("dd")?.setExecutor(command)
        getCommand("dd")?.tabCompleter = command

        logger.info("DynamicD enabled")
    }

    override fun onDisable() {
        if (this::moduleManager.isInitialized) {
            moduleManager.shutdown()
        }
        logger.info("DynamicD disabled")
    }
}
