package icu.aetherland.dynamicd.agent.loop

class TaskDecomposer {
    fun decompose(prompt: String, limit: Int = 5): List<String> {
        val normalized = prompt
            .replace("，", ",")
            .replace("。", ".")
            .replace("；", ";")
            .replace("并且", " and ")
            .replace("然后", " and ")
        val pieces = normalized
            .split(Regex("\\s+and\\s+|,|;|\\."))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.replace(Regex("\\s+"), " ") }
        if (pieces.isEmpty()) {
            return emptyList()
        }
        return pieces.take(limit)
    }
}
