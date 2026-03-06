package icu.aetherland.dynamicd.agent.loop

import java.util.concurrent.atomic.AtomicLong

data class AgentRuntimeStats(
    val totalRuns: Long,
    val successfulRuns: Long,
) {
    val successRate: Double
        get() = if (totalRuns == 0L) 0.0 else successfulRuns.toDouble() / totalRuns.toDouble()
}

class AgentService(
    private val engine: AgentLoopEngine,
    private val sessionStore: AgentSessionStore,
    private val memoryStore: AgentMemoryStore,
) {
    private val totalRuns = AtomicLong(0)
    private val successfulRuns = AtomicLong(0)

    fun runPrompt(operator: String, permissions: Set<String>, prompt: String): AgentTurnResult {
        val memoryContext = buildMemoryContext(operator)
        val effectivePrompt = if (memoryContext.isBlank()) prompt else "$memoryContext\n\nTask:\n$prompt"
        val result = engine.run(operator, permissions, effectivePrompt)
        sessionStore.append(result)
        memoryStore.append(AgentMemoryStore.entry(operator, prompt, result))
        totalRuns.incrementAndGet()
        if (result.success) {
            successfulRuns.incrementAndGet()
        }
        return result
    }

    fun runtimeStats(): AgentRuntimeStats {
        return AgentRuntimeStats(
            totalRuns = totalRuns.get(),
            successfulRuns = successfulRuns.get(),
        )
    }

    private fun buildMemoryContext(operator: String): String {
        val recent = memoryStore.recent(operator, 5)
        if (recent.isEmpty()) return ""
        val lines = recent.joinToString("\n") { entry ->
            "memory ts=${entry.timestamp} success=${entry.success} prompt=${entry.prompt} summary=${entry.summary}"
        }
        return "Recent execution memory:\n$lines"
    }
}
