package icu.aetherland.dynamicd.agent.loop

import java.io.File
import java.time.Instant

data class AgentMemoryEntry(
    val timestamp: Long,
    val operator: String,
    val prompt: String,
    val success: Boolean,
    val summary: String,
)

class AgentMemoryStore(private val root: File) {
    init {
        root.mkdirs()
    }

    fun append(entry: AgentMemoryEntry) {
        val file = File(root, "agent-memory-${entry.operator}.log")
        val line = listOf(
            entry.timestamp.toString(),
            sanitize(entry.prompt),
            entry.success.toString(),
            sanitize(entry.summary),
        ).joinToString("|")
        file.appendText("$line\n")
    }

    fun recent(operator: String, limit: Int = 5): List<AgentMemoryEntry> {
        val file = File(root, "agent-memory-$operator.log")
        if (!file.exists()) return emptyList()
        return file.readLines()
            .asReversed()
            .mapNotNull { parseLine(operator, it) }
            .take(limit)
            .toList()
            .asReversed()
    }

    private fun parseLine(operator: String, line: String): AgentMemoryEntry? {
        val parts = line.split("|", limit = 4)
        if (parts.size != 4) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val prompt = unsanitize(parts[1])
        val success = parts[2].toBooleanStrictOrNull() ?: false
        val summary = unsanitize(parts[3])
        return AgentMemoryEntry(ts, operator, prompt, success, summary)
    }

    private fun sanitize(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("|", "\\p")
    }

    private fun unsanitize(value: String): String {
        return value
            .replace("\\p", "|")
            .replace("\\r", "\r")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
    }

    companion object {
        fun entry(operator: String, prompt: String, result: AgentTurnResult): AgentMemoryEntry {
            return AgentMemoryEntry(
                timestamp = Instant.now().epochSecond,
                operator = operator,
                prompt = prompt,
                success = result.success,
                summary = result.summary,
            )
        }
    }
}
