package icu.aetherland.dynamicd.module

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleStateStoreTest {
    @Test
    fun `saves and loads enabled modules`() {
        val root = Files.createTempDirectory("dynamicd-state").toFile()
        val store = ModuleStateStore(File(root, "data/enabled-modules.txt"))
        store.saveEnabledModules(setOf("beta", "alpha"))
        val loaded = store.loadEnabledModules()
        assertEquals(setOf("alpha", "beta"), loaded)
    }
}
