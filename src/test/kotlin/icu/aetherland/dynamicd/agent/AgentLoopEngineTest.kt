package icu.aetherland.dynamicd.agent

import icu.aetherland.dynamicd.agent.llm.LlmProvider
import icu.aetherland.dynamicd.agent.llm.LlmRequest
import icu.aetherland.dynamicd.agent.llm.LlmResponse
import icu.aetherland.dynamicd.agent.loop.AgentLoopConfig
import icu.aetherland.dynamicd.agent.loop.AgentLoopEngine
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentLoopEngineTest {
    @Test
    fun `loop executes tool then finalizes`() {
        val root = Files.createTempDirectory("dynamicd-agent-loop").toFile()
        val moduleRoot = File(root, "modules").apply { mkdirs() }
        val manager = ModuleManager(
            modulesRoot = moduleRoot,
            runtimeBridge = object : RuntimeBridge {
                override fun bindEvent(moduleId: String, eventPath: String): ListenerHandle? = null
                override fun bindTimer(moduleId: String, timerSpec: String): TaskHandle? = null
            },
            snapshotManager = SnapshotManager(File(root, "snapshots")),
            agentToolchain = AgentToolchain(AuditLogger(File(root, "audit.log"))),
            integrationRegistry = IntegrationRegistry.fromAvailability(
                mapOf("PlaceholderAPI" to true, "LuckPerms" to true, "Vault" to true),
            ),
            placeholderBridge = InMemoryPlaceholderRegistrar(),
            securityPolicy = SecurityPolicy(),
            defaultSandboxLevel = SandboxLevel.ADMIN,
            logger = {},
        )

        val provider = object : LlmProvider {
            override val name: String = "fake"
            private var count = 0
            override fun complete(request: LlmRequest): LlmResponse {
                count++
                return if (count == 1) {
                    LlmResponse("TOOL:create welcome module \"dynamicd:welcome\"")
                } else {
                    LlmResponse("FINAL:created module and completed")
                }
            }
        }
        val engine = AgentLoopEngine(
            provider = provider,
            toolExecutor = AgentToolExecutor(manager),
            config = AgentLoopConfig(model = "fake", maxIterations = 4),
        )
        val result = engine.run("tester", AgentToolchain.SYSTEM_PERMISSIONS, "create welcome module")
        assertTrue(result.success)
        assertTrue(result.summary.contains("completed"))
    }

    @Test
    fun `loop supports json tool call format`() {
        val root = Files.createTempDirectory("dynamicd-agent-loop-json").toFile()
        val moduleRoot = File(root, "modules").apply { mkdirs() }
        val manager = ModuleManager(
            modulesRoot = moduleRoot,
            runtimeBridge = object : RuntimeBridge {
                override fun bindEvent(moduleId: String, eventPath: String): ListenerHandle? = null
                override fun bindTimer(moduleId: String, timerSpec: String): TaskHandle? = null
            },
            snapshotManager = SnapshotManager(File(root, "snapshots")),
            agentToolchain = AgentToolchain(AuditLogger(File(root, "audit.log"))),
            integrationRegistry = IntegrationRegistry.fromAvailability(
                mapOf("PlaceholderAPI" to true, "LuckPerms" to true, "Vault" to true),
            ),
            placeholderBridge = InMemoryPlaceholderRegistrar(),
            securityPolicy = SecurityPolicy(),
            defaultSandboxLevel = SandboxLevel.ADMIN,
            logger = {},
        )

        val provider = object : LlmProvider {
            override val name: String = "fake-json"
            private var count = 0
            override fun complete(request: LlmRequest): LlmResponse {
                count++
                return if (count == 1) {
                    LlmResponse("""{"tool":"create","args":"world module \"dynamicd:world\""}""")
                } else {
                    LlmResponse("FINAL:json tool path complete")
                }
            }
        }
        val engine = AgentLoopEngine(
            provider = provider,
            toolExecutor = AgentToolExecutor(manager),
            config = AgentLoopConfig(model = "fake", maxIterations = 4),
        )
        val result = engine.run("tester", AgentToolchain.SYSTEM_PERMISSIONS, "create module via json")
        assertTrue(result.success)
        assertTrue(result.summary.contains("complete"))
    }

    @Test
    fun `loop captures plan reflect and stalls on no progress`() {
        val root = Files.createTempDirectory("dynamicd-agent-loop-stall").toFile()
        val moduleRoot = File(root, "modules").apply { mkdirs() }
        val manager = ModuleManager(
            modulesRoot = moduleRoot,
            runtimeBridge = object : RuntimeBridge {
                override fun bindEvent(moduleId: String, eventPath: String): ListenerHandle? = null
                override fun bindTimer(moduleId: String, timerSpec: String): TaskHandle? = null
            },
            snapshotManager = SnapshotManager(File(root, "snapshots")),
            agentToolchain = AgentToolchain(AuditLogger(File(root, "audit.log"))),
            integrationRegistry = IntegrationRegistry.fromAvailability(
                mapOf("PlaceholderAPI" to true, "LuckPerms" to true, "Vault" to true),
            ),
            placeholderBridge = InMemoryPlaceholderRegistrar(),
            securityPolicy = SecurityPolicy(),
            defaultSandboxLevel = SandboxLevel.ADMIN,
            logger = {},
        )

        val provider = object : LlmProvider {
            override val name: String = "stall"
            override fun complete(request: LlmRequest): LlmResponse {
                return LlmResponse("PLAN:inspect module structure\nREFLECT:waiting next step")
            }
        }
        val engine = AgentLoopEngine(
            provider = provider,
            toolExecutor = AgentToolExecutor(manager),
            config = AgentLoopConfig(model = "stall", maxIterations = 4, maxConsecutiveNoProgress = 1),
        )
        val result = engine.run("tester", AgentToolchain.SYSTEM_PERMISSIONS, "do complex task")
        assertFalse(result.success)
        assertTrue(result.summary.contains("loop stalled"))
        assertTrue(result.events.any { it.type.name == "PLAN_UPDATED" })
        assertTrue(result.events.any { it.type.name == "REFLECTION" })
    }

    @Test
    fun `loop retries when self check fails then succeeds`() {
        val root = Files.createTempDirectory("dynamicd-agent-loop-selfcheck").toFile()
        val moduleRoot = File(root, "modules").apply { mkdirs() }
        val manager = ModuleManager(
            modulesRoot = moduleRoot,
            runtimeBridge = object : RuntimeBridge {
                override fun bindEvent(moduleId: String, eventPath: String): ListenerHandle? = null
                override fun bindTimer(moduleId: String, timerSpec: String): TaskHandle? = null
            },
            snapshotManager = SnapshotManager(File(root, "snapshots")),
            agentToolchain = AgentToolchain(AuditLogger(File(root, "audit.log"))),
            integrationRegistry = IntegrationRegistry.fromAvailability(
                mapOf("PlaceholderAPI" to true, "LuckPerms" to true, "Vault" to true),
            ),
            placeholderBridge = InMemoryPlaceholderRegistrar(),
            securityPolicy = SecurityPolicy(),
            defaultSandboxLevel = SandboxLevel.ADMIN,
            logger = {},
        )

        val provider = object : LlmProvider {
            override val name: String = "selfcheck"
            private var count = 0
            override fun complete(request: LlmRequest): LlmResponse {
                count++
                return when (count) {
                    1 -> LlmResponse("TOOL:create demo module \"dynamicd:demo\"")
                    2 -> LlmResponse("FINAL:first")
                    3 -> LlmResponse("FAIL:missing load")
                    4 -> LlmResponse("TOOL:load demo")
                    5 -> LlmResponse("FINAL:done")
                    else -> LlmResponse("PASS")
                }
            }
        }
        val engine = AgentLoopEngine(
            provider = provider,
            toolExecutor = AgentToolExecutor(manager),
            config = AgentLoopConfig(
                model = "selfcheck",
                maxIterations = 8,
                selfCheckEnabled = true,
                maxSelfCheckRetries = 2,
            ),
        )
        val result = engine.run("tester", AgentToolchain.SYSTEM_PERMISSIONS, "create and load demo")
        assertTrue(result.success)
        assertTrue(result.summary.contains("done"))
        assertTrue(result.events.any { it.type.name == "REFLECTION" && it.message.contains("self-check failed") })
    }
}
