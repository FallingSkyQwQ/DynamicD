package icu.aetherland.dynamicd

import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.agent.llm.OpenAiCompatibleProvider
import icu.aetherland.dynamicd.agent.loop.AgentLoopConfig
import icu.aetherland.dynamicd.agent.loop.AgentLoopEngine
import icu.aetherland.dynamicd.agent.loop.AgentSessionStore
import icu.aetherland.dynamicd.agent.loop.AgentService
import icu.aetherland.dynamicd.agent.loop.AgentToolExecutor
import icu.aetherland.dynamicd.audit.AuditLogger
import icu.aetherland.dynamicd.command.DynamicDCommand
import icu.aetherland.dynamicd.config.DynamicDConfig
import icu.aetherland.dynamicd.integration.IntegrationRegistry
import icu.aetherland.dynamicd.integration.LuckPermsBridge
import icu.aetherland.dynamicd.integration.PlaceholderBridge
import icu.aetherland.dynamicd.module.ModuleManager
import icu.aetherland.dynamicd.module.ModuleStateStore
import icu.aetherland.dynamicd.ops.LogChannel
import icu.aetherland.dynamicd.ops.SnapshotManager
import icu.aetherland.dynamicd.ops.StructuredLogger
import icu.aetherland.dynamicd.persist.PersistStore
import icu.aetherland.dynamicd.repl.ReplEvaluator
import icu.aetherland.dynamicd.repl.ReplSessionManager
import icu.aetherland.dynamicd.security.CircuitBreaker
import icu.aetherland.dynamicd.runtime.BukkitRuntimeBridge
import icu.aetherland.dynamicd.runtime.EventBridge
import icu.aetherland.dynamicd.security.DangerousActionGuard
import icu.aetherland.dynamicd.security.ConfirmationManager
import icu.aetherland.dynamicd.security.ResourceBudget
import icu.aetherland.dynamicd.security.ResourceLimiter
import icu.aetherland.dynamicd.security.SecurityPolicy
import icu.aetherland.dynamicd.util.PaperVersionChecker
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class DynamicDPlugin : JavaPlugin() {
    private lateinit var moduleManager: ModuleManager
    private lateinit var moduleStateStore: ModuleStateStore
    private lateinit var dynamicDConfig: DynamicDConfig

    override fun onLoad() {
        val minecraftVersion = Bukkit.getMinecraftVersion()
        if (!PaperVersionChecker.isSupportedVersion(minecraftVersion)) {
            logger.severe("event=startup phase=version-check status=failed version=$minecraftVersion min=1.21.11")
            server.pluginManager.disablePlugin(this)
            return
        }
        saveDefaultConfig()
        dynamicDConfig = DynamicDConfig.load(config)

        val initStart = System.nanoTime()
        logger.info("event=startup phase=compiler-init status=ok durationMs=${elapsedMs(initStart)}")
        logger.info("event=startup phase=cache-init status=ok durationMs=${elapsedMs(initStart)}")
        logger.info("event=startup phase=onLoad status=ok durationMs=${elapsedMs(initStart)}")
    }

    override fun onEnable() {
        val modulesRoot = File(dataFolder, "modules")
        val logsDir = File(dataFolder, "logs")
        val snapshotsDir = File(dataFolder, "data/snapshots")
        val stateFile = File(dataFolder, "data/enabled-modules.txt")
        val persistDb = File(dataFolder, "data/persist.db")
        modulesRoot.mkdirs()
        logsDir.mkdirs()
        snapshotsDir.mkdirs()
        File(dataFolder, "workspace/agent").mkdirs()

        val auditLogger = AuditLogger(File(logsDir, "agent-audit.log"))
        val structuredLogger = StructuredLogger(logsDir)
        val agentToolchain = AgentToolchain(auditLogger)
        moduleStateStore = ModuleStateStore(stateFile)
        val persistStore = PersistStore(persistDb)
        val resourceLimiter = ResourceLimiter(
            ResourceBudget(
                cpuSteps = dynamicDConfig.security.cpuSteps,
                maxTasks = dynamicDConfig.security.maxTasks,
                ioQuota = dynamicDConfig.security.ioQuota,
            ),
        )

        val integrationRegistry = IntegrationRegistry(server.pluginManager)
        val placeholderBridge = PlaceholderBridge(this)
        val luckPermsBridge = LuckPermsBridge()

        val papiDiag = integrationRegistry.resolve("PlaceholderAPI", dynamicDConfig.integrations.papi)
        logger.info("integration=${papiDiag.integration} enabled=${papiDiag.enabled} message=${papiDiag.message}")
        val lpDiag = integrationRegistry.resolve("LuckPerms", dynamicDConfig.integrations.luckPerms)
        logger.info("integration=${lpDiag.integration} enabled=${lpDiag.enabled} message=${lpDiag.message}")
        val vaultDiag = integrationRegistry.resolve("Vault", dynamicDConfig.integrations.vault)
        logger.info("integration=${vaultDiag.integration} enabled=${vaultDiag.enabled} message=${vaultDiag.message}")

        val papiInstall = placeholderBridge.installIfAvailable(papiDiag.enabled)
        logger.info("integration=${papiInstall.integration} enabled=${papiInstall.enabled} message=${papiInstall.message}")
        val lpInstall = luckPermsBridge.installIfAvailable(lpDiag.enabled)
        logger.info("integration=${lpInstall.integration} enabled=${lpInstall.enabled} message=${lpInstall.message}")

        moduleManager = ModuleManager(
            modulesRoot = modulesRoot,
            runtimeBridge = BukkitRuntimeBridge(this, EventBridge(this)),
            snapshotManager = SnapshotManager(snapshotsDir),
            agentToolchain = agentToolchain,
            integrationRegistry = integrationRegistry,
            placeholderBridge = placeholderBridge,
            securityPolicy = SecurityPolicy(),
            defaultSandboxLevel = dynamicDConfig.security.defaultSandboxLevel,
            logger = { msg ->
                logger.warning(msg)
                structuredLogger.log(LogChannel.RUNTIME, mapOf("message" to msg))
            },
            persistStore = persistStore,
            resourceLimiter = resourceLimiter,
            circuitBreaker = CircuitBreaker(),
        )

        if (dynamicDConfig.runtime.autoLoadModules) {
            val enabledBeforeRestart = moduleStateStore.loadEnabledModules()
            moduleManager.restoreModules(enabledBeforeRestart, dynamicDConfig.runtime.compileOnStartup)
        }

        val provider = OpenAiCompatibleProvider(
            endpoint = dynamicDConfig.agent.endpoint,
            apiKey = dynamicDConfig.agent.apiKey,
        )
        val toolExecutor = AgentToolExecutor(moduleManager)
        val loopEngine = AgentLoopEngine(
            provider = provider,
            toolExecutor = toolExecutor,
            config = AgentLoopConfig(
                model = dynamicDConfig.agent.model.ifBlank { "gpt-4o-mini" },
                maxIterations = dynamicDConfig.agent.maxIterations,
            ),
        )
        val agentService = AgentService(
            engine = loopEngine,
            sessionStore = AgentSessionStore(File(dataFolder, "workspace/agent")),
        )
        val replSessionManager = ReplSessionManager(dynamicDConfig.repl.timeoutSeconds)
        val replEvaluator = ReplEvaluator(
            securityPolicy = SecurityPolicy(),
            sandboxLevel = dynamicDConfig.security.defaultSandboxLevel,
            timeoutSeconds = dynamicDConfig.repl.timeoutSeconds,
        )

        val command = DynamicDCommand(
            moduleManager = moduleManager,
            dangerousActionGuard = DangerousActionGuard(),
            confirmationManager = ConfirmationManager(60),
            agentService = agentService,
            replSessionManager = replSessionManager,
            replEvaluator = replEvaluator,
            placeholderBridge = placeholderBridge,
            luckPermsBridge = luckPermsBridge,
        )
        getCommand("dd")?.setExecutor(command)
        getCommand("dd")?.tabCompleter = command

        logger.info("DynamicD enabled")
        structuredLogger.log(
            LogChannel.RUNTIME,
            mapOf("event" to "plugin_enabled", "autoLoadModules" to dynamicDConfig.runtime.autoLoadModules.toString()),
        )
    }

    override fun onDisable() {
        if (this::moduleManager.isInitialized) {
            moduleStateStore.saveEnabledModules(moduleManager.enabledModuleIds())
            moduleManager.shutdown()
        }
        logger.info("DynamicD disabled")
        StructuredLogger(File(dataFolder, "logs")).log(LogChannel.RUNTIME, mapOf("event" to "plugin_disabled"))
    }

    private fun elapsedMs(start: Long): Long {
        return (System.nanoTime() - start) / 1_000_000
    }
}
