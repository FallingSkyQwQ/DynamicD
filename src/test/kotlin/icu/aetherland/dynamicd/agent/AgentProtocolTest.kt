package icu.aetherland.dynamicd.agent

import icu.aetherland.dynamicd.agent.loop.AgentProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentProtocolTest {
    @Test
    fun `parses tools array payload`() {
        val step = AgentProtocol.parse(
            """TOOLS:[{"tool":"list","args":""},{"tool":"search","args":"wel"}]""",
        )
        assertEquals(2, step.toolCalls.size)
        assertEquals("list", step.toolCalls[0].name)
        assertEquals("search", step.toolCalls[1].name)
    }

    @Test
    fun `parses plan reflect and final`() {
        val step = AgentProtocol.parse(
            """
            PLAN:read module
            REFLECT:module exists
            FINAL:done
            """.trimIndent(),
        )
        assertEquals(listOf("read module"), step.plans)
        assertEquals(listOf("module exists"), step.reflections)
        assertTrue(step.finalSummary == "done")
    }
}
