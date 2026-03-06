package icu.aetherland.dynamicd.runtime

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class EventBridge(private val plugin: JavaPlugin) {
    fun createListener(moduleId: String, eventPath: String): Listener? {
        return when (eventPath.lowercase()) {
            "player join" -> object : Listener {
                @EventHandler
                fun onJoin(event: PlayerJoinEvent) {
                    plugin.logger.fine("Module $moduleId handled player join for ${event.player.name}")
                }
            }
            else -> null
        }
    }
}
