package icu.aetherland.dynamicd.runtime

import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class BukkitRuntimeBridge(
    private val plugin: JavaPlugin,
    private val eventBridge: EventBridge,
    private val scheduler: TaskScheduler = TaskSchedulerFactory.create(plugin) { plugin.logger.info(it) },
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
            "every" -> scheduler.scheduleEvery(plugin, ticks, Runnable {
                plugin.logger.fine("timer tick module=$moduleId duration=$durationLiteral")
            })
            "after" -> scheduler.scheduleAfter(plugin, ticks, Runnable {
                plugin.logger.fine("timer once module=$moduleId duration=$durationLiteral")
            })
            else -> return null
        }
        return task
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
}
