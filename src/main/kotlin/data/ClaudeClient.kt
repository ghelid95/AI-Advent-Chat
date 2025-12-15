package data

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
private data class ChatRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val messages: List<ClaudeApiMessage>,
    val system: String? = null,
    val temperature: Float? = null,
    val tools: List<ClaudeTool>? = null
)

@Serializable
private data class ClaudeApiMessage(
    val role: String,
    val content: ClaudeApiContent
)

@Serializable(with = ClaudeApiContentSerializer::class)
sealed class ClaudeApiContent {
    data class TextContent(val text: String) : ClaudeApiContent()
    data class BlocksContent(val blocks: List<ContentBlock>) : ClaudeApiContent()
}

// Custom serializer for Claude API content (can be string or array of blocks)
object ClaudeApiContentSerializer : kotlinx.serialization.KSerializer<ClaudeApiContent> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("ClaudeApiContent")

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: ClaudeApiContent) {
        when (value) {
            is ClaudeApiContent.TextContent -> encoder.encodeString(value.text)
            is ClaudeApiContent.BlocksContent -> encoder.encodeSerializableValue(
                kotlinx.serialization.builtins.ListSerializer(ContentBlock.serializer()),
                value.blocks
            )
        }
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): ClaudeApiContent {
        throw NotImplementedError("Deserialization not needed for requests")
    }
}

@Serializable
private data class ChatResponse(
    val id: String? = null,
    val content: List<ContentBlock>? = null,
    val usage: Usage? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
private data class Usage(
    @SerialName("input_tokens") val promptTokens: Int,
    @SerialName("output_tokens") val completionTokens: Int,
)

@Serializable
private data class ModelsResponse(
    val data: List<Model>
)

class ClaudeClient(private val apiKey: String) : ApiClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                encodeDefaults = true
                explicitNulls = false  // Don't serialize null fields
            })
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
            sanitizeHeader { header -> header == "x-api-key" }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
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

    private fun calculateCost(model: String, inputTokens: Int, outputTokens: Int): Triple<Double, Double, Double> {
        // Approximate Claude pricing (as of 2024)
        // Prices per million tokens
        val pricing = when {
            model.contains("opus") -> Pair(15.0, 25.0)
            model.contains("sonnet-4") -> Pair(3.0, 15.0)
            model.contains("sonnet") -> Pair(3.0, 15.0)
            model.contains("haiku") -> Pair(1.0, 5.0)
            else -> Pair(3.0, 15.0) // Default to Sonnet pricing
        }

        val inputCost = inputTokens * pricing.first / 1_000_000
        val outputCost = outputTokens * pricing.second / 1_000_000

        return Triple(inputCost, outputCost, inputCost + outputCost)
    }

    override fun supportsTools(): Boolean = true

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        model: String,
        maxTokens: Int,
        tools: List<ClaudeTool>?
    ): Result<LlmMessage> {
        return try {
            println("=== Sending request to Anthropic API ===")
            println("Message count: ${messages.size}")
            println("Temperature: $temperature")
            println("Model: $model")
            println("Max Tokens: $maxTokens")
            println("Tools: ${tools?.size ?: 0}")

            // Convert ChatMessage to ClaudeApiMessage
            val claudeMessages = messages.map { msg ->
                val content = when (msg.content) {
                    is ChatMessageContent.Text -> ClaudeApiContent.TextContent(msg.content.text)
                    is ChatMessageContent.ContentBlocks -> ClaudeApiContent.BlocksContent(msg.content.blocks)
                }
                ClaudeApiMessage(msg.role, content)
            }

            var response: ChatResponse? = null
            val timeMs = measureTimeMillis {
                response = client.post("https://api.anthropic.com/v1/messages") {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    setBody(ChatRequest(
                        model = model,
                        messages = claudeMessages,
                        system = systemPrompt,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        tools = tools  // null fields are omitted due to explicitNulls = false
                    ))
                }.body()
            }

            println("=== Received response ===")
            println("Response ID: ${response?.id}")
            println("Stop reason: ${response?.stopReason}")

            // Extract text and tool_use blocks
            val textBlocks = response?.content?.filterIsInstance<ContentBlock.Text>() ?: emptyList()
            val toolUseBlocks = response?.content?.filterIsInstance<ContentBlock.ToolUse>() ?: emptyList()

            println("Text blocks: ${textBlocks.size}")
            println("Tool use blocks: ${toolUseBlocks.size}")
            if (textBlocks.isNotEmpty()) {
                println("Content: ${textBlocks.first().text.take(100)}...")
            }
            if (toolUseBlocks.isNotEmpty()) {
                println("Tools requested: ${toolUseBlocks.joinToString(", ") { it.name }}")
            }
            println("Request time: ${timeMs}ms")

            val inputTokens = response?.usage?.promptTokens ?: 0
            val outputTokens = response?.usage?.completionTokens ?: 0
            val totalTokens = inputTokens + outputTokens
            val cost = calculateCost(model, inputTokens, outputTokens)

            // Try to parse first text block as JSON for joke extraction, otherwise use plain text
            val answerText = textBlocks.firstOrNull()?.text ?: ""
            val result = try {
                DefaultJson.decodeFromString<LlmMessage>(answerText)
            } catch (e: Exception) {
                LlmMessage(
                    answer = answerText.ifEmpty { response?.stopReason ?: "No response" },
                    joke = null
                )
            }

            val resultWithUsage = result.copy(
                usage = UsageInfo(
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = totalTokens,
                    estimatedCost = cost.third,
                    requestTimeMs = timeMs,
                    estimatedInputCost = cost.first,
                    estimatedOutputCost = cost.second,
                ),
                toolUses = toolUseBlocks.ifEmpty { null },
                stopReason = response?.stopReason
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
