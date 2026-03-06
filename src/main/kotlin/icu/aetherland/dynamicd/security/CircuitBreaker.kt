package icu.aetherland.dynamicd.security

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class CircuitState(
    val tripped: Boolean,
    val reason: String? = null,
    val trippedAt: Long? = null,
)

class CircuitBreaker {
    private val states = ConcurrentHashMap<String, CircuitState>()

    fun state(moduleId: String): CircuitState = states[moduleId] ?: CircuitState(tripped = false)

    fun trip(moduleId: String, reason: String) {
        states[moduleId] = CircuitState(
            tripped = true,
            reason = reason,
            trippedAt = Instant.now().epochSecond,
        )
    }

    fun clear(moduleId: String) {
        states.remove(moduleId)
    }
}
