package icu.aetherland.dynamicd.module

import icu.aetherland.dynamicd.compiler.CompileResult
import icu.aetherland.dynamicd.runtime.ListenerHandle
import icu.aetherland.dynamicd.runtime.TaskHandle
import java.io.File

data class ModuleDescriptor(
    val id: String,
    val directory: File,
    var state: ModuleState = ModuleState.PREPARED,
    var lastCompileResult: CompileResult? = null,
    var lastRuntimeError: String? = null,
    val listeners: MutableList<ListenerHandle> = mutableListOf(),
    val tasks: MutableList<TaskHandle> = mutableListOf(),
    val activeCommands: MutableSet<String> = mutableSetOf(),
)
