package icu.aetherland.dynamicd.compiler

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompilerFacadeTest {
    @Test
    fun `extracts event command and permission registry`() {
        val dir = Files.createTempDirectory("dynamicd-mod").toFile()
        val file = File(dir, "mod.yuz")
        file.writeText(
            """
            module "dynamicd:welcome"
            permission "dynamicd.coins"
            on player join {
            }
            command "/coins" permission "dynamicd.coins" {
            }
            every 5s {
            }
            """.trimIndent(),
        )

        val result = CompilerFacade.compileModule("welcome", dir)
        assertTrue(result.success)
        assertEquals(listOf("player join"), result.registry.events)
        assertEquals(1, result.registry.commands.size)
        assertEquals(listOf("dynamicd.coins"), result.registry.permissions)
        assertEquals(listOf("every:5s"), result.registry.timers)
    }

    @Test
    fun `fails nullable player access without guard`() {
        val dir = Files.createTempDirectory("dynamicd-mod").toFile()
        val file = File(dir, "mod.yuz")
        file.writeText(
            """
            module "dynamicd:welcome"
            fn test() {
              let target: Player? = null
              tell target.name
            }
            """.trimIndent(),
        )

        val result = CompilerFacade.compileModule("welcome", dir)
        assertFalse(result.success)
        assertTrue(result.diagnostics.any { it.code == "E0401" })
    }

    @Test
    fun `fails sync api in async block`() {
        val dir = Files.createTempDirectory("dynamicd-mod").toFile()
        val file = File(dir, "mod.yuz")
        file.writeText(
            """
            module "dynamicd:welcome"
            async {
              teleport player to loc
            }
            """.trimIndent(),
        )

        val result = CompilerFacade.compileModule("welcome", dir)
        assertFalse(result.success)
        assertTrue(result.diagnostics.any { it.code == "E0500" })
    }
}
