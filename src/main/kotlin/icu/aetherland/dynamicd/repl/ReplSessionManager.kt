package icu.aetherland.dynamicd.repl

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ReplSession(
    val sessionId: String,
    val operator: String,
    val createdAt: Instant,
    var lastAccess: Instant,
    val variables: MutableMap<String, String> = mutableMapOf(),
)

class ReplSessionManager(private val timeoutSeconds: Int) {
    private val sessions = ConcurrentHashMap<String, ReplSession>()

    fun open(operator: String): ReplSession {
        val now = Instant.now()
        val existing = sessions[operator]
        if (existing != null) {
            existing.lastAccess = now
            return existing
        }
        val session = ReplSession(
            sessionId = UUID.randomUUID().toString(),
            operator = operator,
            createdAt = now,
            lastAccess = now,
        )
        sessions[operator] = session
        return session
    }

    fun get(operator: String): ReplSession? {
        val session = sessions[operator] ?: return null
        if (Instant.now().epochSecond - session.lastAccess.epochSecond >= timeoutSeconds) {
            sessions.remove(operator)
            return null
        }
        session.lastAccess = Instant.now()
        return session
    }

    fun close(operator: String): Boolean = sessions.remove(operator) != null

    fun cleanupExpired(): Int {
        val now = Instant.now().epochSecond
        val expired = sessions.values.filter { now - it.lastAccess.epochSecond > timeoutSeconds }
        expired.forEach { sessions.remove(it.operator) }
        return expired.size
    }
}
