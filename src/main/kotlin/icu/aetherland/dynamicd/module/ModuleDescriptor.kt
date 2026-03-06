package icu.aetherland.dynamicd.module

import icu.aetherland.dynamicd.compiler.CompileResult
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitTask
import java.io.File

data class ModuleDescriptor(
    val id: String,
    val directory: File,
    var state: ModuleState = ModuleState.PREPARED,
    var lastCompileResult: CompileResult? = null,
    val listeners: MutableList<Listener> = mutableListOf(),
    val tasks: MutableList<BukkitTask> = mutableListOf(),
)
