package icu.aetherland.dynamicd.agent.loop

data class AgentModelStep(
    val plans: List<String> = emptyList(),
    val reflections: List<String> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val finalSummary: String? = null,
)

data class ToolCall(
    val name: String,
    val args: String,
)

object AgentProtocol {
    fun parse(content: String): AgentModelStep {
        val plans = mutableListOf<String>()
        val reflections = mutableListOf<String>()
        val toolCalls = mutableListOf<ToolCall>()
        var finalSummary: String? = null

        content.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("PLAN:", ignoreCase = true) -> {
                    val payload = line.substringAfter("PLAN:", "").trim()
                    if (payload.isNotBlank()) plans += payload
                }
                line.startsWith("REFLECT:", ignoreCase = true) -> {
                    val payload = line.substringAfter("REFLECT:", "").trim()
                    if (payload.isNotBlank()) reflections += payload
                }
                line.startsWith("TOOL:", ignoreCase = true) -> {
                    val payload = line.substringAfter("TOOL:", "").trim()
                    if (payload.isNotBlank()) {
                        val parts = payload.split(" ", limit = 2)
                        toolCalls += ToolCall(parts[0], parts.getOrElse(1) { "" })
                    }
                }
                line.startsWith("FINAL:", ignoreCase = true) -> {
                    finalSummary = line.substringAfter("FINAL:", "").trim().ifBlank { null }
                }
                line.startsWith("{") && line.contains("\"tool\"") -> {
                    val tool = extractJsonString(line, "tool")
                    if (!tool.isNullOrBlank()) {
                        toolCalls += ToolCall(tool, extractJsonString(line, "args").orEmpty())
                    }
                }
                line.startsWith("TOOLS:", ignoreCase = true) -> {
                    val payload = line.substringAfter("TOOLS:", "").trim()
                    toolCalls += parseToolArray(payload)
                }
                line.startsWith("[") && line.contains("\"tool\"") -> {
                    toolCalls += parseToolArray(line)
                }
                line.startsWith("{") && line.contains("\"final\"") -> {
                    finalSummary = extractJsonString(line, "final")
                }
            }
        }

        return AgentModelStep(
            plans = plans,
            reflections = reflections,
            toolCalls = toolCalls,
            finalSummary = finalSummary,
        )
    }

    private fun extractJsonString(input: String, key: String): String? {
        val marker = "\"$key\""
        val keyIdx = input.indexOf(marker)
        if (keyIdx < 0) return null
        val colon = input.indexOf(':', keyIdx + marker.length)
        if (colon < 0) return null
        val quoteStart = input.indexOf('"', colon + 1)
        if (quoteStart < 0) return null
        var i = quoteStart + 1
        val sb = StringBuilder()
        var escaped = false
        while (i < input.length) {
            val c = input[i]
            if (escaped) {
                sb.append(c)
                escaped = false
                i++
                continue
            }
            if (c == '\\') {
                escaped = true
                i++
                continue
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun parseToolArray(payload: String): List<ToolCall> {
        if (payload.isBlank()) return emptyList()
        val entries = Regex("\\{[^{}]*\"tool\"\\s*:\\s*\"[^\"]+\"[^{}]*}")
            .findAll(payload)
            .map { it.value }
            .toList()
        if (entries.isEmpty()) return emptyList()
        return entries.mapNotNull { item ->
            val tool = extractJsonString(item, "tool")
            if (tool.isNullOrBlank()) {
                null
            } else {
                ToolCall(tool, extractJsonString(item, "args").orEmpty())
            }
        }
    }
}
