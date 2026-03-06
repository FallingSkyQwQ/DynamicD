package icu.aetherland.dynamicd.compiler

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncrementalCompilerTest {
    @Test
    fun `incremental compile reuses unchanged files`() {
        val root = Files.createTempDirectory("dynamicd-incr").toFile()
        File(root, "a.yuz").writeText(
            """
            module "dynamicd:test"
            fn a() {}
            """.trimIndent(),
        )
        File(root, "b.yuz").writeText(
            """
            module "dynamicd:test"
            fn b() {}
            """.trimIndent(),
        )

        val full = CompilerFacade.compileModule("test", root, CompileMode.FULL)
        assertTrue(full.success)
        assertEquals(2, full.metrics.filesCompiled)

        val incNoChange = CompilerFacade.compileModule("test", root, CompileMode.INCREMENTAL)
        assertTrue(incNoChange.success)
        assertEquals(0, incNoChange.metrics.filesCompiled)
        assertEquals(2, incNoChange.metrics.filesReused)

        File(root, "b.yuz").writeText(
            """
            module "dynamicd:test"
            fn b() {}
            fn c() {}
            """.trimIndent(),
        )
        val incOneChange = CompilerFacade.compileModule("test", root, CompileMode.INCREMENTAL)
        assertTrue(incOneChange.success)
        assertEquals(1, incOneChange.metrics.filesCompiled)
        assertEquals(1, incOneChange.metrics.filesReused)
    }
}
