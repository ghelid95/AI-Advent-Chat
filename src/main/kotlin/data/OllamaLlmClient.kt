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
private data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

@Serializable
private data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
private data class OllamaOptions(
    val temperature: Float? = null,
    @SerialName("num_predict") val numPredict: Int? = null,
    @SerialName("num_ctx") val numCtx: Int? = null,  // Context window size
    @SerialName("num_gpu") val numGpu: Int? = null,  // Number of GPU layers
    @SerialName("num_thread") val numThread: Int? = null,  // Number of threads
    @SerialName("repeat_penalty") val repeatPenalty: Float? = null,  // Penalty for repetition
    @SerialName("top_k") val topK: Int? = null,  // Top-K sampling
    @SerialName("top_p") val topP: Float? = null  // Top-P (nucleus) sampling
)

@Serializable
private data class OllamaChatResponse(
    val model: String? = null,
    val message: OllamaMessage? = null,
    val done: Boolean = false,
    @SerialName("total_duration") val totalDuration: Long? = null,
    @SerialName("load_duration") val loadDuration: Long? = null,
    @SerialName("prompt_eval_count") val promptEvalCount: Int? = null,
    @SerialName("prompt_eval_duration") val promptEvalDuration: Long? = null,
    @SerialName("eval_count") val evalCount: Int? = null,
    @SerialName("eval_duration") val evalDuration: Long? = null
)

@Serializable
private data class OllamaTagsResponse(
    val models: List<OllamaModelInfo>
)

@Serializable
private data class OllamaModelInfo(
    val name: String,
    val model: String? = null,
    @SerialName("modified_at") val modifiedAt: String? = null,
    val size: Long? = null,
    val digest: String? = null
)

/**
 * Ollama client for local LLM inference.
 * Connects to a locally running Ollama server (default: http://localhost:11434).
 * No API key required for local usage.
 */
class OllamaLlmClient(
    private val baseUrl: String = "http://localhost:11434"
) : ApiClient {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                encodeDefaults = true
                explicitNulls = false
            })
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000  // 5 minutes for local models
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 300_000
        }
    }

    override suspend fun fetchModels(): Result<List<Model>> {
        return try {
            println("=== Fetching models from Ollama API ===")

            val response: OllamaTagsResponse = client.get("$baseUrl/api/tags").body()

            val models = response.models.map { ollamaModel ->
                Model(
                    id = ollamaModel.name,
                    type = "ollama",
                    displayName = ollamaModel.name,
                    createdAt = ollamaModel.modifiedAt
                )
            }

            println("=== Fetched ${models.size} models ===")
            Result.success(models)
        } catch (e: Exception) {
            println("=== Error fetching Ollama models ===")
            println("Error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override fun supportsTools(): Boolean = false  // Ollama doesn't have native tool support

    /**
     * Send message with custom Ollama configuration
     */
    suspend fun sendMessageWithConfig(
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        model: String,
        maxTokens: Int,
        config: OllamaOptimizationConfig,
        tools: List<ClaudeTool>? = null
    ): Result<LlmMessage> {
        return sendMessageInternal(
            messages = messages,
            systemPrompt = systemPrompt,
            temperature = temperature,
            model = model,
            maxTokens = maxTokens,
            tools = tools,
            customConfig = config
        )
    }

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        model: String,
        maxTokens: Int,
        tools: List<ClaudeTool>?
    ): Result<LlmMessage> {
        return sendMessageInternal(
            messages = messages,
            systemPrompt = systemPrompt,
            temperature = temperature,
            model = model,
            maxTokens = maxTokens,
            tools = tools,
            customConfig = null
        )
    }

    private suspend fun sendMessageInternal(
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        model: String,
        maxTokens: Int,
        tools: List<ClaudeTool>?,
        customConfig: OllamaOptimizationConfig?
    ): Result<LlmMessage> {
        return try {
            println("=== Sending request to Ollama API ===")
            println("Message count: ${messages.size}")
            println("Temperature: $temperature")
            println("Model: $model")
            println("Max Tokens: $maxTokens")

            if (tools != null && tools.isNotEmpty()) {
                println("Warning: Tools provided but Ollama doesn't support native tool use. Ignoring tools.")
            }

            // Convert ChatMessage to OllamaMessage
            val ollamaMessages = buildList {
                // Add system prompt as first message if provided
                if (systemPrompt.isNotBlank()) {
                    add(OllamaMessage(role = "system", content = systemPrompt))
                }

                // Add conversation messages
                messages.forEach { msg ->
                    val content = when (msg.content) {
                        is ChatMessageContent.Text -> msg.content.text
                        is ChatMessageContent.ContentBlocks -> {
                            // Flatten content blocks to text
                            msg.content.blocks.filterIsInstance<ContentBlock.Text>()
                                .joinToString("\n") { it.text }
                        }
                    }
                    if (content.isNotBlank()) {
                        add(OllamaMessage(role = msg.role, content = content))
                    }
                }
            }

            var response: OllamaChatResponse? = null
            val timeMs = measureTimeMillis {
                response = client.post("$baseUrl/api/chat") {
                    contentType(ContentType.Application.Json)
                    setBody(OllamaChatRequest(
                        model = model,
                        messages = ollamaMessages,
                        stream = false,
                        options = if (customConfig != null) {
                            OllamaOptions(
                                temperature = customConfig.temperature,
                                numPredict = customConfig.maxTokens,
                                numCtx = customConfig.numCtx,
                                numGpu = customConfig.numGpu,
                                numThread = customConfig.numThread,
                                repeatPenalty = customConfig.repeatPenalty,
                                topK = customConfig.topK,
                                topP = customConfig.topP
                            )
                        } else {
                            OllamaOptions(
                                temperature = temperature,
                                numPredict = maxTokens
                            )
                        }
                    ))
                }.body()
            }

            println("=== Received response ===")
            println("Done: ${response?.done}")
            println("Content: ${response?.message?.content?.take(100)}...")
            println("Request time: ${timeMs}ms")

            val answerText = response?.message?.content ?: "No response"

            // Calculate token usage from Ollama response
            val inputTokens = response?.promptEvalCount ?: 0
            val outputTokens = response?.evalCount ?: 0
            val totalTokens = inputTokens + outputTokens

            // Try to parse as JSON for joke extraction, otherwise use plain text
            val result = try {
                DefaultJson.decodeFromString<LlmMessage>(answerText)
            } catch (e: Exception) {
                LlmMessage(
                    answer = answerText,
                    joke = null
                )
            }

            val resultWithUsage = result.copy(
                usage = UsageInfo(
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = totalTokens,
                    estimatedCost = 0.0,  // Local model, no cost
                    requestTimeMs = timeMs,
                    estimatedInputCost = 0.0,
                    estimatedOutputCost = 0.0
                ),
                toolUses = null,
                stopReason = if (response?.done == true) "end_turn" else null
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
