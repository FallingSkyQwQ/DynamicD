package icu.aetherland.dynamicd.agent.patch

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AstPatchEngineTest {
    @Test
    fun `ast patch applies append event and replace`() {
        val root = Files.createTempDirectory("dynamicd-ast-patch").toFile()
        val file = File(root, "mod.yuz")
        file.writeText("module \"dynamicd:test\"\nfn old() {}\n")
        val engine = AstPatchEngine()

        val appendResult = engine.apply(file, PatchRequest("append event player join"))
        assertTrue(appendResult.success)
        assertTrue(file.readText().contains("on player join"))

        val replaceResult = engine.apply(file, PatchRequest("replace old => new"))
        assertTrue(replaceResult.success)
        assertTrue(file.readText().contains("fn new()"))
    }

    @Test
    fun `ast patch blocks dangerous delete module`() {
        val root = Files.createTempDirectory("dynamicd-ast-patch-conflict").toFile()
        val file = File(root, "mod.yuz")
        file.writeText("module \"dynamicd:test\"\n")
        val engine = AstPatchEngine()
        val result = engine.apply(file, PatchRequest("delete module dynamicd:test"))
        assertFalse(result.success)
        assertTrue(result.conflictReason?.contains("blocked") == true)
    }
}
