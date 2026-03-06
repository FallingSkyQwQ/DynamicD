package icu.aetherland.dynamicd.agent.patch

import java.io.File

data class PatchRequest(
    val instruction: String,
)

data class PatchResult(
    val success: Boolean,
    val appliedStrategy: String,
    val conflictReason: String? = null,
)

class AstPatchEngine(
    private val conflictResolver: PatchConflictResolver = PatchConflictResolver(),
) {
    fun apply(file: File, request: PatchRequest): PatchResult {
        val source = file.readText()
        val conflict = conflictResolver.detectConflict(source, request.instruction)
        if (conflict != null) {
            return PatchResult(
                success = false,
                appliedStrategy = "AST",
                conflictReason = conflict,
            )
        }

        val instruction = request.instruction.trim()
        val normalized = instruction.lowercase()
        val updated = when {
            normalized.startsWith("append event ") -> {
                val event = instruction.substringAfter("append event ", "").trim()
                if (Regex("^on\\s+$event\\b", RegexOption.MULTILINE).containsMatchIn(source)) {
                    return PatchResult(success = true, appliedStrategy = "AST")
                }
                "$source\non $event {\n}\n"
            }
            normalized.startsWith("append command ") -> {
                val command = instruction.substringAfter("append command ", "").trim()
                if (Regex("^command\\s+\"${Regex.escape(command)}\"", RegexOption.MULTILINE).containsMatchIn(source)) {
                    return PatchResult(success = true, appliedStrategy = "AST")
                }
                "$source\ncommand \"$command\" {\n}\n"
            }
            normalized.startsWith("upsert use ") -> {
                val useDecl = instruction.substringAfter("upsert use ", "").trim()
                val line = "use $useDecl"
                if (Regex("^${Regex.escape(line)}\\s*$", RegexOption.MULTILINE).containsMatchIn(source)) {
                    source
                } else {
                    insertUse(source, line)
                }
            }
            normalized.startsWith("replace ") -> {
                val payload = instruction.substringAfter("replace ", "").split("=>", limit = 2)
                if (payload.size != 2) {
                    return PatchResult(success = false, appliedStrategy = "AST", conflictReason = "invalid replace payload")
                }
                val from = payload[0].trim()
                val to = payload[1].trim()
                if (from.isBlank() || !source.contains(from)) {
                    return PatchResult(success = false, appliedStrategy = "AST", conflictReason = "replace source not found")
                }
                source.replace(from, to)
            }
            else -> "$source\n// ast patch note: ${request.instruction}\n"
        }
        file.writeText(updated)
        return PatchResult(success = true, appliedStrategy = "AST")
    }

    private fun insertUse(source: String, useLine: String): String {
        val lines = source.lines().toMutableList()
        val moduleIndex = lines.indexOfFirst { it.trim().startsWith("module ") }
        val insertAt = if (moduleIndex >= 0) moduleIndex + 1 else 0
        lines.add(insertAt, useLine)
        return lines.joinToString("\n").let { if (it.endsWith("\n")) it else "$it\n" }
    }
}
