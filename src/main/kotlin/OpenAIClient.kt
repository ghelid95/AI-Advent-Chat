import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatRequest(
    val model: String = "claude-sonnet-4-20250514",
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val messages: List<ChatMessage>,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val content: List<Content>? = null,
    val usage: Usage? = null,
    @SerialName("stop_reason") val failReason: String? = null,
)

@Serializable
data class Content(
    val text: String,
)

@Serializable
data class Usage(
    @SerialName("input_tokens") val promptTokens: Int,
    @SerialName("output_tokens") val completionTokens: Int,
)

class OpenAIClient(private val apiKey: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                encodeDefaults = true
            })
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
            sanitizeHeader { header -> header == "x-api-key" }
        }
    }

    suspend fun sendMessage(messages: List<ChatMessage>): Result<String> {
        return try {
            println("=== Sending request to Anthropic API ===")
            println("Message count: ${messages.size}")

            val response: ChatResponse = client.post("https://api.anthropic.com/v1/messages") {
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(ChatRequest(messages = messages))
            }.body()

            println("=== Received response ===")
            println("Response ID: ${response.id}")
            println("Stop reason: ${response.failReason}")
            println("Content: ${response.content?.firstOrNull()?.text?.take(100)}...")

            Result.success(response.content?.firstOrNull()?.text ?: response.failReason ?: "No response")
        } catch (e: Exception) {
            println("=== Error occurred ===")
            println("Error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}