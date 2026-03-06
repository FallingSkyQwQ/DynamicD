package icu.aetherland.dynamicd.runtime

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer

interface TaskScheduler {
    fun scheduleEvery(plugin: JavaPlugin, ticks: Long, task: Runnable): TaskHandle?
    fun scheduleAfter(plugin: JavaPlugin, ticks: Long, task: Runnable): TaskHandle?
}

object TaskSchedulerFactory {
    fun create(plugin: JavaPlugin, logger: (String) -> Unit): TaskScheduler {
        val folia = FoliaScheduler.tryCreate(plugin, logger)
        if (folia != null) {
            logger("runtime=scheduler mode=FOLIA")
            return folia
        }
        logger("runtime=scheduler mode=BUKKIT")
        return BukkitTaskScheduler()
    }
}

private class BukkitTaskScheduler : TaskScheduler {
    override fun scheduleEvery(plugin: JavaPlugin, ticks: Long, task: Runnable): TaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, ticks, ticks)
        return BukkitTaskHandle(bukkitTask)
    }

    override fun scheduleAfter(plugin: JavaPlugin, ticks: Long, task: Runnable): TaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, ticks)
        return BukkitTaskHandle(bukkitTask)
    }

    private class BukkitTaskHandle(private val delegate: BukkitTask) : TaskHandle {
        override fun cancel() {
            delegate.cancel()
        }
    }
}

private class FoliaScheduler private constructor(
    private val plugin: JavaPlugin,
    private val globalScheduler: Any,
    private val runDelayedMethod: java.lang.reflect.Method,
    private val runAtFixedRateMethod: java.lang.reflect.Method,
) : TaskScheduler {
    override fun scheduleEvery(plugin: JavaPlugin, ticks: Long, task: Runnable): TaskHandle? {
        val consumer = Consumer<Any> { task.run() }
        val handle = runAtFixedRateMethod.invoke(globalScheduler, this.plugin, consumer, ticks, ticks)
        return ReflectionTaskHandle(handle)
    }

    override fun scheduleAfter(plugin: JavaPlugin, ticks: Long, task: Runnable): TaskHandle? {
        val consumer = Consumer<Any> { task.run() }
        val handle = runDelayedMethod.invoke(globalScheduler, this.plugin, consumer, ticks)
        return ReflectionTaskHandle(handle)
    }

    companion object {
        fun tryCreate(plugin: JavaPlugin, logger: (String) -> Unit): FoliaScheduler? {
            return try {
                val serverClass = plugin.server.javaClass
                val globalMethod = serverClass.methods.firstOrNull {
                    it.name == "getGlobalRegionScheduler" && it.parameterCount == 0
                } ?: return null
                val globalScheduler = globalMethod.invoke(plugin.server) ?: return null
                val methods = globalScheduler.javaClass.methods
                val runDelayed = methods.firstOrNull {
                    it.name == "runDelayed" && it.parameterCount == 3
                } ?: return null
                val runAtFixedRate = methods.firstOrNull {
                    it.name == "runAtFixedRate" && it.parameterCount == 4
                } ?: return null
                FoliaScheduler(plugin, globalScheduler, runDelayed, runAtFixedRate)
            } catch (ex: Exception) {
                logger("runtime=scheduler folia_detect_failed reason=${ex.message}")
                null
            }
        }
    }

    private class ReflectionTaskHandle(private val delegate: Any) : TaskHandle {
        override fun cancel() {
            delegate.javaClass.methods.firstOrNull { it.name == "cancel" && it.parameterCount == 0 }?.invoke(delegate)
        }
    }
}
