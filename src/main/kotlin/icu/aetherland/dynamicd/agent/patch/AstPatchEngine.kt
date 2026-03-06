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

        val normalized = request.instruction.trim().lowercase()
        val updated = when {
            normalized.startsWith("append event ") -> {
                val event = request.instruction.removePrefix("append event ").trim()
                "$source\non $event {\n}\n"
            }
            normalized.startsWith("append command ") -> {
                val command = request.instruction.removePrefix("append command ").trim()
                "$source\ncommand \"$command\" {\n}\n"
            }
            normalized.startsWith("replace ") -> {
                val payload = request.instruction.removePrefix("replace ").split("=>", limit = 2)
                if (payload.size == 2) source.replace(payload[0].trim(), payload[1].trim()) else source
            }
            else -> "$source\n// ast patch note: ${request.instruction}\n"
        }
        file.writeText(updated)
        return PatchResult(success = true, appliedStrategy = "AST")
    }
}
