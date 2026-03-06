package icu.aetherland.dynamicd.agent.llm

data class LlmMessage(
    val role: String,
    val content: String,
)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double = 0.2,
)

data class LlmResponse(
    val content: String,
)

interface LlmProvider {
    val name: String
    fun complete(request: LlmRequest): LlmResponse
}
