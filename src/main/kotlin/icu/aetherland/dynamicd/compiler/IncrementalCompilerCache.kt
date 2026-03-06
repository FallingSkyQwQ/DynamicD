package icu.aetherland.dynamicd.compiler

import com.github.benmanes.caffeine.cache.Caffeine
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class CachedFileAnalysis(
    val hash: String,
    val events: List<String>,
    val commands: List<String>,
    val permissions: List<String>,
    val timers: List<String>,
    val placeholders: List<String>,
    val integrations: Set<String>,
    val diagnostics: List<Diagnostic>,
    val exportedFunctions: List<String>,
    val dependencies: List<String>,
    val compiledPredicates: Int,
    val throttledEvents: Int,
)

class IncrementalCompilerCache {
    private val cache = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build<String, CachedFileAnalysis>()

    private val moduleFiles = ConcurrentHashMap<String, MutableSet<String>>()

    fun get(file: File, hash: String): CachedFileAnalysis? = cache.getIfPresent("${file.absolutePath}#$hash")

    fun put(moduleId: String, file: File, hash: String, analysis: CachedFileAnalysis) {
        cache.put("${file.absolutePath}#$hash", analysis)
        moduleFiles.computeIfAbsent(moduleId) { mutableSetOf() }.add(file.absolutePath)
    }

    fun clearModule(moduleId: String) {
        moduleFiles.remove(moduleId)?.forEach { path ->
            cache.asMap().keys.removeIf { key -> key.startsWith("$path#") }
        }
    }

    fun hash(content: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
