package icu.aetherland.dynamicd.agent.loop

class AgentService(
    private val engine: AgentLoopEngine,
    private val sessionStore: AgentSessionStore,
    private val memoryStore: AgentMemoryStore,
) {
    fun runPrompt(operator: String, permissions: Set<String>, prompt: String): AgentTurnResult {
        val memoryContext = buildMemoryContext(operator)
        val effectivePrompt = if (memoryContext.isBlank()) prompt else "$memoryContext\n\nTask:\n$prompt"
        val result = engine.run(operator, permissions, effectivePrompt)
        sessionStore.append(result)
        memoryStore.append(AgentMemoryStore.entry(operator, prompt, result))
        return result
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
