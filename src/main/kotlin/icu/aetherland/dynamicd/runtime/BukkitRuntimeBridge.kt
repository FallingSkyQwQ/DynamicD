package icu.aetherland.dynamicd.runtime

import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class BukkitRuntimeBridge(
    private val plugin: JavaPlugin,
    private val eventBridge: EventBridge,
) : RuntimeBridge {
    override fun bindEvent(moduleId: String, eventPath: String): ListenerHandle? {
        val listener = eventBridge.createListener(moduleId, eventPath) ?: return null
        Bukkit.getPluginManager().registerEvents(listener, plugin)
        return BukkitListenerHandle(listener)
    }

    override fun bindTimer(moduleId: String, timerSpec: String): TaskHandle? {
        val parts = timerSpec.split(":")
        if (parts.size != 2) {
            return null
        }
        val timerType = parts[0]
        val durationLiteral = parts[1]
        val ticks = parseTicks(durationLiteral) ?: return null

        val task = when (timerType) {
            "every" -> Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                plugin.logger.fine("timer tick module=$moduleId duration=$durationLiteral")
            }, ticks, ticks)
            "after" -> Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                plugin.logger.fine("timer once module=$moduleId duration=$durationLiteral")
            }, ticks)
            else -> return null
        }
        return BukkitTaskHandle(task)
    }

    private fun parseTicks(durationLiteral: String): Long? {
        if (durationLiteral.length < 2) {
            return null
        }
        val unit = durationLiteral.last()
        val value = durationLiteral.dropLast(1).toLongOrNull() ?: return null
        if (value < 0) {
            return null
        }
        return when (unit) {
            't' -> value
            's' -> value * 20
            'm' -> value * 20 * 60
            'h' -> value * 20 * 60 * 60
            'd' -> value * 20 * 60 * 60 * 24
            else -> null
        }
    }

    private class BukkitListenerHandle(private val listener: Listener) : ListenerHandle {
        override fun unregister() {
            HandlerList.unregisterAll(listener)
        }
    }

    private class BukkitTaskHandle(private val task: BukkitTask) : TaskHandle {
        override fun cancel() {
            task.cancel()
        }
    }
}
