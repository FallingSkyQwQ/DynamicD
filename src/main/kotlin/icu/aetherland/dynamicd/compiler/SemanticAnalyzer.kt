package icu.aetherland.dynamicd.compiler

import java.io.File

object SemanticAnalyzer {
    fun analyze(file: File, source: String, ast: AstModule): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        diagnostics += checkModuleHeader(file, ast)
        diagnostics += checkDuplicateDeclarations(file, source, ast)
        diagnostics += checkNullableGuards(file, source)
        diagnostics += checkAsyncEffects(file, source)
        return diagnostics
    }

    private fun checkModuleHeader(file: File, ast: AstModule): List<Diagnostic> {
        if (!ast.moduleName.isNullOrBlank()) {
            return emptyList()
        }
        return listOf(
            Diagnostic(
                code = "E0200",
                level = DiagnosticLevel.ERROR,
                stage = DiagnosticStage.PARSER,
                message = "Module declaration is required",
                file = file.name,
                line = 1,
                column = 1,
                expected = "module \"dynamicd:<name>\"",
                actual = "missing",
                suggestion = "Add module declaration at file top",
            ),
        )
    }

    private fun checkDuplicateDeclarations(file: File, source: String, ast: AstModule): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val moduleDeclCount = source.lines().count { it.trim().startsWith("module ") }
        if (moduleDeclCount > 1) {
            diagnostics += Diagnostic(
                code = "E0201",
                level = DiagnosticLevel.ERROR,
                stage = DiagnosticStage.RESOLVE,
                message = "Duplicate module declaration",
                file = file.name,
                line = 1,
                column = 1,
                expected = "single module declaration",
                actual = "count=$moduleDeclCount",
                suggestion = "Keep one module declaration per file",
            )
        }

        val symbolCounts = mutableMapOf<String, Int>()
        ast.declarations.forEach { decl ->
            val symbol = when (decl) {
                is FunctionDeclaration -> "fn:${decl.name}"
                is StateDeclaration -> "state:${decl.name}"
                else -> null
            }
            if (symbol != null) {
                symbolCounts[symbol] = (symbolCounts[symbol] ?: 0) + 1
            }
        }
        symbolCounts
            .filterValues { it > 1 }
            .forEach { (symbol, count) ->
                diagnostics += Diagnostic(
                    code = "E0202",
                    level = DiagnosticLevel.ERROR,
                    stage = DiagnosticStage.RESOLVE,
                    message = "Duplicate declaration for $symbol",
                    file = file.name,
                    line = 1,
                    column = 1,
                    expected = "single declaration",
                    actual = "count=$count",
                    suggestion = "Rename or merge duplicated declarations",
                )
            }
        return diagnostics
    }

    private fun checkNullableGuards(file: File, source: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = source.lines()
        val nullablePlayerVars = mutableMapOf<String, Int>()
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            val decl = Regex("(let|var)\\s+(\\w+)\\s*:\\s*Player\\?")
                .find(line)
            if (decl != null) {
                nullablePlayerVars[decl.groupValues[2]] = i
            }
        }

        nullablePlayerVars.forEach { (name, declIndex) ->
            val guardRegex = Regex("guard\\s+$name\\s*!=\\s*null")
            var guarded = false
            lines.forEachIndexed { idx, raw ->
                val line = raw.trim()
                if (idx > declIndex && guardRegex.containsMatchIn(line)) {
                    guarded = true
                }
                if (idx > declIndex && line.contains("$name.") && !guarded) {
                    diagnostics.add(
                        Diagnostic(
                            code = "E0401",
                            level = DiagnosticLevel.ERROR,
                            stage = DiagnosticStage.TYPE,
                            message = "Nullable Player must be checked before member access",
                            file = file.name,
                            line = idx + 1,
                            column = line.indexOf("$name.") + 1,
                            expected = "Player non-null",
                            actual = "Player?",
                            suggestion = "Add `guard $name != null else { return }` before access",
                            contextSnippet = raw,
                        ),
                    )
                }
            }
        }
        return diagnostics
    }

    private fun checkAsyncEffects(file: File, source: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = source.lines()
        var asyncDepth = 0
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.startsWith("async")) {
                asyncDepth += line.count { it == '{' }.coerceAtLeast(1)
            }
            if (asyncDepth > 0 && Regex("\\b(teleport|title|broadcast|tell)\\b").containsMatchIn(line)) {
                diagnostics.add(
                    Diagnostic(
                        code = "E0500",
                        level = DiagnosticLevel.ERROR,
                        stage = DiagnosticStage.EFFECT,
                        message = "sync-only API call in async context",
                        file = file.name,
                        line = i + 1,
                        column = 1,
                        expected = "sync block",
                        actual = "async block",
                        suggestion = "Wrap call with `sync { ... }`",
                        contextSnippet = raw,
                    ),
                )
            }
            if (line.contains("}")) {
                asyncDepth = (asyncDepth - line.count { it == '}' }).coerceAtLeast(0)
            }
        }
        return diagnostics
    }
}
