package icu.aetherland.dynamicd.integration

interface PlaceholderRegistrar {
    fun register(spec: PlaceholderSpec)
    fun listRegisteredKeys(): List<String>
}
