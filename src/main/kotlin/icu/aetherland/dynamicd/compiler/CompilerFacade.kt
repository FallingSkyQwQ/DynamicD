package icu.aetherland.dynamicd.compiler

import java.io.File
import kotlin.system.measureTimeMillis

object CompilerFacade {
    private val incrementalCache = IncrementalCompilerCache()
    private val eventPredicateCompiler = EventPredicateCompiler()

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
        val dependencies = mutableListOf<String>()
        val records = mutableListOf<String>()
        val enums = mutableListOf<String>()
        val traits = mutableListOf<String>()
        var compiledPredicates = 0
        var throttledEvents = 0

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
                    dependencies.addAll(cached.dependencies)
                    records.addAll(cached.records)
                    enums.addAll(cached.enums)
                    traits.addAll(cached.traits)
                    compiledPredicates += cached.compiledPredicates
                    throttledEvents += cached.throttledEvents
                    return@forEach
                }

                filesCompiled++
                val ast = Parser.parse(source)
                val stageDiagnostics = runChecks(file, source, ast)
                diagnostics.addAll(stageDiagnostics)
                val eventDecls = ast.declarations.filterIsInstance<EventDeclaration>()
                val events = eventDecls.map { it.eventPath }
                eventDecls.forEach { decl ->
                    val where = decl.whereClause
                    if (!where.isNullOrBlank()) {
                        eventPredicateCompiler.compile(where)
                        compiledPredicates++
                    }
                    if (!decl.throttleLiteral.isNullOrBlank()) {
                        throttledEvents++
                    }
                }
                val commands = ast.declarations.filterIsInstance<CommandDeclaration>().map { it.raw }
                val permissions = ast.declarations.filterIsInstance<PermissionDeclaration>().map { it.node }
                val timers = ast.declarations.filterIsInstance<TimerDeclaration>().map { "${it.timerType}:${it.durationLiteral}" }
                val placeholders = extractPlaceholders(ast)
                val integrations = extractIntegrations(ast)
                val functions = ast.declarations
                    .filterIsInstance<FunctionDeclaration>()
                    .filter { it.exported }
                    .map { it.name }
                val deps = ast.declarations.filterIsInstance<UseDeclaration>()
                    .map { it.path }
                    .filter { it.startsWith("dynamicd:") }
                    .map { it.substringAfter("dynamicd:") }
                val declaredRecords = ast.declarations.filterIsInstance<RecordDeclaration>().map { it.name }
                val declaredEnums = ast.declarations.filterIsInstance<EnumDeclaration>().map { it.name }
                val declaredTraits = ast.declarations.filterIsInstance<TraitDeclaration>().map { it.name }

                allEvents.addAll(events)
                allCommands.addAll(commands)
                allPermissions.addAll(permissions)
                allTimers.addAll(timers)
                allPlaceholders.addAll(placeholders)
                requiredIntegrations.addAll(integrations)
                exportedFunctions.addAll(functions)
                dependencies.addAll(deps)
                records.addAll(declaredRecords)
                enums.addAll(declaredEnums)
                traits.addAll(declaredTraits)

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
                        dependencies = deps,
                        records = declaredRecords,
                        enums = declaredEnums,
                        traits = declaredTraits,
                        compiledPredicates = eventDecls.count { !it.whereClause.isNullOrBlank() },
                        throttledEvents = eventDecls.count { !it.throttleLiteral.isNullOrBlank() },
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
                dependencyImports = dependencies.distinct(),
            ),
            symbolIndex = SymbolIndex(
                moduleId = moduleId,
                exportedFunctions = exportedFunctions.distinct(),
                events = allEvents.distinct(),
                commands = allCommands.distinct(),
                dependencies = dependencies.distinct(),
                records = records.distinct(),
                enums = enums.distinct(),
                traits = traits.distinct(),
            ),
            metrics = CompileMetrics(
                mode = mode,
                totalMillis = elapsed,
                filesCompiled = filesCompiled,
                filesReused = filesReused,
                compiledPredicates = compiledPredicates,
                throttledEvents = throttledEvents,
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

    private fun runChecks(file: File, source: String, ast: AstModule): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()

        try {
            Lexer.tokenize(source)
        } catch (ex: LexerException) {
            diagnostics += Diagnostic(
                code = ex.code,
                level = DiagnosticLevel.ERROR,
                stage = DiagnosticStage.LEXER,
                message = ex.message ?: "Lexer failure",
                file = file.name,
                line = ex.errorLine,
                column = ex.errorColumn,
                contextSnippet = source.lines().firstOrNull(),
                suggestion = "Check illegal token characters",
            )
            return diagnostics
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
        diagnostics += SemanticAnalyzer.analyze(file, source, ast)
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
                dependencyImports = emptyList(),
            ),
            symbolIndex = SymbolIndex(
                moduleId = moduleId,
                exportedFunctions = emptyList(),
                events = emptyList(),
                commands = emptyList(),
                dependencies = emptyList(),
                records = emptyList(),
                enums = emptyList(),
                traits = emptyList(),
            ),
            metrics = CompileMetrics(
                mode = mode,
                totalMillis = 0,
                filesCompiled = 0,
                filesReused = 0,
                compiledPredicates = 0,
                throttledEvents = 0,
            ),
        )
    }

    private fun extractPlaceholders(ast: AstModule): List<String> {
        return ast.declarations.filterIsInstance<PlaceholderDeclaration>().mapNotNull { decl ->
            when {
                !decl.namespace.isNullOrBlank() -> "${decl.namespace}:*"
                !decl.key.isNullOrBlank() -> decl.key
                else -> null
            }
        }
    }

    private fun extractIntegrations(ast: AstModule): Set<String> {
        val found = mutableSetOf<String>()
        val usePaths = ast.declarations.filterIsInstance<UseDeclaration>().map { it.path.lowercase() }
        val hasPlaceholderDecl = ast.declarations.any { it is PlaceholderDeclaration }
        if (hasPlaceholderDecl || usePaths.any { it.contains("papi") || it.contains("placeholder") }) {
            found += "PlaceholderAPI"
        }
        if (usePaths.any { it.contains("luckperms") }) {
            found += "LuckPerms"
        }
        if (usePaths.any { it.contains("vault") }) {
            found += "Vault"
        }
        return found
    }
}
