package icu.aetherland.dynamicd.compiler

import java.io.File

object CompilerFacade {
    fun compileModule(moduleId: String, moduleDir: File): CompileResult {
        val sourceFiles = moduleDir.walkTopDown()
            .filter { it.isFile && it.extension == "yuz" }
            .toList()

        if (sourceFiles.isEmpty()) {
            return failure(
                moduleId = moduleId,
                code = "E0100",
                message = "No .yuz files found for module",
                file = "${moduleDir.name}/",
                line = 1,
                column = 1,
            )
        }

        val diagnostics = mutableListOf<Diagnostic>()
        val allEvents = mutableListOf<String>()
        val allCommands = mutableListOf<String>()
        val allPermissions = mutableListOf<String>()
        val allTimers = mutableListOf<String>()

        sourceFiles.forEach { file ->
            val source = file.readText()
            val stageDiagnostics = runChecks(file, source)
            diagnostics.addAll(stageDiagnostics)
            val ast = Parser.parse(source)
            allEvents.addAll(ast.declarations.filterIsInstance<EventDeclaration>().map { it.eventPath })
            allCommands.addAll(ast.declarations.filterIsInstance<CommandDeclaration>().map { it.raw })
            allPermissions.addAll(ast.declarations.filterIsInstance<PermissionDeclaration>().map { it.node })
            allTimers.addAll(ast.declarations.filterIsInstance<TimerDeclaration>().map { "${it.timerType}:${it.durationLiteral}" })
        }

        val success = diagnostics.none { it.level == DiagnosticLevel.ERROR }
        return CompileResult(
            moduleId = moduleId,
            success = success,
            diagnostics = diagnostics,
            registry = CompileRegistry(
                events = allEvents.distinct(),
                commands = allCommands.distinct(),
                permissions = allPermissions.distinct(),
                timers = allTimers.distinct(),
            ),
        )
    }

    private fun runChecks(file: File, source: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()

        Lexer.tokenize(source) // lexical pass placeholder
        val lines = source.lines()

        // DD-YUZ-002: naive nullable misuse check for Player?
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
                            message = "Nullable Player must be checked before member access",
                            file = file.name,
                            line = idx + 1,
                            column = line.indexOf("$name.") + 1,
                            expected = "Player non-null",
                            actual = "Player?",
                            suggestion = "Add `guard $name != null else { return }` before access",
                        ),
                    )
                }
            }
        }

        // DD-YUZ-005: effect check for async block using sync-only API
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
                        message = "sync-only API call in async context",
                        file = file.name,
                        line = i + 1,
                        column = 1,
                        expected = "sync block",
                        actual = "async block",
                        suggestion = "Wrap call with `sync { ... }`",
                    ),
                )
            }
            if (line.contains("}")) {
                asyncDepth = (asyncDepth - line.count { it == '}' }).coerceAtLeast(0)
            }
        }

        return diagnostics
    }

    private fun failure(
        moduleId: String,
        code: String,
        message: String,
        file: String,
        line: Int,
        column: Int,
    ): CompileResult {
        return CompileResult(
            moduleId = moduleId,
            success = false,
            diagnostics = listOf(
                Diagnostic(
                    code = code,
                    level = DiagnosticLevel.ERROR,
                    message = message,
                    file = file,
                    line = line,
                    column = column,
                ),
            ),
            registry = CompileRegistry(
                events = emptyList(),
                commands = emptyList(),
                permissions = emptyList(),
                timers = emptyList(),
            ),
        )
    }
}
