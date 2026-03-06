package icu.aetherland.dynamicd.agent.llm

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OpenAiCompatibleProvider(
    private val endpoint: String,
    private val apiKey: String,
) : LlmProvider {
    override val name: String = "openai-compatible"
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override fun complete(request: LlmRequest): LlmResponse {
        if (endpoint.isBlank() || apiKey.isBlank()) {
            return LlmResponse(
                content = """
                PLAN: no remote endpoint configured
                TOOL: search ${request.messages.lastOrNull()?.content?.take(24) ?: "module"}
                """.trimIndent(),
            )
        }
        val payload = buildJson(request)
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .timeout(Duration.ofSeconds(30))
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            return LlmResponse("PLAN: provider_error status=${response.statusCode()}")
        }
        val content = extractContent(response.body())
        return LlmResponse(content.ifBlank { "PLAN: empty_response" })
    }

    private fun buildJson(request: LlmRequest): String {
        val escapedMessages = request.messages.joinToString(",") { message ->
            """{"role":"${escape(message.role)}","content":"${escape(message.content)}"}"""
        }
        return """{"model":"${escape(request.model)}","temperature":${request.temperature},"messages":[${escapedMessages}]}"""
    }

    private fun extractContent(body: String): String {
        val marker = "\"content\":\""
        val idx = body.indexOf(marker)
        if (idx < 0) {
            return ""
        }
        val start = idx + marker.length
        val sb = StringBuilder()
        var i = start
        var escaped = false
        while (i < body.length) {
            val c = body[i]
            if (escaped) {
                sb.append(
                    when (c) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        '\\' -> '\\'
                        '"' -> '"'
                        else -> c
                    },
                )
                escaped = false
                i++
                continue
            }
            if (c == '\\') {
                escaped = true
                i++
                continue
            }
            if (c == '"') {
                break
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun escape(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
