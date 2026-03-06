package icu.aetherland.dynamicd.agent.loop

import java.time.Instant
import java.util.UUID

enum class AgentEventType {
    TURN_STARTED,
    MODEL_RESPONSE,
    TOOL_CALL,
    TOOL_RESULT,
    TURN_COMPLETED,
    TURN_FAILED,
}

data class AgentEvent(
    val requestId: String = UUID.randomUUID().toString(),
    val type: AgentEventType,
    val message: String,
    val timestamp: Instant = Instant.now(),
)

data class AgentTurnResult(
    val requestId: String,
    val success: Boolean,
    val summary: String,
    val events: List<AgentEvent>,
)
