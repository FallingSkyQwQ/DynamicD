package icu.aetherland.dynamicd.security

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PendingConfirmation(
    val token: String,
    val operator: String,
    val action: String,
    val payload: String,
    val expiresAtEpochSec: Long,
)

class ConfirmationManager(private val ttlSeconds: Long = 60) {
    private val pending = ConcurrentHashMap<String, PendingConfirmation>()

    fun create(operator: String, action: String, payload: String): PendingConfirmation {
        val token = UUID.randomUUID().toString().substring(0, 8)
        val conf = PendingConfirmation(
            token = token,
            operator = operator,
            action = action,
            payload = payload,
            expiresAtEpochSec = Instant.now().epochSecond + ttlSeconds,
        )
        pending[token] = conf
        return conf
    }

    fun consume(operator: String, token: String): PendingConfirmation? {
        cleanupExpired()
        val conf = pending[token] ?: return null
        if (conf.operator != operator) {
            return null
        }
        pending.remove(token)
        return conf
    }

    fun cleanupExpired() {
        val now = Instant.now().epochSecond
        pending.values.filter { it.expiresAtEpochSec <= now }.forEach { pending.remove(it.token) }
    }
}
