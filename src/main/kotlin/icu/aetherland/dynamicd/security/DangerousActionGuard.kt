package icu.aetherland.dynamicd.security

import org.bukkit.command.CommandSender

class DangerousActionGuard {
    fun isConfirmed(sender: CommandSender, explicitConfirm: Boolean): Boolean {
        return explicitConfirm || sender.hasPermission("dynamicd.ops.confirm.bypass")
    }
}
