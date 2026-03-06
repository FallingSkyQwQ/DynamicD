package icu.aetherland.dynamicd.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConfirmationManagerTest {
    @Test
    fun `create and consume token`() {
        val manager = ConfirmationManager(ttlSeconds = 60)
        val pending = manager.create("alice", "agent.run", "say hello")
        assertNotNull(pending.token)
        val consumed = manager.consume("alice", pending.token)
        assertNotNull(consumed)
        assertEquals("agent.run", consumed.action)
        assertNull(manager.consume("alice", pending.token))
    }

    @Test
    fun `token cannot be consumed by other operator`() {
        val manager = ConfirmationManager(ttlSeconds = 60)
        val pending = manager.create("alice", "snapshot.rollback", "s-1")
        assertNull(manager.consume("bob", pending.token))
    }
}
