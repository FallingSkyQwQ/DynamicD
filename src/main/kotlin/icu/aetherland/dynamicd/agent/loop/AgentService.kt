package icu.aetherland.dynamicd.agent.loop

class AgentService(private val engine: AgentLoopEngine) {
    fun runPrompt(operator: String, permissions: Set<String>, prompt: String): AgentTurnResult {
        return engine.run(operator, permissions, prompt)
    }
}
