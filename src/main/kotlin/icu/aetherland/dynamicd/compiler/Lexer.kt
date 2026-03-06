package icu.aetherland.dynamicd.compiler

enum class TokenType {
    IDENTIFIER,
    KEYWORD,
    STRING,
    NUMBER,
    SYMBOL,
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val line: Int,
    val column: Int,
)

class LexerException(
    val code: String,
    val errorLine: Int,
    val errorColumn: Int,
    message: String,
) : RuntimeException(message)

object Lexer {
    private val keywords = setOf(
        "module",
        "version",
        "use",
        "export",
        "as",
        "let",
        "var",
        "const",
        "state",
        "persist",
        "fn",
        "on",
        "where",
        "throttle",
        "command",
        "permission",
        "every",
        "after",
        "async",
        "sync",
        "placeholder",
        "namespace",
        "papi",
    )

    fun tokenize(source: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var line = 1
        var col = 1
        var blockCommentDepth = 0

        fun advance(): Char {
            val ch = source[i++]
            if (ch == '\n') {
                line++
                col = 1
            } else {
                col++
            }
            return ch
        }

        fun peek(offset: Int = 0): Char? = source.getOrNull(i + offset)

        while (i < source.length) {
            val ch = peek() ?: break

            if (blockCommentDepth > 0) {
                if (ch == '/' && peek(1) == '*') {
                    throw LexerException(
                        code = "E0002",
                        errorLine = line,
                        errorColumn = col,
                        message = "Nested block comments are not allowed",
                    )
                }
                if (ch == '*' && peek(1) == '/') {
                    advance()
                    advance()
                    blockCommentDepth--
                    continue
                }
                advance()
                continue
            }

            if (ch.isWhitespace()) {
                advance()
                continue
            }
            if (ch == '/' && peek(1) == '/') {
                while (peek() != null && peek() != '\n') {
                    advance()
                }
                continue
            }
            if (ch == '/' && peek(1) == '*') {
                blockCommentDepth++
                advance()
                advance()
                continue
            }

            val startLine = line
            val startCol = col

            if (ch == '"') {
                advance()
                val sb = StringBuilder()
                var escaped = false
                while (true) {
                    val next = peek()
                    if (next == null) {
                        throw LexerException(
                            code = "E0003",
                            errorLine = startLine,
                            errorColumn = startCol,
                            message = "Unterminated string literal",
                        )
                    }
                    if (escaped) {
                        sb.append(advance())
                        escaped = false
                        continue
                    }
                    if (next == '\\') {
                        escaped = true
                        sb.append(advance())
                        continue
                    }
                    if (next == '"') {
                        advance()
                        break
                    }
                    sb.append(advance())
                }
                tokens += Token(TokenType.STRING, "\"$sb\"", startLine, startCol)
                continue
            }

            if (ch.isLetter() || ch == '_') {
                val sb = StringBuilder()
                while (true) {
                    val c = peek() ?: break
                    if (c.isLetterOrDigit() || c == '_' || c == '.' || c == ':') {
                        sb.append(advance())
                    } else {
                        break
                    }
                }
                val lexeme = sb.toString()
                val type = if (lexeme in keywords) TokenType.KEYWORD else TokenType.IDENTIFIER
                tokens += Token(type, lexeme, startLine, startCol)
                continue
            }

            if (ch.isDigit()) {
                val sb = StringBuilder()
                while (peek()?.isDigit() == true) {
                    sb.append(advance())
                }
                if (peek() == '.' && peek(1)?.isDigit() == true) {
                    sb.append(advance())
                    while (peek()?.isDigit() == true) {
                        sb.append(advance())
                    }
                }
                if (peek()?.let { it in setOf('t', 's', 'm', 'h', 'd') } == true) {
                    sb.append(advance())
                }
                tokens += Token(TokenType.NUMBER, sb.toString(), startLine, startCol)
                continue
            }

            val symbol = advance().toString()
            tokens += Token(TokenType.SYMBOL, symbol, startLine, startCol)
        }

        if (blockCommentDepth > 0) {
            throw LexerException(
                code = "E0004",
                errorLine = line,
                errorColumn = col,
                message = "Unterminated block comment",
            )
        }
        return tokens
    }
}
