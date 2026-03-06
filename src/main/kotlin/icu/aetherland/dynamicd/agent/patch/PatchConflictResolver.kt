package icu.aetherland.dynamicd.agent.patch

class PatchConflictResolver {
    fun detectConflict(source: String, instruction: String): String? {
        val normalized = instruction.lowercase()
        if ("delete module" in normalized) {
            return "module deletion is blocked by policy"
        }
        if ("replace " in normalized && !normalized.contains("=>")) {
            return "replace patch must use `replace <old> => <new>` format"
        }
        if (normalized.startsWith("replace ")) {
            val oldValue = instruction.removePrefix("replace ").split("=>", limit = 2).firstOrNull()?.trim().orEmpty()
            if (oldValue.isNotBlank() && oldValue !in source) {
                return "replace target not found in source"
            }
        }
        return null
    }
}
