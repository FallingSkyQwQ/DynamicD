package icu.aetherland.dynamicd.integration.spi

interface TypeRegistry {
    fun registerType(name: String)
    fun listTypes(): List<String>
}

interface FunctionRegistry {
    fun registerFunction(name: String)
    fun listFunctions(): List<String>
}

interface EventBridgeRegistry {
    fun registerEvent(eventPath: String)
    fun listEvents(): List<String>
}

interface YuzHostExtension {
    val id: String
    fun registerTypes(types: TypeRegistry) {}
    fun registerFunctions(functions: FunctionRegistry) {}
    fun registerEvents(events: EventBridgeRegistry) {}
}
