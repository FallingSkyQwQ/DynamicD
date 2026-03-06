package icu.aetherland.dynamicd.agent.loop

class AgentService(
    private val engine: AgentLoopEngine,
    private val sessionStore: AgentSessionStore,
) {
    fun runPrompt(operator: String, permissions: Set<String>, prompt: String): AgentTurnResult {
        val result = engine.run(operator, permissions, prompt)
        sessionStore.append(result)
        return result
    }
}
