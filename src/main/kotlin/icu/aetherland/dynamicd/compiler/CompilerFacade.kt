package icu.aetherland.dynamicd.compiler

import java.io.File
import kotlin.system.measureTimeMillis

object CompilerFacade {
    private val incrementalCache = IncrementalCompilerCache()

    fun compileModule(
        moduleId: String,
        moduleDir: File,
        mode: CompileMode = CompileMode.FULL,
    ): CompileResult {
        val sourceFiles = moduleDir.walkTopDown()
            .filter { it.isFile && it.extension == "yuz" }
            .sortedBy { it.absolutePath }
            .toList()

        if (sourceFiles.isEmpty()) {
            return failure(
                moduleId = moduleId,
                code = "E0100",
                stage = DiagnosticStage.PARSER,
                message = "No .yuz files found for module",
                file = "${moduleDir.name}/",
                line = 1,
                column = 1,
                mode = mode,
            )
        }

        val diagnostics = mutableListOf<Diagnostic>()
        val allEvents = mutableListOf<String>()
        val allCommands = mutableListOf<String>()
        val allPermissions = mutableListOf<String>()
        val allTimers = mutableListOf<String>()
        val allPlaceholders = mutableListOf<String>()
        val requiredIntegrations = mutableSetOf<String>()
        val exportedFunctions = mutableListOf<String>()

        var filesCompiled = 0
        var filesReused = 0
        val elapsed = measureTimeMillis {
            sourceFiles.forEach { file ->
                val source = file.readText()
                val hash = incrementalCache.hash(source)
                val cached = if (mode == CompileMode.INCREMENTAL) incrementalCache.get(file, hash) else null
                if (cached != null) {
                    filesReused++
                    diagnostics.addAll(cached.diagnostics)
                    allEvents.addAll(cached.events)
                    allCommands.addAll(cached.commands)
                    allPermissions.addAll(cached.permissions)
                    allTimers.addAll(cached.timers)
                    allPlaceholders.addAll(cached.placeholders)
                    requiredIntegrations.addAll(cached.integrations)
                    exportedFunctions.addAll(cached.exportedFunctions)
                    return@forEach
                }

                filesCompiled++
                val stageDiagnostics = runChecks(file, source)
                diagnostics.addAll(stageDiagnostics)
                val ast = Parser.parse(source)
                val events = ast.declarations.filterIsInstance<EventDeclaration>().map { it.eventPath }
                val commands = ast.declarations.filterIsInstance<CommandDeclaration>().map { it.raw }
                val permissions = ast.declarations.filterIsInstance<PermissionDeclaration>().map { it.node }
                val timers = ast.declarations.filterIsInstance<TimerDeclaration>().map { "${it.timerType}:${it.durationLiteral}" }
                val placeholders = extractPlaceholders(source)
                val integrations = extractIntegrations(source)
                val functions = extractExportedFunctions(source)

                allEvents.addAll(events)
                allCommands.addAll(commands)
                allPermissions.addAll(permissions)
                allTimers.addAll(timers)
                allPlaceholders.addAll(placeholders)
                requiredIntegrations.addAll(integrations)
                exportedFunctions.addAll(functions)

                incrementalCache.put(
                    moduleId,
                    file,
                    hash,
                    CachedFileAnalysis(
                        hash = hash,
                        events = events,
                        commands = commands,
                        permissions = permissions,
                        timers = timers,
                        placeholders = placeholders,
                        integrations = integrations,
                        diagnostics = stageDiagnostics,
                        exportedFunctions = functions,
                    ),
                )
            }
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
                placeholders = allPlaceholders.distinct(),
                requiredIntegrations = requiredIntegrations,
            ),
            symbolIndex = SymbolIndex(
                moduleId = moduleId,
                exportedFunctions = exportedFunctions.distinct(),
                events = allEvents.distinct(),
                commands = allCommands.distinct(),
            ),
            metrics = CompileMetrics(
                mode = mode,
                totalMillis = elapsed,
                filesCompiled = filesCompiled,
                filesReused = filesReused,
            ),
        )
    }

    fun diagnose(moduleId: String, moduleDir: File, mode: CompileMode = CompileMode.INCREMENTAL): List<Diagnostic> {
        return compileModule(moduleId, moduleDir, mode).diagnostics
    }

    fun buildSymbolIndex(moduleId: String, moduleDir: File, mode: CompileMode = CompileMode.INCREMENTAL): SymbolIndex {
        return compileModule(moduleId, moduleDir, mode).symbolIndex
    }

    fun clearModuleCache(moduleId: String) {
        incrementalCache.clearModule(moduleId)
    }

    private fun runChecks(file: File, source: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()

        try {
            Lexer.tokenize(source)
        } catch (ex: Exception) {
            diagnostics += Diagnostic(
                code = "E0001",
                level = DiagnosticLevel.ERROR,
                stage = DiagnosticStage.LEXER,
                message = ex.message ?: "Lexer failure",
                file = file.name,
                line = 1,
                column = 1,
                contextSnippet = source.lines().firstOrNull(),
                suggestion = "Check illegal token characters",
            )
            return diagnostics
        }

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

    private fun failure(
        moduleId: String,
        code: String,
        stage: DiagnosticStage,
        message: String,
        file: String,
        line: Int,
        column: Int,
        mode: CompileMode,
    ): CompileResult {
        return CompileResult(
            moduleId = moduleId,
            success = false,
            diagnostics = listOf(
                Diagnostic(
                    code = code,
                    level = DiagnosticLevel.ERROR,
                    stage = stage,
                    message = message,
                    file = file,
                    line = line,
                    column = column,
                    contextSnippet = null,
                ),
            ),
            registry = CompileRegistry(
                events = emptyList(),
                commands = emptyList(),
                permissions = emptyList(),
                timers = emptyList(),
                placeholders = emptyList(),
                requiredIntegrations = emptySet(),
            ),
            symbolIndex = SymbolIndex(moduleId, emptyList(), emptyList(), emptyList()),
            metrics = CompileMetrics(mode = mode, totalMillis = 0, filesCompiled = 0, filesReused = 0),
        )
    }

    private fun extractPlaceholders(source: String): List<String> {
        val placeholders = mutableListOf<String>()
        source.lines().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("placeholder ")) {
                val name = Regex("placeholder\\s+\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                if (!name.isNullOrBlank()) {
                    placeholders += name
                }
            }
            if (line.startsWith("papi namespace ")) {
                val namespace = Regex("papi\\s+namespace\\s+\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                if (!namespace.isNullOrBlank()) {
                    placeholders += "$namespace:*"
                }
            }
        }
        return placeholders
    }

    private fun extractIntegrations(source: String): Set<String> {
        val found = mutableSetOf<String>()
        val lowered = source.lowercase()
        if ("papi " in lowered || "placeholder " in lowered) {
            found += "PlaceholderAPI"
        }
        if ("luckperms." in lowered || "luckperms " in lowered) {
            found += "LuckPerms"
        }
        if ("vault." in lowered || "vault " in lowered) {
            found += "Vault"
        }
        return found
    }

    private fun extractExportedFunctions(source: String): List<String> {
        val result = mutableListOf<String>()
        source.lines().forEach { raw ->
            val line = raw.trim()
            val m = Regex("(export\\s+)?fn\\s+(\\w+)\\s*\\(").find(line)
            if (m != null) {
                result += m.groupValues[2]
            }
        }
        return result
    }
}
