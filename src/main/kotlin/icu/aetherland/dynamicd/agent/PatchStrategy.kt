package icu.aetherland.dynamicd.agent

enum class PatchStrategy {
    AST,
    TOKEN,
    TEXT,
}

data class PatchDecision(
    val desired: PatchStrategy,
    val actual: PatchStrategy,
    val downgradeReason: String? = null,
)
