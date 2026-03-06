package icu.aetherland.dynamicd.ops

import icu.aetherland.dynamicd.agent.AgentToolchain
import icu.aetherland.dynamicd.module.ModuleManager
import java.io.File
import kotlin.math.max

data class BenchReport(
    val moduleId: String,
    val iterations: Int,
    val compileColdMs: Long,
    val compileWarmAvgMs: Long,
    val reloadAvgMs: Long,
    val incrementalReuseRatio: Double,
)

class BenchService(
    private val moduleManager: ModuleManager,
    private val storageFile: File,
) {
    init {
        storageFile.parentFile?.mkdirs()
    }

    fun run(moduleId: String, iterations: Int): BenchReport {
        val safeIterations = max(1, iterations)
        val perms = AgentToolchain.SYSTEM_PERMISSIONS

        val coldStart = System.nanoTime()
        val coldCompile = moduleManager.compileModule(moduleId, "bench", perms)
        val compileColdMs = elapsedMs(coldStart)

        var warmCompileTotal = 0L
        var reloadTotal = 0L
        var reuseNumerator = 0
        var reuseDenominator = 0

        repeat(safeIterations) {
            val compileStart = System.nanoTime()
            val compile = moduleManager.compileModule(moduleId, "bench", perms)
            warmCompileTotal += elapsedMs(compileStart)
            reuseNumerator += compile.metrics.filesReused
            reuseDenominator += compile.metrics.filesCompiled + compile.metrics.filesReused

            val reloadStart = System.nanoTime()
            moduleManager.reloadModule(moduleId, "bench", perms)
            reloadTotal += elapsedMs(reloadStart)
        }

        val report = BenchReport(
            moduleId = moduleId,
            iterations = safeIterations,
            compileColdMs = compileColdMs,
            compileWarmAvgMs = warmCompileTotal / safeIterations,
            reloadAvgMs = reloadTotal / safeIterations,
            incrementalReuseRatio = if (reuseDenominator == 0) 0.0 else reuseNumerator.toDouble() / reuseDenominator.toDouble(),
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
        val compileColdMs = values["compileColdMs"]?.toLongOrNull() ?: return null
        val compileWarmAvgMs = values["compileWarmAvgMs"]?.toLongOrNull() ?: return null
        val reloadAvgMs = values["reloadAvgMs"]?.toLongOrNull() ?: return null
        val incrementalReuseRatio = values["incrementalReuseRatio"]?.toDoubleOrNull() ?: return null
        return BenchReport(moduleId, iterations, compileColdMs, compileWarmAvgMs, reloadAvgMs, incrementalReuseRatio)
    }

    private fun write(report: BenchReport) {
        storageFile.writeText(
            """
            moduleId=${report.moduleId}
            iterations=${report.iterations}
            compileColdMs=${report.compileColdMs}
            compileWarmAvgMs=${report.compileWarmAvgMs}
            reloadAvgMs=${report.reloadAvgMs}
            incrementalReuseRatio=${report.incrementalReuseRatio}
            """.trimIndent() + "\n",
        )
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
