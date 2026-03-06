package icu.aetherland.dynamicd.compiler

enum class DiagnosticLevel {
    ERROR,
    WARNING,
    INFO,
}

enum class DiagnosticStage {
    LEXER,
    PARSER,
    RESOLVE,
    TYPE,
    EFFECT,
    RUNTIME,
    SECURITY,
}

data class Diagnostic(
    val code: String,
    val level: DiagnosticLevel,
    val stage: DiagnosticStage,
    val message: String,
    val file: String,
    val line: Int,
    val column: Int,
    val expected: String? = null,
    val actual: String? = null,
    val suggestion: String? = null,
    val contextSnippet: String? = null,
)
