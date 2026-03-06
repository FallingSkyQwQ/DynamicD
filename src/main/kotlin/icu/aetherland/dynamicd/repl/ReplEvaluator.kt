package icu.aetherland.dynamicd.repl

import icu.aetherland.dynamicd.security.Capability
import icu.aetherland.dynamicd.security.SecurityPolicy
import icu.aetherland.dynamicd.security.SandboxLevel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReplEvaluator(
    private val securityPolicy: SecurityPolicy,
    private val sandboxLevel: SandboxLevel,
    private val timeoutSeconds: Int,
) {
    private val pool = Executors.newCachedThreadPool()

    fun eval(session: ReplSession, input: String): String {
        val permission = securityPolicy.check(sandboxLevel, Capability.REPL_EXECUTE)
        if (!permission.allowed) {
            return "denied:${permission.missing}"
        }
        val future = pool.submit<String> {
            when {
                input.startsWith(":set ") -> {
                    val payload = input.removePrefix(":set ").split("=", limit = 2)
                    if (payload.size == 2) {
                        session.variables[payload[0].trim()] = payload[1].trim()
                        "ok"
                    } else {
                        "error:usage :set key=value"
                    }
                }
                input == ":vars" -> session.variables.entries.joinToString(",") { "${it.key}=${it.value}" }
                input.startsWith("sleep ") -> {
                    val ms = input.removePrefix("sleep ").trim().toLongOrNull() ?: 0L
                    Thread.sleep(ms)
                    "slept:$ms"
                }
                else -> "eval:$input"
            }
        }
        return try {
            future.get(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            "timeout"
        }
    }
}
