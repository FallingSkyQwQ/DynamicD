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
                        var j = i + 1
                        while (j < lines.size) {
                            val raw = lines[j].trim()
                            if (raw.startsWith("}")) break
                            val variant = Regex("^(\\w+)$").find(raw)?.groupValues?.getOrNull(1)
                            if (!variant.isNullOrBlank()) {
                                variants += variant
                            }
                            j++
                        }
                        declarations += EnumDeclaration(name, variants)
                    }
                }
                line.startsWith("trait ") -> {
                    val name = Regex("trait\\s+(\\w+)").find(line)?.groupValues?.getOrNull(1)
                    if (!name.isNullOrBlank()) {
                        declarations += TraitDeclaration(name)
                    }
                }
                line.startsWith("impl ") -> {
                    val m = Regex("impl\\s+(\\w+)\\s+for\\s+(\\w+)").find(line)
                    if (m != null) {
                        declarations += ImplDeclaration(
                            traitName = m.groupValues[1],
                            targetType = m.groupValues[2],
                        )
                    }
                }
                line.startsWith("match ") -> {
                    val targetExpression = line.removePrefix("match ").substringBefore("{").trim()
                    val details = collectMatchDetails(lines, i)
                    declarations += MatchDeclaration(
                        targetExpression = targetExpression,
                        hasElseBranch = details.first,
                        caseCount = details.second,
                    )
                }
                line.startsWith("export fn ") || line.startsWith("fn ") -> {
                    val exported = line.startsWith("export fn ")
                    val m = Regex("(export\\s+)?fn\\s+(\\w+)\\s*\\(").find(line)
                    val fn = m?.groupValues?.getOrNull(2)
                    if (!fn.isNullOrBlank()) {
                        declarations += FunctionDeclaration(name = fn, exported = exported)
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

    private fun collectMatchDetails(lines: List<String>, startIndex: Int): Pair<Boolean, Int> {
        var depth = 0
        var caseCount = 0
        var hasElse = false
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i]
            if (line.contains("{")) {
                depth += line.count { it == '{' }
            }
            if (line.contains("case ")) {
                caseCount++
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
        return hasElse to caseCount
    }
}
