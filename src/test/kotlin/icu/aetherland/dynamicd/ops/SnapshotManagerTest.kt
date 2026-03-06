package icu.aetherland.dynamicd.ops

import icu.aetherland.dynamicd.module.ModuleDescriptor
import icu.aetherland.dynamicd.module.ModuleState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotManagerTest {
    @Test
    fun `create read and list snapshots`() {
        val root = Files.createTempDirectory("dynamicd-snap").toFile()
        val manager = SnapshotManager(File(root, "snapshots"))
        val modules = listOf(
            ModuleDescriptor("a", File(root, "a"), state = ModuleState.ENABLED),
            ModuleDescriptor("b", File(root, "b"), state = ModuleState.DISABLED),
        )
        val id = manager.createSnapshot(modules)
        val listed = manager.listSnapshots()
        assertTrue(id in listed)
        val data = manager.readSnapshot(id)
        assertEquals(true, data["a"])
        assertEquals(false, data["b"])
    }
}
