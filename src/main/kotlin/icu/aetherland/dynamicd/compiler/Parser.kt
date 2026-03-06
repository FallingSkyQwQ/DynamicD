package icu.aetherland.dynamicd.compiler

object Parser {
    fun parse(source: String): AstModule {
        var moduleName: String? = null
        var versionLiteral: String? = null
        val declarations = mutableListOf<AstDeclaration>()
        val lines = source.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isBlank() || line.startsWith("//")) {
                i++
                continue
            }

            when {
                line.startsWith("module ") -> {
                    moduleName = Regex("module\\s+\"([^\"]+)\"")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                }
                line.startsWith("version ") -> {
                    versionLiteral = Regex("version\\s+\"([^\"]+)\"")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                }
                line.startsWith("use ") -> {
                    val path = Regex("use\\s+([^\\s]+)")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                    val alias = Regex("\\sas\\s+(\\w+)")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                    if (!path.isNullOrBlank()) {
                        declarations += UseDeclaration(path = path, alias = alias)
                    }
                }
                line.startsWith("record ") -> {
                    val name = Regex("record\\s+(\\w+)").find(line)?.groupValues?.getOrNull(1)
                    if (!name.isNullOrBlank()) {
                        declarations += RecordDeclaration(name)
                    }
                }
                line.startsWith("enum ") -> {
                    val name = Regex("enum\\s+(\\w+)").find(line)?.groupValues?.getOrNull(1)
                    if (!name.isNullOrBlank()) {
                        val variants = mutableListOf<String>()
                        val end = findBlockEndIndex(lines, i)
                        var j = i + 1
                        while (j <= end && j < lines.size) {
                            val raw = lines[j].trim().removeSuffix(",")
                            if (raw.startsWith("}")) break
                            val variant = Regex("^(\\w+)$").find(raw)?.groupValues?.getOrNull(1)
                            if (!variant.isNullOrBlank()) {
                                variants += variant
                            }
                            j++
                        }
                        declarations += EnumDeclaration(name, variants)
                        i = end
                    }
                }
                line.startsWith("trait ") -> {
                    val name = Regex("trait\\s+(\\w+)").find(line)?.groupValues?.getOrNull(1)
                    if (!name.isNullOrBlank()) {
                        declarations += TraitDeclaration(name, collectFunctionSignatures(lines, i))
                        i = findBlockEndIndex(lines, i)
                    }
                }
                line.startsWith("impl ") -> {
                    val m = Regex("impl\\s+(\\w+)\\s+for\\s+(\\w+)").find(line)
                    if (m != null) {
                        declarations += ImplDeclaration(
                            traitName = m.groupValues[1],
                            targetType = m.groupValues[2],
                            methods = collectFunctionSignatures(lines, i),
                        )
                        i = findBlockEndIndex(lines, i)
                    }
                }
                line.startsWith("match ") -> {
                    val targetExpression = line.removePrefix("match ").substringBefore("{").trim()
                    val details = collectMatchDetails(lines, i)
                    declarations += MatchDeclaration(
                        targetExpression = targetExpression,
                        hasElseBranch = details.hasElse,
                        caseCount = details.caseLabels.size,
                        caseLabels = details.caseLabels,
                    )
                    i = findBlockEndIndex(lines, i)
                }
                line.startsWith("export fn ") || line.startsWith("fn ") -> {
                    val exported = line.startsWith("export fn ")
                    val m = Regex("(export\\s+)?fn\\s+(\\w+)\\s*\\(").find(line)
                    val fn = m?.groupValues?.getOrNull(2)
                    if (!fn.isNullOrBlank()) {
                        val signature = parseFunctionSignature(line) ?: FunctionSignature(fn, emptyList(), null)
                        declarations += FunctionDeclaration(name = fn, exported = exported, signature = signature)
                    }
                }
                line.startsWith("state ") || line.startsWith("persist ") -> {
                    val persistent = line.startsWith("persist ")
                    val m = Regex("(state|persist)\\s+(\\w+)\\s*:").find(line)
                    val name = m?.groupValues?.getOrNull(2)
                    if (!name.isNullOrBlank()) {
                        declarations += StateDeclaration(name = name, persistent = persistent)
                    }
                }
                line.startsWith("placeholder ") -> {
                    val key = Regex("placeholder\\s+\"([^\"]+)\"")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                    if (!key.isNullOrBlank()) {
                        declarations += PlaceholderDeclaration(namespace = null, key = key)
                    }
                }
                line.startsWith("papi namespace ") -> {
                    val namespace = Regex("papi\\s+namespace\\s+\"([^\"]+)\"")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                    if (!namespace.isNullOrBlank()) {
                        declarations += PlaceholderDeclaration(namespace = namespace, key = "*")
                    }
                }
                line.startsWith("on ") -> {
                    val path = line.removePrefix("on ").substringBefore(" where").substringBefore(" throttle")
                        .substringBefore(" priority").substringBefore(" ignoreCancelled").substringBefore("{").trim()
                    val where = Regex("\\bwhere\\b\\s+(.+?)(\\s+throttle\\b|\\s+priority\\b|\\s+ignoreCancelled\\b|\\{|$)")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()
                    val throttle = Regex("\\bthrottle\\b\\s+([0-9]+[tsmhd])")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                    if (path.isNotBlank()) {
                        declarations.add(EventDeclaration(path, whereClause = where, throttleLiteral = throttle))
                    }
                }
                line.startsWith("command ") -> declarations.add(CommandDeclaration(line))
                line.startsWith("permission ") -> {
                    val node = Regex("permission\\s+\"([^\"]+)\"")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                    if (!node.isNullOrBlank()) {
                        declarations.add(PermissionDeclaration(node))
                    }
                }
                line.startsWith("every ") -> {
                    val duration = line.removePrefix("every ").substringBefore(" ").trim()
                    declarations.add(TimerDeclaration("every", duration))
                }
                line.startsWith("after ") -> {
                    val duration = line.removePrefix("after ").substringBefore(" ").trim()
                    declarations.add(TimerDeclaration("after", duration))
                }
            }
            i++
        }

        return AstModule(
            moduleName = moduleName,
            versionLiteral = versionLiteral,
            declarations = declarations,
        )
    }

    private fun collectFunctionSignatures(lines: List<String>, startIndex: Int): List<FunctionSignature> {
        var depth = 0
        var i = startIndex
        val methods = mutableListOf<FunctionSignature>()
        while (i < lines.size) {
            val line = lines[i]
            if (line.contains("{")) {
                depth += line.count { it == '{' }
            }
            val signature = parseFunctionSignature(line)
            if (signature != null) {
                methods += signature
            }
            if (line.contains("}")) {
                depth -= line.count { it == '}' }
                if (depth <= 0 && i > startIndex) {
                    break
                }
            }
            i++
        }
        return methods.distinctBy { it.name }
    }

    private fun findBlockEndIndex(lines: List<String>, startIndex: Int): Int {
        var depth = 0
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i]
            if (line.contains("{")) {
                depth += line.count { it == '{' }
            }
            if (line.contains("}")) {
                depth -= line.count { it == '}' }
                if (depth <= 0 && i > startIndex) {
                    return i
                }
            }
            i++
        }
        return startIndex
    }

    private fun collectMatchDetails(lines: List<String>, startIndex: Int): MatchDetails {
        var depth = 0
        var hasElse = false
        var i = startIndex
        val caseLabels = mutableListOf<String>()
        while (i < lines.size) {
            val line = lines[i]
            if (line.contains("{")) {
                depth += line.count { it == '{' }
            }
            val caseLabel = Regex("\\bcase\\s+([^=\\s]+)\\s*=>").find(line)?.groupValues?.getOrNull(1)
            if (!caseLabel.isNullOrBlank()) {
                caseLabels += caseLabel
            }
            if (Regex("\\belse\\b").containsMatchIn(line)) {
                hasElse = true
            }
            if (line.contains("}")) {
                depth -= line.count { it == '}' }
                if (depth <= 0 && i > startIndex) {
                    break
                }
            }
            i++
        }
        return MatchDetails(hasElse = hasElse, caseLabels = caseLabels.distinct())
    }

    private data class MatchDetails(
        val hasElse: Boolean,
        val caseLabels: List<String>,
    )

    private fun parseFunctionSignature(line: String): FunctionSignature? {
        val m = Regex("\\bfn\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*(->\\s*([^\\s{]+))?").find(line) ?: return null
        val name = m.groupValues[1]
        val paramsRaw = m.groupValues[2].trim()
        val paramTypes = if (paramsRaw.isBlank()) {
            emptyList()
        } else {
            paramsRaw.split(",")
                .mapNotNull { param ->
                    val p = param.trim()
                    if (p.isBlank()) {
                        null
                    } else {
                        Regex(":\\s*([A-Za-z_][A-Za-z0-9_<>?]*)")
                            .find(p)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?: "Any"
                    }
                }
        }
        val returnType = m.groupValues.getOrNull(4)?.trim()?.ifBlank { null }
        return FunctionSignature(name = name, paramTypes = paramTypes, returnType = returnType)
    }
}
