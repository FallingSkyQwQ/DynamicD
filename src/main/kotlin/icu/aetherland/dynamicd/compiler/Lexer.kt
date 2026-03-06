package icu.aetherland.dynamicd.compiler

data class Token(val value: String, val line: Int, val column: Int)

object Lexer {
    fun tokenize(source: String): List<Token> {
        val tokens = mutableListOf<Token>()
        source.lineSequence().forEachIndexed { lineIndex, line ->
            var cursor = 0
            val trimmed = line.substringBefore("//")
            val split = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
            split.forEach { part ->
                val idx = trimmed.indexOf(part, cursor).coerceAtLeast(cursor)
                tokens.add(Token(part, lineIndex + 1, idx + 1))
                cursor = idx + part.length
            }
        }
        return tokens
    }
}
