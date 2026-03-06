package icu.aetherland.dynamicd.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LexerTest {
    @Test
    fun `tokenizes string number and identifiers`() {
        val tokens = Lexer.tokenize(
            """
            module "dynamicd:test"
            every 5s { }
            """.trimIndent(),
        )
        assertTrue(tokens.any { it.type == TokenType.KEYWORD && it.lexeme == "module" })
        assertTrue(tokens.any { it.type == TokenType.STRING && it.lexeme == "\"dynamicd:test\"" })
        assertTrue(tokens.any { it.type == TokenType.NUMBER && it.lexeme == "5s" })
    }

    @Test
    fun `rejects nested block comment`() {
        val ex = assertFailsWith<LexerException> {
            Lexer.tokenize("/* outer /* inner */ */")
        }
        assertEquals("E0002", ex.code)
    }

    @Test
    fun `rejects unterminated string`() {
        val ex = assertFailsWith<LexerException> {
            Lexer.tokenize("""module "dynamicd:test""")
        }
        assertEquals("E0003", ex.code)
    }
}
