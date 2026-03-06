package icu.aetherland.dynamicd.integration

import icu.aetherland.dynamicd.integration.spi.ExtensionRegistry
import icu.aetherland.dynamicd.integration.spi.EventBridgeRegistry
import icu.aetherland.dynamicd.integration.spi.FunctionRegistry
import icu.aetherland.dynamicd.integration.spi.TypeRegistry
import icu.aetherland.dynamicd.integration.spi.YuzHostExtension
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionRegistryTest {
    @Test
    fun `registry records extension published contracts`() {
        val registry = ExtensionRegistry()
        registry.register(
            object : YuzHostExtension {
                override val id: String = "test-ext"
                override fun registerTypes(types: TypeRegistry) {
                    types.registerType("Money")
                }
                override fun registerFunctions(functions: FunctionRegistry) {
                    functions.registerFunction("money.get")
                }
                override fun registerEvents(events: EventBridgeRegistry) {
                    events.registerEvent("economy transaction")
                }
            },
        )

        val snapshot = registry.snapshot()
        assertEquals(listOf("test-ext"), snapshot.extensions)
        assertEquals(listOf("Money"), snapshot.types)
        assertEquals(listOf("money.get"), snapshot.functions)
        assertEquals(listOf("economy transaction"), snapshot.events)
    }
}
