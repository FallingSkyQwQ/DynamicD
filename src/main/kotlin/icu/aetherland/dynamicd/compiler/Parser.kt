package icu.aetherland.dynamicd.compiler

object Parser {
    fun parse(source: String): AstModule {
        var moduleName: String? = null
        val declarations = mutableListOf<AstDeclaration>()
        val lines = source.lines()

        lines.forEach { raw ->
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

        return AstModule(moduleName = moduleName, declarations = declarations)
    }
}
