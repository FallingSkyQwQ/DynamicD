package icu.aetherland.dynamicd.agent

import icu.aetherland.dynamicd.agent.llm.LlmProvider
import icu.aetherland.dynamicd.agent.llm.LlmRequest
import icu.aetherland.dynamicd.agent.llm.LlmResponse
import icu.aetherland.dynamicd.agent.loop.AgentLoopConfig
import icu.aetherland.dynamicd.agent.loop.AgentLoopEngine
import icu.aetherland.dynamicd.agent.loop.AgentService
import icu.aetherland.dynamicd.agent.loop.AgentSessionStore
import icu.aetherland.dynamicd.agent.loop.AgentToolExecutor
import icu.aetherland.dynamicd.audit.AuditLogger
import icu.aetherland.dynamicd.integration.InMemoryPlaceholderRegistrar
import icu.aetherland.dynamicd.integration.IntegrationRegistry
import icu.aetherland.dynamicd.module.ModuleManager
import icu.aetherland.dynamicd.ops.SnapshotManager
import icu.aetherland.dynamicd.runtime.ListenerHandle
import icu.aetherland.dynamicd.runtime.RuntimeBridge
import icu.aetherland.dynamicd.runtime.TaskHandle
import icu.aetherland.dynamicd.security.SandboxLevel
import icu.aetherland.dynamicd.security.SecurityPolicy
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class AgentServiceTest {
    @Test
    fun `service persists session logs`() {
        val root = Files.createTempDirectory("dynamicd-agent-service").toFile()
        val manager = ModuleManager(
            modulesRoot = File(root, "modules").apply { mkdirs() },
            runtimeBridge = object : RuntimeBridge {
                override fun bindEvent(moduleId: String, eventPath: String): ListenerHandle? = null
                override fun bindTimer(moduleId: String, timerSpec: String): TaskHandle? = null
            },
            snapshotManager = SnapshotManager(File(root, "snapshots")),
            agentToolchain = AgentToolchain(AuditLogger(File(root, "audit.log"))),
            integrationRegistry = IntegrationRegistry.fromAvailability(mapOf("PlaceholderAPI" to true, "LuckPerms" to true, "Vault" to true)),
            placeholderBridge = InMemoryPlaceholderRegistrar(),
            securityPolicy = SecurityPolicy(),
            defaultSandboxLevel = SandboxLevel.ADMIN,
            logger = {},
        )
        val provider = object : LlmProvider {
            override val name: String = "fixed"
            override fun complete(request: LlmRequest): LlmResponse = LlmResponse("FINAL:ok")
        }
        val service = AgentService(
            engine = AgentLoopEngine(provider, AgentToolExecutor(manager), AgentLoopConfig("fixed", 2)),
            sessionStore = AgentSessionStore(File(root, "workspace/agent")),
        )
        val result = service.runPrompt("tester", AgentToolchain.SYSTEM_PERMISSIONS, "hello")
        assertTrue(result.success)
        val files = File(root, "workspace/agent").listFiles().orEmpty()
        assertTrue(files.isNotEmpty())
        assertTrue(files.first().readText().contains("requestId="))
    }
}
