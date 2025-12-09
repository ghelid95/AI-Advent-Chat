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
data class PerplexityRequest(
    val model: String,
    @SerialName("messages") val messages: List<PerplexityMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val temperature: Float? = null
)

@Serializable
data class PerplexityMessage(
    val role: String,
    val content: String
)

@Serializable
data class PerplexityResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val usage: PerplexityUsage? = null,
    val cost: PerplexityCost? = null
)

@Serializable
data class Choice(
    val message: PerplexityMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class PerplexityUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class PerplexityCost(
    @SerialName("input_tokens_cost") val inputTokensCost: Double? = null,
    @SerialName("output_tokens_cost") val outputTokensCost: Double? = null,
    @SerialName("request_cost") val requestCost: Double? = null,
    @SerialName("total_cost") val totalCost: Double? = null
)

class PerplexityClient(private val apiKey: String) : ApiClient {
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
            sanitizeHeader { header -> header == "Authorization" }
        }
    }

    override suspend fun fetchModels(): Result<List<Model>> = Result.success(listOf(
        "sonar","sonar-pro", "sonar-deep-research", "sonar-reasoning", "sonar-reasoning-pro"
    ).map {
        Model(
            id = it,
            type = it,
            displayName = it,
        )
    })

    private fun calculateCost(model: String, inputTokens: Int, outputTokens: Int): Double {
        // Approximate Perplexity pricing
        // Prices per million tokens
        val pricing = when (model) {
            "sonar" -> Pair(1.0, 1.0)
            "sonar-pro" -> Pair(3.0, 15.0)
            "sonar-deep-research" -> Pair(5.0, 5.0)
            "sonar-reasoning" -> Pair(1.0, 5.0)
            "sonar-reasoning-pro" -> Pair(1.0, 5.0)
            else -> Pair(1.0, 1.0) // Default to sonar pricing
        }

        return (inputTokens * pricing.first / 1_000_000) + (outputTokens * pricing.second / 1_000_000)
    }

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        model: String
    ): Result<LlmMessage> {
        return try {
            println("=== Sending request to Perplexity API ===")
            println("Message count: ${messages.size}")
            println("Temperature: $temperature")
            println("Model: $model")

            // Convert to Perplexity format, adding system prompt as first message
            val perplexityMessages = mutableListOf<PerplexityMessage>()
            if (systemPrompt.isNotBlank()) {
                perplexityMessages.add(PerplexityMessage("system", systemPrompt))
            }
            perplexityMessages.addAll(messages.map {
                PerplexityMessage(it.role, it.content)
            })

            var response: PerplexityResponse? = null
            val timeMs = measureTimeMillis {
                response = client.post("https://api.perplexity.ai/chat/completions") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(PerplexityRequest(
                        model = model,
                        messages = perplexityMessages,
                        temperature = temperature
                    ))
                }.body()
            }

            println("=== Received response ===")
            println("Response ID: ${response?.id}")
            println("Finish reason: ${response?.choices?.firstOrNull()?.finishReason}")
            println("Request time: ${timeMs}ms")

            val messageContent = response?.choices?.firstOrNull()?.message?.content
                ?: throw Exception("No response from Perplexity")

            println("Content: ${messageContent.take(100)}...")

            val inputTokens = response.usage?.promptTokens ?: 0
            val outputTokens = response.usage?.completionTokens ?: 0
            val totalTokens = response.usage?.totalTokens ?: (inputTokens + outputTokens)

            // Use actual cost from API response if available, otherwise calculate
            val cost = response.cost?.totalCost ?: calculateCost(model, inputTokens, outputTokens)

            println("Cost: $${String.format("%.6f", cost)}")

            val result = try {
                DefaultJson.decodeFromString<LlmMessage>(messageContent)
            } catch (e: Exception) {
                LlmMessage(messageContent, joke = null)
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