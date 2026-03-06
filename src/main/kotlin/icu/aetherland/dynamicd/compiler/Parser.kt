package icu.aetherland.dynamicd.compiler

object Parser {
    fun parse(source: String): AstModule {
        var moduleName: String? = null
        var versionLiteral: String? = null
        val declarations = mutableListOf<AstDeclaration>()
        source.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("//")) {
                return@forEach
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
        }

        return AstModule(
            moduleName = moduleName,
            versionLiteral = versionLiteral,
            declarations = declarations,
        )
    }
}
