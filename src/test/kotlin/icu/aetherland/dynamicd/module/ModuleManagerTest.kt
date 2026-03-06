package icu.aetherland.dynamicd.module

import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.audit.AuditLogger
import icu.aetherland.dynamicd.integration.InMemoryPlaceholderRegistrar
import icu.aetherland.dynamicd.integration.IntegrationRegistry
import icu.aetherland.dynamicd.persist.PersistStore
import icu.aetherland.dynamicd.ops.SnapshotManager
import icu.aetherland.dynamicd.runtime.ListenerHandle
import icu.aetherland.dynamicd.runtime.RuntimeBridge
import icu.aetherland.dynamicd.runtime.TaskHandle
import icu.aetherland.dynamicd.security.SandboxLevel
import icu.aetherland.dynamicd.security.SecurityPolicy
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModuleManagerTest {
    @Test
    fun `denies compile without permission and returns E0900`() {
        val env = testEnv("m1" to """module "dynamicd:m1"""")
        val result = env.manager.compileModule("m1", "tester", emptySet())
        assertFalse(result.success)
        assertTrue(result.diagnostics.any { it.code == "E0900" })
    }

    @Test
    fun `reload keeps single binding and unload cancels timers`() {
        val env = testEnv(
            "m1" to """
            module "dynamicd:m1"
            on player join { }
            every 1s { }
            command "/demo" permission "dynamicd.demo" { }
            """.trimIndent(),
        )
        val perms = AgentToolchain.SYSTEM_PERMISSIONS
        assertTrue(env.manager.compileModule("m1", "tester", perms).success)
        assertTrue(env.manager.loadModule("m1", "tester", perms))
        assertEquals(1, env.runtime.eventBindings("m1"))
        assertEquals(1, env.runtime.timerBindings("m1"))

        assertTrue(env.manager.reloadModule("m1", "tester", perms))
        assertEquals(1, env.runtime.eventBindings("m1"))
        assertEquals(1, env.runtime.timerBindings("m1"))

        assertTrue(env.manager.unloadModule("m1", "tester"))
        assertEquals(0, env.runtime.eventBindings("m1"))
        assertEquals(0, env.runtime.timerBindings("m1"))
    }

    @Test
    fun `isolates module runtime fault`() {
        val env = testEnv("faulty" to """module "dynamicd:faulty"""")
        val ok = env.manager.runModuleSafely("faulty") {
            error("boom")
        }
        assertFalse(ok)
        assertNotNull(env.manager.moduleLastRuntimeError("faulty"))
    }

    @Test
    fun `snapshot rollback restores enabled modules only`() {
        val env = testEnv(
            "a" to """module "dynamicd:a" on player join { }""",
            "b" to """module "dynamicd:b" on player join { }""",
        )
        val perms = AgentToolchain.SYSTEM_PERMISSIONS
        env.manager.compileModule("a", "tester", perms)
        env.manager.loadModule("a", "tester", perms)
        env.manager.compileModule("b", "tester", perms)
        // b intentionally remains disabled

        val snapshotId = env.manager.createSnapshot("tester", perms)
        assertNotNull(snapshotId)
        assertTrue(env.manager.listSnapshots().contains(snapshotId))

        env.manager.loadModule("b", "tester", perms)
        assertEquals(ModuleState.ENABLED, env.manager.moduleState("b"))
        assertTrue(env.manager.rollback(snapshotId, "tester", perms))
        assertEquals(ModuleState.ENABLED, env.manager.moduleState("a"))
        assertEquals(ModuleState.DISABLED, env.manager.moduleState("b"))
    }

    @Test
    fun `agent patch decision recorded with fallback strategy`() {
        val env = testEnv("m1" to """module "dynamicd:m1"""")
        val ok = env.manager.applyAgentPatch(
            moduleId = "m1",
            operator = "tester",
            grantedPermissions = AgentToolchain.SYSTEM_PERMISSIONS,
            astAvailable = false,
            tokenAvailable = false,
        )
        assertTrue(ok)
        val auditText = env.auditFile.readText()
        assertTrue(auditText.contains("action=patch"))
        assertTrue(auditText.contains("strategy=TEXT"))
    }

    @Test
    fun `rollback latest usable snapshot works`() {
        val env = testEnv(
            "a" to """module "dynamicd:a" on player join { }""",
        )
        val perms = AgentToolchain.SYSTEM_PERMISSIONS
        env.manager.compileModule("a", "tester", perms)
        env.manager.loadModule("a", "tester", perms)
        val snapshotId = env.manager.createSnapshot("tester", perms)
        assertNotNull(snapshotId)
        env.manager.unloadModule("a", "tester")
        assertTrue(env.manager.rollbackLatestUsable("tester", perms))
        assertEquals(ModuleState.ENABLED, env.manager.moduleState("a"))
    }

    @Test
    fun `dangerous command requires dangerous permission`() {
        val env = testEnv("m1" to """module "dynamicd:m1"""")
        val withoutDanger = setOf("dynamicd.agent.command")
        val denied = env.manager.runDangerousCommand("op Steve", "tester", withoutDanger)
        assertFalse(denied.allowed)
        val withDanger = setOf("dynamicd.agent.command", "dynamicd.agent.command.dangerous")
        val allowed = env.manager.runDangerousCommand("op Steve", "tester", withDanger)
        assertTrue(allowed.allowed)
    }

    @Test
    fun `dependency graph and load order are built from use dynamicd imports`() {
        val env = testEnv(
            "core" to """module "dynamicd:core"""",
            "welcome" to """
            module "dynamicd:welcome"
            use dynamicd:core
            """.trimIndent(),
        )
        val graph = env.manager.moduleDependencyGraph()
        assertEquals(emptyList(), graph["core"])
        assertEquals(listOf("core"), graph["welcome"])
        assertEquals(listOf("core", "welcome"), env.manager.moduleLoadOrder())
    }

    @Test
    fun `load denied when dependency not enabled`() {
        val env = testEnv(
            "core" to """module "dynamicd:core"""",
            "welcome" to """
            module "dynamicd:welcome"
            use dynamicd:core
            on player join { }
            """.trimIndent(),
        )
        val perms = AgentToolchain.SYSTEM_PERMISSIONS
        assertTrue(env.manager.compileModule("welcome", "tester", perms).success)
        assertFalse(env.manager.loadModule("welcome", "tester", perms))
        assertTrue(env.manager.compileModule("core", "tester", perms).success)
        assertTrue(env.manager.loadModule("core", "tester", perms))
        assertTrue(env.manager.loadModule("welcome", "tester", perms))
    }

    private fun testEnv(vararg modules: Pair<String, String>): Env {
        val root = Files.createTempDirectory("dynamicd-mm").toFile()
        val modulesRoot = File(root, "modules").apply { mkdirs() }
        modules.forEach { (id, source) ->
            val dir = File(modulesRoot, id).apply { mkdirs() }
            File(dir, "mod.yuz").writeText(source)
        }
        val auditFile = File(root, "logs/audit.log")
        val runtime = FakeRuntimeBridge()
        val manager = ModuleManager(
            modulesRoot = modulesRoot,
            runtimeBridge = runtime,
            snapshotManager = SnapshotManager(File(root, "snapshots")),
            agentToolchain = AgentToolchain(AuditLogger(auditFile)),
            integrationRegistry = IntegrationRegistry.fromAvailability(
                mapOf(
                    "PlaceholderAPI" to true,
                    "LuckPerms" to true,
                    "Vault" to true,
                ),
            ),
            placeholderBridge = InMemoryPlaceholderRegistrar(),
            securityPolicy = SecurityPolicy(),
            defaultSandboxLevel = SandboxLevel.ADMIN,
            logger = {},
            persistStore = PersistStore(File(root, "persist.db")),
        )
        manager.discoverModules()
        return Env(manager, runtime, auditFile)
    }

    private data class Env(
        val manager: ModuleManager,
        val runtime: FakeRuntimeBridge,
        val auditFile: File,
    )
}

private class FakeRuntimeBridge : RuntimeBridge {
    private val eventHandles = mutableMapOf<String, MutableList<FakeListenerHandle>>()
    private val taskHandles = mutableMapOf<String, MutableList<FakeTaskHandle>>()

    override fun bindEvent(moduleId: String, eventPath: String): ListenerHandle {
        val handle = FakeListenerHandle { eventHandles[moduleId]?.removeIf { it.cancelled } }
        eventHandles.getOrPut(moduleId) { mutableListOf() }.add(handle)
        return handle
    }

    override fun bindTimer(moduleId: String, timerSpec: String): TaskHandle {
        val handle = FakeTaskHandle { taskHandles[moduleId]?.removeIf { it.cancelled } }
        taskHandles.getOrPut(moduleId) { mutableListOf() }.add(handle)
        return handle
    }

    fun eventBindings(moduleId: String): Int = eventHandles[moduleId]?.count { !it.cancelled } ?: 0

    fun timerBindings(moduleId: String): Int = taskHandles[moduleId]?.count { !it.cancelled } ?: 0

    private class FakeListenerHandle(
        private val onCancel: () -> Unit,
    ) : ListenerHandle {
        var cancelled: Boolean = false

        override fun unregister() {
            cancelled = true
            onCancel()
        }
    }

    private class FakeTaskHandle(
        private val onCancel: () -> Unit,
    ) : TaskHandle {
        var cancelled: Boolean = false

        override fun cancel() {
            cancelled = true
            onCancel()
        }
    }
}
