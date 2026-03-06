package icu.aetherland.dynamicd.agent.loop

import icu.aetherland.dynamicd.agent.llm.LlmMessage
import icu.aetherland.dynamicd.agent.llm.LlmProvider
import icu.aetherland.dynamicd.agent.llm.LlmRequest
import java.util.UUID

data class AgentLoopConfig(
    val model: String,
    val maxIterations: Int,
    val maxConsecutiveNoProgress: Int = 2,
    val selfCheckEnabled: Boolean = false,
    val maxSelfCheckRetries: Int = 1,
)

class AgentLoopEngine(
    private val provider: LlmProvider,
    private val toolExecutor: AgentToolExecutor,
    private val config: AgentLoopConfig,
    private val taskDecomposer: TaskDecomposer = TaskDecomposer(),
) {
    fun run(operator: String, permissions: Set<String>, prompt: String): AgentTurnResult {
        val requestId = UUID.randomUUID().toString()
        val events = mutableListOf<AgentEvent>()
        val planLines = mutableListOf<String>()
        val completedToolCalls = mutableListOf<String>()
        var noProgressCount = 0
        var selfCheckRetries = 0
        val initialPlan = taskDecomposer.decompose(prompt)
        val transcript = mutableListOf(
            LlmMessage(
                role = "system",
                content = """
                You are DynamicD autonomous agent.
                Use this strict protocol:
                PLAN:<short next step>
                TOOL:<name> <args>
                REFLECT:<what changed>
                FINAL:<summary> (only when done)
                Keep actions auditable and minimal.
                """.trimIndent(),
            ),
            LlmMessage(role = "user", content = prompt),
        )
        if (initialPlan.isNotEmpty()) {
            transcript += LlmMessage(
                role = "system",
                content = "Seed plan: ${initialPlan.joinToString(" | ")}",
            )
        }

        events += AgentEvent(requestId = requestId, type = AgentEventType.TURN_STARTED, message = "turn started")
        if (initialPlan.isNotEmpty()) {
            events += AgentEvent(
                requestId = requestId,
                type = AgentEventType.PLAN_UPDATED,
                message = "seed=${initialPlan.joinToString(" | ").take(240)}",
            )
            planLines += initialPlan
        }
        repeat(config.maxIterations) { step ->
            val response = provider.complete(
                LlmRequest(
                    model = config.model,
                    messages = transcript,
                ),
            )
            val content = response.content.trim()
            if (content.isBlank()) {
                noProgressCount++
                transcript += LlmMessage("user", "Empty response is invalid. Continue with PLAN/TOOL/REFLECT/FINAL.")
                return@repeat
            }
            events += AgentEvent(
                requestId = requestId,
                type = AgentEventType.MODEL_RESPONSE,
                message = "step=$step content=${content.take(240)}",
            )
            val modelStep = AgentProtocol.parse(content)

            if (modelStep.plans.isNotEmpty()) {
                planLines += modelStep.plans
                events += AgentEvent(
                    requestId = requestId,
                    type = AgentEventType.PLAN_UPDATED,
                    message = modelStep.plans.joinToString(" | ").take(240),
                )
            }
            if (modelStep.reflections.isNotEmpty()) {
                events += AgentEvent(
                    requestId = requestId,
                    type = AgentEventType.REFLECTION,
                    message = modelStep.reflections.joinToString(" | ").take(240),
                )
            }

            if (modelStep.toolCalls.isNotEmpty()) {
                modelStep.toolCalls.forEach { toolCall ->
                    events += AgentEvent(
                        requestId = requestId,
                        type = AgentEventType.TOOL_CALL,
                        message = "tool=${toolCall.name} args=${toolCall.args.take(120)}",
                    )
                }
                val toolResults = toolExecutor.executeBatch(operator, permissions, modelStep.toolCalls)
                toolResults.forEachIndexed { index, result ->
                    val call = modelStep.toolCalls[index]
                    completedToolCalls += "${call.name} ${call.args}".trim()
                    events += AgentEvent(
                        requestId = requestId,
                        type = AgentEventType.TOOL_RESULT,
                        message = "tool=${call.name} result=${result.take(180)}",
                    )
                }
                transcript += LlmMessage("assistant", content)
                transcript += LlmMessage("tool", "TOOL_RESULT ${toolResults.joinToString(" | ")}")
                noProgressCount = 0
                return@repeat
            }

            val finalSummary = modelStep.finalSummary
            if (finalSummary != null) {
                val selfCheck = if (config.selfCheckEnabled) {
                    verifyCompletion(prompt, finalSummary, completedToolCalls)
                } else {
                    VerificationResult(pass = true, reason = "disabled")
                }
                if (!selfCheck.pass && selfCheckRetries < config.maxSelfCheckRetries) {
                    selfCheckRetries++
                    events += AgentEvent(
                        requestId = requestId,
                        type = AgentEventType.REFLECTION,
                        message = "self-check failed reason=${selfCheck.reason}",
                    )
                    transcript += LlmMessage("assistant", content)
                    transcript += LlmMessage(
                        "user",
                        "Self-check failed: ${selfCheck.reason}. Continue and fix gaps before FINAL.",
                    )
                    return@repeat
                }
                events += AgentEvent(
                    requestId = requestId,
                    type = AgentEventType.TURN_COMPLETED,
                    message = "summary=${finalSummary.take(240)}",
                )
                return AgentTurnResult(
                    requestId = requestId,
                    success = true,
                    summary = buildSummary(finalSummary, planLines, completedToolCalls),
                    events = events,
                )
            }

            noProgressCount++
            if (noProgressCount > config.maxConsecutiveNoProgress) {
                val summary = "loop stalled: no tool/final response after $noProgressCount iterations"
                events += AgentEvent(
                    requestId = requestId,
                    type = AgentEventType.TURN_FAILED,
                    message = summary,
                )
                return AgentTurnResult(
                    requestId = requestId,
                    success = false,
                    summary = buildSummary(summary, planLines, completedToolCalls),
                    events = events,
                )
            }
            transcript += LlmMessage("assistant", content)
            transcript += LlmMessage("user", "No actionable TOOL/FINAL found. Continue with strict PLAN/TOOL/REFLECT/FINAL.")
        }

        events += AgentEvent(
            requestId = requestId,
            type = AgentEventType.TURN_FAILED,
            message = "max iterations reached",
        )
        return AgentTurnResult(
            requestId = requestId,
            success = false,
            summary = buildSummary("max iterations reached", planLines, completedToolCalls),
            events = events,
        )
    }

    private fun buildSummary(
        finalSummary: String,
        plans: List<String>,
        completedToolCalls: List<String>,
    ): String {
        val planPart = if (plans.isEmpty()) "none" else plans.distinct().joinToString(" -> ").take(280)
        val toolsPart = if (completedToolCalls.isEmpty()) "none" else completedToolCalls.joinToString(" | ").take(280)
        return "result=$finalSummary plan=$planPart tools=$toolsPart"
    }

    private fun verifyCompletion(
        prompt: String,
        finalSummary: String,
        completedToolCalls: List<String>,
    ): VerificationResult {
        return try {
            val response = provider.complete(
                LlmRequest(
                    model = config.model,
                    temperature = 0.0,
                    messages = listOf(
                        LlmMessage(
                            "system",
                            "You are a strict verifier. Reply with PASS or FAIL:<reason> only.",
                        ),
                        LlmMessage("user", "Objective: $prompt"),
                        LlmMessage("user", "Summary: $finalSummary"),
                        LlmMessage("user", "Executed tools: ${completedToolCalls.joinToString(" | ")}"),
                    ),
                ),
            ).content.trim()
            if (response.startsWith("PASS", ignoreCase = true)) {
                VerificationResult(pass = true, reason = "pass")
            } else {
                val reason = response.substringAfter("FAIL:", "insufficient confidence").trim()
                VerificationResult(pass = false, reason = reason)
            }
        } catch (ex: Exception) {
            VerificationResult(pass = false, reason = "verifier error: ${ex.message}")
        }
    }

    private data class VerificationResult(
        val pass: Boolean,
        val reason: String,
    )
}
