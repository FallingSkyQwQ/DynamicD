package icu.aetherland.dynamicd.security

import java.util.concurrent.ConcurrentHashMap

data class ResourceBudget(
    val cpuSteps: Long,
    val maxTasks: Int,
    val ioQuota: Long,
)

class ResourceLimiter(private val defaultBudget: ResourceBudget) {
    private val cpuUsage = ConcurrentHashMap<String, Long>()
    private val taskUsage = ConcurrentHashMap<String, Int>()
    private val ioUsage = ConcurrentHashMap<String, Long>()

    fun consumeCpu(moduleId: String, steps: Long): SecurityDecision {
        val used = cpuUsage.merge(moduleId, steps) { a, b -> a + b } ?: steps
        return if (used <= defaultBudget.cpuSteps) {
            SecurityDecision(true)
        } else {
            SecurityDecision(false, "cpu budget exceeded ($used/${defaultBudget.cpuSteps})")
        }
    }

    fun registerTask(moduleId: String): SecurityDecision {
        val used = taskUsage.merge(moduleId, 1) { a, b -> a + b } ?: 1
        return if (used <= defaultBudget.maxTasks) {
            SecurityDecision(true)
        } else {
            SecurityDecision(false, "task budget exceeded ($used/${defaultBudget.maxTasks})")
        }
    }

    fun consumeIo(moduleId: String, amount: Long): SecurityDecision {
        val used = ioUsage.merge(moduleId, amount) { a, b -> a + b } ?: amount
        return if (used <= defaultBudget.ioQuota) {
            SecurityDecision(true)
        } else {
            SecurityDecision(false, "io budget exceeded ($used/${defaultBudget.ioQuota})")
        }
    }

    fun releaseTask(moduleId: String) {
        taskUsage.computeIfPresent(moduleId) { _, value -> (value - 1).coerceAtLeast(0) }
    }

    fun reset(moduleId: String) {
        cpuUsage.remove(moduleId)
        taskUsage.remove(moduleId)
        ioUsage.remove(moduleId)
    }
}
