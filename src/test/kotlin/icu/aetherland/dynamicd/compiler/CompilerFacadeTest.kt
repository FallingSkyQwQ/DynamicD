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

    @Test
    fun `requires module declaration`() {
        val dir = Files.createTempDirectory("dynamicd-mod").toFile()
        File(dir, "mod.yuz").writeText(
            """
            fn test() { }
            """.trimIndent(),
        )

        val result = CompilerFacade.compileModule("welcome", dir)
        assertFalse(result.success)
        assertTrue(result.diagnostics.any { it.code == "E0200" })
    }

    @Test
    fun `only exported functions are in symbol index`() {
        val dir = Files.createTempDirectory("dynamicd-mod").toFile()
        File(dir, "mod.yuz").writeText(
            """
            module "dynamicd:welcome"
            record Reward {
                id: String
            }
            enum Rank {
                MEMBER
                VIP
            }
            trait Formatter {
                fn format() -> String
            }
            impl Formatter for Reward {
            }
            fn hidden() { }
            export fn open() { }
            use dynamicd:core
            """.trimIndent(),
        )

        val result = CompilerFacade.compileModule("welcome", dir)
        assertTrue(result.success)
        assertEquals(listOf("open"), result.symbolIndex.exportedFunctions)
        assertEquals(listOf("core"), result.symbolIndex.dependencies)
        assertEquals(listOf("Reward"), result.symbolIndex.records)
        assertEquals(listOf("Rank"), result.symbolIndex.enums)
        assertEquals(listOf("Formatter"), result.symbolIndex.traits)
    }

    @Test
    fun `fails impl target and trait validation`() {
        val dir = Files.createTempDirectory("dynamicd-mod").toFile()
        File(dir, "mod.yuz").writeText(
            """
            module "dynamicd:welcome"
            impl UnknownTrait for UnknownType {
            }
            """.trimIndent(),
        )

        val result = CompilerFacade.compileModule("welcome", dir)
        assertFalse(result.success)
        assertTrue(result.diagnostics.any { it.code == "E0601" })
        assertTrue(result.diagnostics.any { it.code == "E0602" })
    }

    @Test
    fun `fails question mark outside result context`() {
        val dir = Files.createTempDirectory("dynamicd-mod").toFile()
        File(dir, "mod.yuz").writeText(
            """
            module "dynamicd:welcome"
            fn test() {
              let x = findHome("spawn")?
            }
            """.trimIndent(),
        )
        val result = CompilerFacade.compileModule("welcome", dir)
        assertFalse(result.success)
        assertTrue(result.diagnostics.any { it.code == "E0701" })
    }

    @Test
    fun `match without else emits warning`() {
        val dir = Files.createTempDirectory("dynamicd-mod").toFile()
        File(dir, "mod.yuz").writeText(
            """
            module "dynamicd:welcome"
            match rank {
              case VIP => tell player "vip"
            }
            """.trimIndent(),
        )
        val result = CompilerFacade.compileModule("welcome", dir)
        assertTrue(result.success)
        assertTrue(result.diagnostics.any { it.code == "W0604" })
    }
}
