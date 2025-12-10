import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.HttpTimeout
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
    val usage: PerplexityUsage,
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
    @SerialName("total_tokens") val totalTokens: Int,
    val cost: PerplexityCost
)

@Serializable
data class PerplexityCost(
    @SerialName("input_tokens_cost") val inputTokensCost: Double,
    @SerialName("output_tokens_cost") val outputTokensCost: Double,
    @SerialName("request_cost") val requestCost: Double,
    @SerialName("total_cost") val totalCost: Double
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
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
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

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        model: String,
        maxTokens: Int
    ): Result<LlmMessage> {
        return try {
            println("=== Sending request to Perplexity API ===")
            println("Message count: ${messages.size}")
            println("Temperature: $temperature")
            println("Model: $model")
            println("Max Tokens: $maxTokens")

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
                        temperature = temperature,
                        maxTokens = maxTokens
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

            val inputTokens = response.usage.promptTokens
            val outputTokens = response.usage.completionTokens
            val totalTokens = response.usage.totalTokens

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
                    estimatedCost = response.usage.cost.totalCost,
                    requestTimeMs = timeMs,
                    estimatedInputCost = response.usage.cost.inputTokensCost,
                    estimatedOutputCost = response.usage.cost.outputTokensCost
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