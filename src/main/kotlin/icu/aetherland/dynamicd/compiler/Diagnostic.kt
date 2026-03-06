package icu.aetherland.dynamicd.compiler

enum class DiagnosticLevel {
    ERROR,
    WARNING,
    INFO,
}

data class Diagnostic(
    val code: String,
    val level: DiagnosticLevel,
    val message: String,
    val file: String,
    val line: Int,
    val column: Int,
    val expected: String? = null,
    val actual: String? = null,
    val suggestion: String? = null,
)
