package icu.aetherland.dynamicd.agent.loop

import icu.aetherland.dynamicd.agent.llm.LlmMessage
import icu.aetherland.dynamicd.agent.llm.LlmProvider
import icu.aetherland.dynamicd.agent.llm.LlmRequest
import java.util.UUID

data class AgentLoopConfig(
    val model: String,
    val maxIterations: Int,
)

class AgentLoopEngine(
    private val provider: LlmProvider,
    private val toolExecutor: AgentToolExecutor,
    private val config: AgentLoopConfig,
) {
    fun run(operator: String, permissions: Set<String>, prompt: String): AgentTurnResult {
        val requestId = UUID.randomUUID().toString()
        val events = mutableListOf<AgentEvent>()
        val transcript = mutableListOf(
            LlmMessage(
                role = "system",
                content = """
                You are DynamicD autonomous agent.
                Use TOOL calls with format: TOOL:<name> <args>
                Finish with: FINAL:<summary>
                """.trimIndent(),
            ),
            LlmMessage(role = "user", content = prompt),
        )

        events += AgentEvent(requestId = requestId, type = AgentEventType.TURN_STARTED, message = "turn started")
        repeat(config.maxIterations) { step ->
            val response = provider.complete(
                LlmRequest(
                    model = config.model,
                    messages = transcript,
                ),
            )
            val content = response.content.trim()
            events += AgentEvent(
                requestId = requestId,
                type = AgentEventType.MODEL_RESPONSE,
                message = "step=$step content=${content.take(240)}",
            )

            val toolCall = parseToolCall(content)
            if (toolCall != null) {
                events += AgentEvent(
                    requestId = requestId,
                    type = AgentEventType.TOOL_CALL,
                    message = "tool=${toolCall.name} args=${toolCall.args.take(120)}",
                )
                val result = toolExecutor.execute(operator, permissions, toolCall.name, toolCall.args)
                events += AgentEvent(
                    requestId = requestId,
                    type = AgentEventType.TOOL_RESULT,
                    message = "tool=${toolCall.name} result=${result.take(180)}",
                )
                transcript += LlmMessage("assistant", content)
                transcript += LlmMessage("tool", "TOOL_RESULT ${toolCall.name}: $result")
                return@repeat
            }

            val finalSummary = parseFinal(content)
            if (finalSummary != null) {
                events += AgentEvent(
                    requestId = requestId,
                    type = AgentEventType.TURN_COMPLETED,
                    message = "summary=${finalSummary.take(240)}",
                )
                return AgentTurnResult(
                    requestId = requestId,
                    success = true,
                    summary = finalSummary,
                    events = events,
                )
            }

            transcript += LlmMessage("assistant", content)
            transcript += LlmMessage("user", "No tool call/final summary detected, continue.")
        }

        events += AgentEvent(
            requestId = requestId,
            type = AgentEventType.TURN_FAILED,
            message = "max iterations reached",
        )
        return AgentTurnResult(
            requestId = requestId,
            success = false,
            summary = "max iterations reached",
            events = events,
        )
    }

    private fun parseToolCall(content: String): ToolCall? {
        val line = content.lines().firstOrNull { it.trim().startsWith("TOOL:", ignoreCase = true) } ?: return null
        val payload = line.substringAfter("TOOL:", "").trim()
        if (payload.isBlank()) {
            return null
        }
        val parts = payload.split(" ", limit = 2)
        return ToolCall(parts[0], parts.getOrElse(1) { "" })
    }

    private fun parseFinal(content: String): String? {
        val line = content.lines().firstOrNull { it.trim().startsWith("FINAL:", ignoreCase = true) } ?: return null
        return line.substringAfter("FINAL:", "").trim().ifBlank { null }
    }

    private data class ToolCall(val name: String, val args: String)
}
