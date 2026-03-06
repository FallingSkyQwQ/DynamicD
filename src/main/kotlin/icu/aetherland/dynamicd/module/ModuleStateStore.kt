package icu.aetherland.dynamicd.module

import java.io.File

class ModuleStateStore(private val file: File) {
    init {
        file.parentFile.mkdirs()
    }

    fun loadEnabledModules(): Set<String> {
        if (!file.exists()) {
            return emptySet()
        }
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun saveEnabledModules(moduleIds: Set<String>) {
        file.writeText(moduleIds.sorted().joinToString("\n"))
    }
}
