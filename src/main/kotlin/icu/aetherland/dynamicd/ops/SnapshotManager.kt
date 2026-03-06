package icu.aetherland.dynamicd.ops

import icu.aetherland.dynamicd.module.ModuleDescriptor
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class SnapshotManager(private val snapshotDir: File) {
    init {
        snapshotDir.mkdirs()
    }

    fun createSnapshot(modules: Collection<ModuleDescriptor>): String {
        val id = "snapshot-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
            "-" + UUID.randomUUID().toString().take(8)
        val snapshotFile = File(snapshotDir, "$id.snapshot")
        val content = buildString {
            modules.forEach { module ->
                append(module.id)
                append("|")
                append(module.state == icu.aetherland.dynamicd.module.ModuleState.ENABLED)
                appendLine()
            }
        }
        snapshotFile.writeText(content)
        return id
    }

    fun readSnapshot(snapshotId: String): Map<String, Boolean> {
        val file = File(snapshotDir, "$snapshotId.snapshot")
        if (!file.exists()) {
            return emptyMap()
        }
        return file.readLines()
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 2) {
                    null
                } else {
                    parts[0] to parts[1].toBooleanStrictOrNull().let { parsed -> parsed ?: (parts[1] == "ENABLED") }
                }
            }.toMap()
    }

    fun listSnapshots(): List<String> {
        return snapshotDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".snapshot") }
            ?.map { it.name.removeSuffix(".snapshot") }
            ?.sorted()
            .orEmpty()
    }
}
