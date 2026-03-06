package icu.aetherland.dynamicd.repl

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplSessionManagerTest {
    @Test
    fun `session open close and timeout`() {
        val manager = ReplSessionManager(timeoutSeconds = 1)
        val session = manager.open("tester")
        assertNotNull(session)
        Thread.sleep(1200)
        assertNull(manager.get("tester"))
        assertTrue(manager.open("tester").sessionId.isNotBlank())
        assertTrue(manager.close("tester"))
    }
}
