package icu.aetherland.dynamicd.compiler

import java.io.File

object SemanticAnalyzer {
    fun analyze(file: File, source: String, ast: AstModule): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        diagnostics += checkModuleHeader(file, ast)
        diagnostics += checkDuplicateDeclarations(file, source, ast)
        diagnostics += checkImplBindings(file, ast)
        diagnostics += checkMatchCoverage(file, ast)
        diagnostics += checkResultFlow(file, source)
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
                is RecordDeclaration -> "record:${decl.name}"
                is EnumDeclaration -> "enum:${decl.name}"
                is TraitDeclaration -> "trait:${decl.name}"
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

    private fun checkImplBindings(file: File, ast: AstModule): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val traitMap = ast.declarations.filterIsInstance<TraitDeclaration>().associateBy { it.name }
        val traits = traitMap.keys
        val types = mutableSetOf<String>()
        types += ast.declarations.filterIsInstance<RecordDeclaration>().map { it.name }
        types += ast.declarations.filterIsInstance<EnumDeclaration>().map { it.name }
        types += setOf("Int", "Long", "Float", "Double", "Bool", "String", "Player", "World")
        val implSeen = mutableSetOf<String>()

        ast.declarations.filterIsInstance<ImplDeclaration>().forEach { decl ->
            val implKey = "${decl.traitName}->${decl.targetType}"
            if (!implSeen.add(implKey)) {
                diagnostics += Diagnostic(
                    code = "E0607",
                    level = DiagnosticLevel.ERROR,
                    stage = DiagnosticStage.RESOLVE,
                    message = "Duplicate impl block for $implKey",
                    file = file.name,
                    line = 1,
                    column = 1,
                    expected = "single impl per trait-target pair",
                    actual = "duplicate",
                    suggestion = "Merge duplicate impl blocks",
                )
            }
            if (decl.traitName !in traits) {
                diagnostics += Diagnostic(
                    code = "E0601",
                    level = DiagnosticLevel.ERROR,
                    stage = DiagnosticStage.RESOLVE,
                    message = "Impl references unknown trait `${decl.traitName}`",
                    file = file.name,
                    line = 1,
                    column = 1,
                    expected = "known trait",
                    actual = decl.traitName,
                    suggestion = "Declare trait `${decl.traitName}` before impl",
                )
            }
            if (decl.targetType !in types) {
                diagnostics += Diagnostic(
                    code = "E0602",
                    level = DiagnosticLevel.ERROR,
                    stage = DiagnosticStage.TYPE,
                    message = "Impl target type `${decl.targetType}` is not defined",
                    file = file.name,
                    line = 1,
                    column = 1,
                    expected = "known type",
                    actual = decl.targetType,
                    suggestion = "Declare record/enum `${decl.targetType}` or use a builtin type",
                )
            }
            val traitMethods = traitMap[decl.traitName]?.methods.orEmpty().toSet()
            if (traitMethods.isNotEmpty()) {
                val implMethods = decl.methods.toSet()
                val missing = traitMethods
                    .filter { required -> implMethods.none { actual -> actual.name == required.name } }
                    .map { it.name }
                    .sorted()
                if (missing.isNotEmpty()) {
                    diagnostics += Diagnostic(
                        code = "E0605",
                        level = DiagnosticLevel.ERROR,
                        stage = DiagnosticStage.TYPE,
                        message = "Impl for `${decl.traitName}` missing methods: ${missing.joinToString(",")}",
                        file = file.name,
                        line = 1,
                        column = 1,
                        expected = traitMethods.joinToString(","),
                        actual = implMethods.joinToString(","),
                        suggestion = "Implement missing trait methods in impl block",
                    )
                }
                val extra = implMethods
                    .filter { actual -> traitMethods.none { required -> required.name == actual.name } }
                    .map { it.name }
                    .sorted()
                if (extra.isNotEmpty()) {
                    diagnostics += Diagnostic(
                        code = "W0606",
                        level = DiagnosticLevel.WARNING,
                        stage = DiagnosticStage.TYPE,
                        message = "Impl has extra methods not in trait `${decl.traitName}`: ${extra.joinToString(",")}",
                        file = file.name,
                        line = 1,
                        column = 1,
                        suggestion = "Remove extra methods or update trait definition",
                    )
                }
                val mismatched = mutableListOf<String>()
                traitMethods.forEach { required ->
                    val actual = implMethods.firstOrNull { it.name == required.name } ?: return@forEach
                    val sameReturn = actual.returnType == required.returnType
                    val sameParams = actual.paramTypes == required.paramTypes
                    if (!sameReturn || !sameParams) {
                        mismatched += required.name
                    }
                }
                if (mismatched.isNotEmpty()) {
                    diagnostics += Diagnostic(
                        code = "E0609",
                        level = DiagnosticLevel.ERROR,
                        stage = DiagnosticStage.TYPE,
                        message = "Impl method signature mismatch: ${mismatched.joinToString(",")}",
                        file = file.name,
                        line = 1,
                        column = 1,
                        suggestion = "Align impl method parameters/return types with trait definition",
                    )
                }
            }
        }
        return diagnostics
    }

    private fun checkMatchCoverage(file: File, ast: AstModule): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val enumMap = ast.declarations.filterIsInstance<EnumDeclaration>().associateBy { it.name }
        val inferredTypeMap = inferLocalTypes(file, ast)
        ast.declarations.filterIsInstance<MatchDeclaration>().forEach { decl ->
            if (!decl.hasElseBranch && decl.caseCount == 0) {
                diagnostics += Diagnostic(
                    code = "E0603",
                    level = DiagnosticLevel.ERROR,
                    stage = DiagnosticStage.TYPE,
                    message = "match requires at least one case or else branch",
                    file = file.name,
                    line = 1,
                    column = 1,
                    expected = "case/else branch",
                    actual = "empty match",
                    suggestion = "Add at least one case or an else branch",
                )
                return@forEach
            }

            val enumDecl = enumMap[decl.targetExpression] ?: inferredTypeMap[decl.targetExpression]?.let { enumMap[it] }
            if (enumDecl != null && !decl.hasElseBranch) {
                val missing = enumDecl.variants.filter { it !in decl.caseLabels }
                if (missing.isNotEmpty()) {
                    diagnostics += Diagnostic(
                        code = "E0608",
                        level = DiagnosticLevel.ERROR,
                        stage = DiagnosticStage.TYPE,
                        message = "Non-exhaustive enum match for `${enumDecl.name}`, missing: ${missing.joinToString(",")}",
                        file = file.name,
                        line = 1,
                        column = 1,
                        expected = enumDecl.variants.joinToString(","),
                        actual = decl.caseLabels.joinToString(","),
                        suggestion = "Add missing enum cases or else branch",
                    )
                }
            } else if (!decl.hasElseBranch) {
                diagnostics += Diagnostic(
                    code = "W0604",
                    level = DiagnosticLevel.WARNING,
                    stage = DiagnosticStage.TYPE,
                    message = "match without else may be non-exhaustive",
                    file = file.name,
                    line = 1,
                    column = 1,
                    suggestion = "Add else branch for safer exhaustive behavior",
                )
            }

            val resultCaseSet = decl.caseLabels.map { it.substringBefore("(") }.toSet()
            if (!decl.hasElseBranch && ("ok" in resultCaseSet || "err" in resultCaseSet)) {
                if (!resultCaseSet.containsAll(setOf("ok", "err"))) {
                    diagnostics += Diagnostic(
                        code = "E0703",
                        level = DiagnosticLevel.ERROR,
                        stage = DiagnosticStage.TYPE,
                        message = "Result match must cover both ok and err branches or include else",
                        file = file.name,
                        line = 1,
                        column = 1,
                        expected = "ok + err",
                        actual = resultCaseSet.joinToString(","),
                        suggestion = "Add missing branch or else",
                    )
                }
            }
        }
        return diagnostics
    }

    private fun checkResultFlow(file: File, source: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = source.lines()
        var inResultFn = false
        var fnDepth = 0
        lines.forEachIndexed { idx, raw ->
            val line = raw.trim()
            if (line.startsWith("fn ") || line.startsWith("export fn ")) {
                inResultFn = Regex("->\\s*Result<").containsMatchIn(line)
                fnDepth = line.count { it == '{' } - line.count { it == '}' }
            } else if (fnDepth > 0) {
                fnDepth += line.count { it == '{' }
                fnDepth -= line.count { it == '}' }
                if (fnDepth <= 0) {
                    inResultFn = false
                }
            }
            if (line.contains("?") && !inResultFn) {
                diagnostics += Diagnostic(
                    code = "E0701",
                    level = DiagnosticLevel.ERROR,
                    stage = DiagnosticStage.TYPE,
                    message = "`?` operator requires Result-returning function context",
                    file = file.name,
                    line = idx + 1,
                    column = line.indexOf('?') + 1,
                    expected = "fn ... -> Result<T,E>",
                    actual = "non-Result context",
                    suggestion = "Change function return type to Result<T,E> or handle result explicitly",
                    contextSnippet = raw,
                )
            }
            if (Regex("\\breturn\\s+(ok|err)\\s*\\(").containsMatchIn(line) && !inResultFn) {
                diagnostics += Diagnostic(
                    code = "E0702",
                    level = DiagnosticLevel.ERROR,
                    stage = DiagnosticStage.TYPE,
                    message = "ok/err return requires Result-returning function context",
                    file = file.name,
                    line = idx + 1,
                    column = 1,
                    expected = "fn ... -> Result<T,E>",
                    actual = "non-Result context",
                    suggestion = "Change function return type to Result<T,E> or return plain value",
                    contextSnippet = raw,
                )
            }
        }
        return diagnostics
    }

    private fun inferLocalTypes(file: File, ast: AstModule): Map<String, String> {
        val knownEnums = ast.declarations.filterIsInstance<EnumDeclaration>().map { it.name }.toSet()
        val result = mutableMapOf<String, String>()
        file.readLines().forEach { raw ->
            val line = raw.trim()
            val explicit = Regex("(let|var|state|persist)\\s+(\\w+)\\s*:\\s*([A-Za-z_][A-Za-z0-9_]*)")
                .find(line)
            if (explicit != null) {
                result[explicit.groupValues[2]] = explicit.groupValues[3]
                return@forEach
            }
            val enumCtor = Regex("(let|var)\\s+(\\w+)\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)\\.[A-Za-z_][A-Za-z0-9_]*")
                .find(line)
            if (enumCtor != null) {
                val enumType = enumCtor.groupValues[3]
                if (enumType in knownEnums) {
                    result[enumCtor.groupValues[2]] = enumType
                }
            }
        }
        return result
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
