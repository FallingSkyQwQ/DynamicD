package icu.aetherland.dynamicd.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceLimiterTest {
    @Test
    fun `resource limits enforce cpu task and io quotas`() {
        val limiter = ResourceLimiter(ResourceBudget(cpuSteps = 10, maxTasks = 2, ioQuota = 100))
        assertTrue(limiter.consumeCpu("m1", 5).allowed)
        assertFalse(limiter.consumeCpu("m1", 6).allowed)

        assertTrue(limiter.registerTask("m1").allowed)
        assertTrue(limiter.registerTask("m1").allowed)
        assertFalse(limiter.registerTask("m1").allowed)

        assertTrue(limiter.consumeIo("m1", 80).allowed)
        assertFalse(limiter.consumeIo("m1", 30).allowed)
    }
}
