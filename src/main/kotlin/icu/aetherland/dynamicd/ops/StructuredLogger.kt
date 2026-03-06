package icu.aetherland.dynamicd.ops

import java.io.File
import java.time.Instant

enum class LogChannel {
    COMPILE,
    RUNTIME,
    AGENT,
    REPL,
}

class StructuredLogger(private val logsRoot: File) {
    init {
        logsRoot.mkdirs()
    }

    fun log(channel: LogChannel, fields: Map<String, String>) {
        val file = File(logsRoot, "${channel.name.lowercase()}.log")
        val line = buildString {
            append("ts=")
            append(Instant.now().epochSecond)
            fields.forEach { (k, v) ->
                append(' ')
                append(k)
                append('=')
                append(escape(v))
            }
        }
        file.appendText("$line\n")
    }

    private fun escape(value: String): String {
        return value.replace(" ", "_").replace("\n", "\\n").replace("\r", "\\r")
    }
}
