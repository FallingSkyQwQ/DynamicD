package icu.aetherland.dynamicd.compiler

enum class CompileMode {
    FULL,
    INCREMENTAL,
}

data class CompileMetrics(
    val mode: CompileMode,
    val totalMillis: Long,
    val filesCompiled: Int,
    val filesReused: Int,
    val compiledPredicates: Int = 0,
    val throttledEvents: Int = 0,
)

data class CompileRegistry(
    val events: List<String>,
    val commands: List<String>,
    val permissions: List<String>,
    val timers: List<String>,
    val placeholders: List<String>,
    val requiredIntegrations: Set<String>,
    val dependencyImports: List<String> = emptyList(),
)

data class SymbolIndex(
    val moduleId: String,
    val exportedFunctions: List<String>,
    val events: List<String>,
    val commands: List<String>,
    val dependencies: List<String> = emptyList(),
)

data class CompileResult(
    val moduleId: String,
    val success: Boolean,
    val diagnostics: List<Diagnostic>,
    val registry: CompileRegistry,
    val symbolIndex: SymbolIndex,
    val metrics: CompileMetrics,
    val strategyLevel: String = "AST",
)
