package icu.aetherland.dynamicd.persist

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistStoreTest {
    @Test
    fun `upsert, query and migrate state`() {
        val root = Files.createTempDirectory("dynamicd-persist").toFile()
        val store = PersistStore(File(root, "persist.db"))
        store.upsert(PersistEntry("m1", "coins", "100", 1))
        val before = store.getModuleState("m1")
        assertEquals(1, before.size)
        assertEquals("100", before.first().value)

        store.migrateModule("m1", 2) { e -> e.copy(value = (e.value.toInt() + 1).toString()) }
        val after = store.getModuleState("m1")
        assertEquals("101", after.first().value)
        assertEquals(2, store.schemaVersion("m1"))
        assertTrue(after.first().schemaVersion == 2)
    }
}
