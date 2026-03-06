package icu.aetherland.dynamicd.ops

import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.agent.loop.AgentRuntimeStats
import icu.aetherland.dynamicd.module.ModuleManager
import java.io.File
import kotlin.math.max

enum class BenchScenario {
    STANDARD,
    MIXED,
    SOAK,
}

data class BenchReport(
    val moduleId: String,
    val iterations: Int,
    val scenario: BenchScenario,
    val compileColdMs: Long,
    val compileWarmAvgMs: Long,
    val reloadAvgMs: Long,
    val incrementalReuseRatio: Double,
    val reloadSuccessRate: Double,
    val eventThroughputPerSec: Double,
    val agentSuccessRate: Double,
    val soakSamples: Int,
    val soakStartReloadMs: Long,
    val soakMidReloadMs: Long,
    val soakEndReloadMs: Long,
    val failureSample: String?,
)

class BenchService(
    private val moduleManager: ModuleManager,
    private val storageFile: File,
    private val agentStatsProvider: () -> AgentRuntimeStats = { AgentRuntimeStats(0, 0) },
) {
    init {
        storageFile.parentFile?.mkdirs()
    }

    fun run(moduleId: String, iterations: Int, scenario: BenchScenario = BenchScenario.STANDARD): BenchReport {
        val safeIterations = max(1, iterations)
        val perms = AgentToolchain.SYSTEM_PERMISSIONS

        val coldStart = System.nanoTime()
        val coldCompile = moduleManager.compileModule(moduleId, "bench", perms)
        val compileColdMs = elapsedMs(coldStart)

        var warmCompileTotal = 0L
        var reloadTotal = 0L
        var reloadSuccess = 0
        var reuseNumerator = 0
        var reuseDenominator = 0
        var eventCountTotal = 0
        var soakSamples = 0
        var soakStartReloadMs = 0L
        var soakMidReloadMs = 0L
        var soakEndReloadMs = 0L
        val failureSamples = mutableListOf<String>()

        repeat(safeIterations) {
            val runIndex = it
            val compileStart = System.nanoTime()
            val compile = moduleManager.compileModule(moduleId, "bench", perms)
            warmCompileTotal += elapsedMs(compileStart)
            reuseNumerator += compile.metrics.filesReused
            reuseDenominator += compile.metrics.filesCompiled + compile.metrics.filesReused
            eventCountTotal += compile.registry.events.size
            if (scenario == BenchScenario.MIXED) {
                eventCountTotal += compile.registry.commands.size + compile.registry.timers.size
            }

            val reloadStart = System.nanoTime()
            val ok = moduleManager.reloadModule(moduleId, "bench", perms)
            if (ok) reloadSuccess++
            val reloadMs = elapsedMs(reloadStart)
            reloadTotal += reloadMs
            if (!ok) {
                failureSamples += "iter=$runIndex reload=false"
            }
            if (scenario == BenchScenario.SOAK) {
                soakSamples++
                if (runIndex == 0) soakStartReloadMs = reloadMs
                if (runIndex == safeIterations / 2) soakMidReloadMs = reloadMs
                if (runIndex == safeIterations - 1) soakEndReloadMs = reloadMs
            }
        }
        val stats = agentStatsProvider()
        val throughput = if (reloadTotal <= 0) 0.0 else eventCountTotal.toDouble() / (reloadTotal.toDouble() / 1000.0)

        val report = BenchReport(
            moduleId = moduleId,
            iterations = safeIterations,
            scenario = scenario,
            compileColdMs = compileColdMs,
            compileWarmAvgMs = warmCompileTotal / safeIterations,
            reloadAvgMs = reloadTotal / safeIterations,
            incrementalReuseRatio = if (reuseDenominator == 0) 0.0 else reuseNumerator.toDouble() / reuseDenominator.toDouble(),
            reloadSuccessRate = reloadSuccess.toDouble() / safeIterations.toDouble(),
            eventThroughputPerSec = throughput,
            agentSuccessRate = stats.successRate,
            soakSamples = soakSamples,
            soakStartReloadMs = soakStartReloadMs,
            soakMidReloadMs = soakMidReloadMs,
            soakEndReloadMs = soakEndReloadMs,
            failureSample = failureSamples.firstOrNull(),
        )
        write(report)
        return report
    }

    fun latest(): BenchReport? {
        if (!storageFile.exists()) return null
        val values = storageFile.readLines()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i < 0) null else line.substring(0, i) to line.substring(i + 1)
            }
            .toMap()
        val moduleId = values["moduleId"] ?: return null
        val iterations = values["iterations"]?.toIntOrNull() ?: return null
        val scenario = values["scenario"]?.let {
            runCatching { BenchScenario.valueOf(it) }.getOrNull()
        } ?: return null
        val compileColdMs = values["compileColdMs"]?.toLongOrNull() ?: return null
        val compileWarmAvgMs = values["compileWarmAvgMs"]?.toLongOrNull() ?: return null
        val reloadAvgMs = values["reloadAvgMs"]?.toLongOrNull() ?: return null
        val incrementalReuseRatio = values["incrementalReuseRatio"]?.toDoubleOrNull() ?: return null
        val reloadSuccessRate = values["reloadSuccessRate"]?.toDoubleOrNull() ?: return null
        val eventThroughputPerSec = values["eventThroughputPerSec"]?.toDoubleOrNull() ?: return null
        val agentSuccessRate = values["agentSuccessRate"]?.toDoubleOrNull() ?: return null
        val soakSamples = values["soakSamples"]?.toIntOrNull() ?: return null
        val soakStartReloadMs = values["soakStartReloadMs"]?.toLongOrNull() ?: return null
        val soakMidReloadMs = values["soakMidReloadMs"]?.toLongOrNull() ?: return null
        val soakEndReloadMs = values["soakEndReloadMs"]?.toLongOrNull() ?: return null
        val failureSample = values["failureSample"]?.takeIf { it != "null" }
        return BenchReport(
            moduleId,
            iterations,
            scenario,
            compileColdMs,
            compileWarmAvgMs,
            reloadAvgMs,
            incrementalReuseRatio,
            reloadSuccessRate,
            eventThroughputPerSec,
            agentSuccessRate,
            soakSamples,
            soakStartReloadMs,
            soakMidReloadMs,
            soakEndReloadMs,
            failureSample,
        )
    }

    private fun write(report: BenchReport) {
        storageFile.writeText(
            """
            moduleId=${report.moduleId}
            iterations=${report.iterations}
            scenario=${report.scenario.name}
            compileColdMs=${report.compileColdMs}
            compileWarmAvgMs=${report.compileWarmAvgMs}
            reloadAvgMs=${report.reloadAvgMs}
            incrementalReuseRatio=${report.incrementalReuseRatio}
            reloadSuccessRate=${report.reloadSuccessRate}
            eventThroughputPerSec=${report.eventThroughputPerSec}
            agentSuccessRate=${report.agentSuccessRate}
            soakSamples=${report.soakSamples}
            soakStartReloadMs=${report.soakStartReloadMs}
            soakMidReloadMs=${report.soakMidReloadMs}
            soakEndReloadMs=${report.soakEndReloadMs}
            failureSample=${report.failureSample}
            """.trimIndent() + "\n",
        )
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
