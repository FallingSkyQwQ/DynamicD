package icu.aetherland.dynamicd.integration.spi

import java.util.ServiceLoader
import java.util.concurrent.CopyOnWriteArrayList

data class ExtensionSnapshot(
    val extensions: List<String>,
    val types: List<String>,
    val functions: List<String>,
    val events: List<String>,
)

class ExtensionRegistry {
    private val typeRegistry = InMemoryTypeRegistry()
    private val functionRegistry = InMemoryFunctionRegistry()
    private val eventRegistry = InMemoryEventRegistry()
    private val loadedExtensions = CopyOnWriteArrayList<String>()

    fun register(extension: YuzHostExtension) {
        extension.registerTypes(typeRegistry)
        extension.registerFunctions(functionRegistry)
        extension.registerEvents(eventRegistry)
        loadedExtensions += extension.id
    }

    fun autoDiscover(): Int {
        var count = 0
        ServiceLoader.load(YuzHostExtension::class.java).forEach { extension ->
            register(extension)
            count++
        }
        return count
    }

    fun snapshot(): ExtensionSnapshot {
        return ExtensionSnapshot(
            extensions = loadedExtensions.toList().distinct().sorted(),
            types = typeRegistry.listTypes(),
            functions = functionRegistry.listFunctions(),
            events = eventRegistry.listEvents(),
        )
    }

    private class InMemoryTypeRegistry : TypeRegistry {
        private val values = linkedSetOf<String>()
        override fun registerType(name: String) {
            if (name.isNotBlank()) values += name
        }
        override fun listTypes(): List<String> = values.toList().sorted()
    }

    private class InMemoryFunctionRegistry : FunctionRegistry {
        private val values = linkedSetOf<String>()
        override fun registerFunction(name: String) {
            if (name.isNotBlank()) values += name
        }
        override fun listFunctions(): List<String> = values.toList().sorted()
    }

    private class InMemoryEventRegistry : EventBridgeRegistry {
        private val values = linkedSetOf<String>()
        override fun registerEvent(eventPath: String) {
            if (eventPath.isNotBlank()) values += eventPath
        }
        override fun listEvents(): List<String> = values.toList().sorted()
    }
}
