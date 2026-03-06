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
    val failureCount: Int,
    val reloadSuccessTrendDelta: Double,
)

data class BenchSuiteReport(
    val scenario: BenchScenario,
    val iterations: Int,
    val moduleCount: Int,
    val avgCompileWarmMs: Long,
    val avgReloadMs: Long,
    val avgReloadSuccessRate: Double,
    val avgEventThroughputPerSec: Double,
    val failedModules: List<String>,
    val failedModuleCount: Int,
    val failureBuckets: Map<String, Int>,
    val avgReloadSuccessTrendDelta: Double,
)

class BenchService(
    private val moduleManager: ModuleManager,
    private val storageFile: File,
    private val suiteStorageFile: File = File(storageFile.parentFile ?: File("."), "latest-suite.report"),
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
        var firstHalfSuccess = 0
        var secondHalfSuccess = 0
        val split = max(1, safeIterations / 2)

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
            if (!compile.success) {
                failureSamples += "compile_failed:iter=$runIndex"
            }

            val reloadStart = System.nanoTime()
            val ok = moduleManager.reloadModule(moduleId, "bench", perms)
            if (ok) reloadSuccess++
            if (ok) {
                if (runIndex < split) firstHalfSuccess++ else secondHalfSuccess++
            }
            val reloadMs = elapsedMs(reloadStart)
            reloadTotal += reloadMs
            if (!ok) {
                failureSamples += "reload_failed:iter=$runIndex"
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
        val firstHalfTotal = split
        val secondHalfTotal = safeIterations - split
        val firstRate = if (firstHalfTotal <= 0) 0.0 else firstHalfSuccess.toDouble() / firstHalfTotal.toDouble()
        val secondRate = if (secondHalfTotal <= 0) 0.0 else secondHalfSuccess.toDouble() / secondHalfTotal.toDouble()

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
            failureCount = failureSamples.size,
            reloadSuccessTrendDelta = secondRate - firstRate,
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
        val failureCount = values["failureCount"]?.toIntOrNull() ?: return null
        val reloadSuccessTrendDelta = values["reloadSuccessTrendDelta"]?.toDoubleOrNull() ?: return null
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
            failureCount,
            reloadSuccessTrendDelta,
        )
    }

    fun runSuite(iterations: Int, scenario: BenchScenario = BenchScenario.MIXED): BenchSuiteReport {
        val safeIterations = max(1, iterations)
        val moduleIds = moduleManager.listModules().map { it.id }.sorted()
        if (moduleIds.isEmpty()) {
            val empty = BenchSuiteReport(
                scenario = scenario,
                iterations = safeIterations,
                moduleCount = 0,
                avgCompileWarmMs = 0,
                avgReloadMs = 0,
                avgReloadSuccessRate = 0.0,
                avgEventThroughputPerSec = 0.0,
                failedModules = emptyList(),
                failedModuleCount = 0,
                failureBuckets = emptyMap(),
                avgReloadSuccessTrendDelta = 0.0,
            )
            writeSuite(empty)
            return empty
        }
        val reports = moduleIds.map { moduleId -> run(moduleId, safeIterations, scenario) }
        val failedModules = reports.filter { it.reloadSuccessRate < 1.0 }.map { it.moduleId }
        val failureBuckets = mutableMapOf<String, Int>()
        reports.forEach { r ->
            r.failureSample?.substringBefore(':')?.let { bucket ->
                failureBuckets[bucket] = (failureBuckets[bucket] ?: 0) + 1
            }
        }
        val suite = BenchSuiteReport(
            scenario = scenario,
            iterations = safeIterations,
            moduleCount = reports.size,
            avgCompileWarmMs = reports.map { it.compileWarmAvgMs }.average().toLong(),
            avgReloadMs = reports.map { it.reloadAvgMs }.average().toLong(),
            avgReloadSuccessRate = reports.map { it.reloadSuccessRate }.average(),
            avgEventThroughputPerSec = reports.map { it.eventThroughputPerSec }.average(),
            failedModules = failedModules,
            failedModuleCount = failedModules.size,
            failureBuckets = failureBuckets.toSortedMap(),
            avgReloadSuccessTrendDelta = reports.map { it.reloadSuccessTrendDelta }.average(),
        )
        writeSuite(suite)
        return suite
    }

    fun latestSuite(): BenchSuiteReport? {
        if (!suiteStorageFile.exists()) return null
        val values = suiteStorageFile.readLines()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i < 0) null else line.substring(0, i) to line.substring(i + 1)
            }
            .toMap()
        val scenario = values["scenario"]?.let {
            runCatching { BenchScenario.valueOf(it) }.getOrNull()
        } ?: return null
        val iterations = values["iterations"]?.toIntOrNull() ?: return null
        val moduleCount = values["moduleCount"]?.toIntOrNull() ?: return null
        val avgCompileWarmMs = values["avgCompileWarmMs"]?.toLongOrNull() ?: return null
        val avgReloadMs = values["avgReloadMs"]?.toLongOrNull() ?: return null
        val avgReloadSuccessRate = values["avgReloadSuccessRate"]?.toDoubleOrNull() ?: return null
        val avgEventThroughputPerSec = values["avgEventThroughputPerSec"]?.toDoubleOrNull() ?: return null
        val failedModuleCount = values["failedModuleCount"]?.toIntOrNull() ?: return null
        val avgReloadSuccessTrendDelta = values["avgReloadSuccessTrendDelta"]?.toDoubleOrNull() ?: return null
        val failedModules = values["failedModules"]
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val failureBuckets = values["failureBuckets"]
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { raw ->
                val idx = raw.indexOf(':')
                if (idx < 0) null else {
                    val k = raw.substring(0, idx)
                    val v = raw.substring(idx + 1).toIntOrNull() ?: 0
                    k to v
                }
            }
            ?.toMap()
            ?: emptyMap()
        return BenchSuiteReport(
            scenario = scenario,
            iterations = iterations,
            moduleCount = moduleCount,
            avgCompileWarmMs = avgCompileWarmMs,
            avgReloadMs = avgReloadMs,
            avgReloadSuccessRate = avgReloadSuccessRate,
            avgEventThroughputPerSec = avgEventThroughputPerSec,
            failedModules = failedModules,
            failedModuleCount = failedModuleCount,
            failureBuckets = failureBuckets,
            avgReloadSuccessTrendDelta = avgReloadSuccessTrendDelta,
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
            failureCount=${report.failureCount}
            reloadSuccessTrendDelta=${report.reloadSuccessTrendDelta}
            """.trimIndent() + "\n",
        )
    }

    private fun writeSuite(report: BenchSuiteReport) {
        suiteStorageFile.writeText(
            """
            scenario=${report.scenario.name}
            iterations=${report.iterations}
            moduleCount=${report.moduleCount}
            avgCompileWarmMs=${report.avgCompileWarmMs}
            avgReloadMs=${report.avgReloadMs}
            avgReloadSuccessRate=${report.avgReloadSuccessRate}
            avgEventThroughputPerSec=${report.avgEventThroughputPerSec}
            failedModules=${report.failedModules.joinToString(",")}
            failedModuleCount=${report.failedModuleCount}
            failureBuckets=${report.failureBuckets.entries.joinToString(",") { "${it.key}:${it.value}" }}
            avgReloadSuccessTrendDelta=${report.avgReloadSuccessTrendDelta}
            """.trimIndent() + "\n",
        )
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
