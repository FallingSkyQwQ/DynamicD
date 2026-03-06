package icu.aetherland.dynamicd.ops

import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.audit.AuditLogger
import icu.aetherland.dynamicd.integration.InMemoryPlaceholderRegistrar
import icu.aetherland.dynamicd.integration.IntegrationRegistry
import icu.aetherland.dynamicd.agent.loop.AgentRuntimeStats
import icu.aetherland.dynamicd.module.ModuleManager
import icu.aetherland.dynamicd.runtime.ListenerHandle
import icu.aetherland.dynamicd.runtime.RuntimeBridge
import icu.aetherland.dynamicd.runtime.TaskHandle
import icu.aetherland.dynamicd.security.SandboxLevel
import icu.aetherland.dynamicd.security.SecurityPolicy
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BenchServiceTest {
    @Test
    fun `runs and persists benchmark report`() {
        val root = Files.createTempDirectory("dynamicd-bench").toFile()
        val modulesRoot = File(root, "modules").apply { mkdirs() }
        val welcomeDir = File(modulesRoot, "welcome").apply { mkdirs() }
        File(welcomeDir, "mod.yuz").writeText(
            """
            module "dynamicd:welcome"
            on player join { }
            every 5s { }
            """.trimIndent(),
        )
        val manager = ModuleManager(
            modulesRoot = modulesRoot,
            runtimeBridge = object : RuntimeBridge {
                override fun bindEvent(moduleId: String, eventPath: String): ListenerHandle? = object : ListenerHandle {
                    override fun unregister() {}
                }
                override fun bindTimer(moduleId: String, timerSpec: String): TaskHandle? = object : TaskHandle {
                    override fun cancel() {}
                }
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

        val reportFile = File(root, "data/bench/latest.report")
        val service = BenchService(manager, reportFile) { AgentRuntimeStats(totalRuns = 10, successfulRuns = 7) }
        val report = service.run("welcome", 3, BenchScenario.SOAK)
        assertEquals("welcome", report.moduleId)
        assertEquals(3, report.iterations)
        assertEquals(BenchScenario.SOAK, report.scenario)
        assertTrue(report.compileColdMs >= 0)
        assertTrue(report.compileWarmAvgMs >= 0)
        assertTrue(report.reloadAvgMs >= 0)
        assertTrue(report.reloadSuccessRate >= 0.0)
        assertTrue(report.eventThroughputPerSec >= 0.0)
        assertTrue(report.agentSuccessRate >= 0.0)
        assertEquals(3, report.soakSamples)

        val latest = service.latest()
        assertNotNull(latest)
        assertEquals("welcome", latest.moduleId)
        assertEquals(0.7, latest.agentSuccessRate)
        assertEquals(BenchScenario.SOAK, latest.scenario)
    }
}
