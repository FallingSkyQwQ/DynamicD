package icu.aetherland.dynamicd.agent.loop

import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

class AgentSessionStore(private val root: File) {
    init {
        root.mkdirs()
    }

    fun append(result: AgentTurnResult) {
        val day = DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now())
        val file = File(root, "agent-session-$day.log")
        val lines = buildString {
            append("requestId=${result.requestId} success=${result.success} summary=${sanitize(result.summary)}")
            appendLine()
            result.events.forEach { event ->
                append("event=${event.type} ts=${Instant.ofEpochSecond(event.timestamp.epochSecond)} msg=${sanitize(event.message)}")
                appendLine()
            }
            appendLine()
        }
        file.appendText(lines)
    }

    private fun sanitize(input: String): String {
        return input.replace("\n", "\\n").replace("\r", "\\r")
    }
}
