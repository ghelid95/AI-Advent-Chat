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
import kotlin.system.measureTimeMillis

@Serializable
data class ChatRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val messages: List<ChatMessage>,
    val system: String? = null,
    val temperature: Float? = null
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

@Serializable
data class ModelsResponse(
    val data: List<Model>
)

@Serializable
data class Model(
    val id: String,
    val type: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

class ClaudeClient(private val apiKey: String) : ApiClient {
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

    override suspend fun fetchModels(): Result<List<Model>> {
        return try {
            println("=== Fetching models from Anthropic API ===")

            val response: ModelsResponse = client.get("https://api.anthropic.com/v1/models") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
            }.body()

            println("=== Fetched ${response.data.size} models ===")
            Result.success(response.data)
        } catch (e: Exception) {
            println("=== Error fetching models ===")
            println("Error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun calculateCost(model: String, inputTokens: Int, outputTokens: Int): Double {
        // Approximate Claude pricing (as of 2024)
        // Prices per million tokens
        val pricing = when {
            model.contains("opus") -> Pair(15.0, 25.0)
            model.contains("sonnet-4") -> Pair(3.0, 15.0)
            model.contains("sonnet") -> Pair(3.0, 15.0)
            model.contains("haiku") -> Pair(1.0, 5.0)
            else -> Pair(3.0, 15.0) // Default to Sonnet pricing
        }

        return (inputTokens * pricing.first / 1_000_000) + (outputTokens * pricing.second / 1_000_000)
    }

    override suspend fun sendMessage(messages: List<ChatMessage>, systemPrompt: String, temperature: Float, model: String): Result<LlmMessage> {
        return try {
            println("=== Sending request to Anthropic API ===")
            println("Message count: ${messages.size}")
            println("Temperature: $temperature")
            println("Model: $model")

            var response: ChatResponse? = null
            val timeMs = measureTimeMillis {
                response = client.post("https://api.anthropic.com/v1/messages") {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    setBody(ChatRequest(model = model, messages = messages, system = systemPrompt, temperature = temperature))
                }.body()
            }

            println("=== Received response ===")
            println("Response ID: ${response?.id}")
            println("Stop reason: ${response?.failReason}")
            println("Content: ${response?.content?.firstOrNull()?.text?.take(100)}...")
            println("Request time: ${timeMs}ms")

            val inputTokens = response?.usage?.promptTokens ?: 0
            val outputTokens = response?.usage?.completionTokens ?: 0
            val totalTokens = inputTokens + outputTokens
            val cost = calculateCost(model, inputTokens, outputTokens)

            val result = try {
                DefaultJson.decodeFromString<LlmMessage>(response?.content?.firstOrNull()?.text!!)
            } catch (e: Exception) {
                LlmMessage(
                    response?.content?.firstOrNull()?.text ?: response?.failReason ?: "No response",
                    joke = null
                )
            }

            val resultWithUsage = result.copy(
                usage = UsageInfo(
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = totalTokens,
                    estimatedCost = cost,
                    requestTimeMs = timeMs
                )
            )

            Result.success(resultWithUsage)
        } catch (e: Exception) {
            println("=== Error occurred ===")
            println("Error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override fun close() {
        client.close()
    }
}