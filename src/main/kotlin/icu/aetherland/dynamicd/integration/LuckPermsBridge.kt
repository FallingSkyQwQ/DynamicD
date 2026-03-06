package icu.aetherland.dynamicd.integration

import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.user.User
import net.luckperms.api.node.types.InheritanceNode
import net.luckperms.api.node.types.MetaNode
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID

class LuckPermsBridge {
    private var available: Boolean = false

    fun installIfAvailable(enabled: Boolean): IntegrationDiagnostic {
        return try {
            LuckPermsProvider.get()
            available = enabled
            if (enabled) {
                IntegrationDiagnostic("LuckPerms", true, true, "LuckPerms bridge installed")
            } else {
                IntegrationDiagnostic("LuckPerms", true, false, "LuckPerms disabled by config")
            }
        } catch (_: IllegalStateException) {
            available = false
            IntegrationDiagnostic("LuckPerms", false, false, "LuckPerms missing, degraded mode")
        }
    }

    fun has(player: Player, node: String): Boolean {
        if (!available) {
            return false
        }
        val user = LuckPermsProvider.get().userManager.getUser(player.uniqueId) ?: return false
        return user.cachedData.permissionData.checkPermission(node).asBoolean()
    }

    fun grant(playerUuid: UUID, group: String, duration: Duration?): Boolean {
        if (!available) {
            return false
        }
        val luckPerms = LuckPermsProvider.get()
        val user = luckPerms.userManager.loadUser(playerUuid).join() ?: return false
        val node = if (duration != null) {
            InheritanceNode.builder(group).expiry(duration).build()
        } else {
            InheritanceNode.builder(group).build()
        }
        user.data().add(node)
        luckPerms.userManager.saveUser(user)
        return true
    }

    fun setMeta(playerUuid: UUID, key: String, value: String): Boolean {
        if (!available) {
            return false
        }
        val luckPerms = LuckPermsProvider.get()
        val user: User = luckPerms.userManager.loadUser(playerUuid).join() ?: return false
        user.data().add(MetaNode.builder(key, value).build())
        luckPerms.userManager.saveUser(user)
        return true
    }
}
