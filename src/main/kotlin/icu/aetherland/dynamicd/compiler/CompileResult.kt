package icu.aetherland.dynamicd.compiler

data class CompileRegistry(
    val events: List<String>,
    val commands: List<String>,
    val permissions: List<String>,
    val timers: List<String>,
    val placeholders: List<String>,
    val requiredIntegrations: Set<String>,
)

data class CompileResult(
    val moduleId: String,
    val success: Boolean,
    val diagnostics: List<Diagnostic>,
    val registry: CompileRegistry,
    val strategyLevel: String = "AST",
)
