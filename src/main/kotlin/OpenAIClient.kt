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
    val system: String? = null
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

    suspend fun sendMessage(messages: List<ChatMessage>): Result<LlmMessage> {
        return try {
            println("=== Sending request to Anthropic API ===")
            println("Message count: ${messages.size}")

            val systemPrompt = """
                You are professional chef. You can prepare a list of recipes, but only after you gather necessary information.
                Information you need to do this is:
                    1. Ingredients in user fridge.
                    2. Spices user have.
                    3. Kitchenware user owns.
                    4. Kitchen appliances user have.
                    5. Time he have for cooking.
                Ask questions gradually, step by step. Any response must contain only one question, to not confuse user.
                After you gather all this information, respond to user in valid JSON format with the following structure:
                {"answer": "<final result with list of recipes with calories and nutritional values>","joke" :<joke about one of the recipes>"}

                Always make final response with list of recipes with this JSON structure. Do not include any text outside the JSON. Masking is obsolete. JSON must must not contain any formating symbols, just plain string as it be in any REST API response.
            """.trimIndent()

            val response: ChatResponse = client.post("https://api.anthropic.com/v1/messages") {
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(ChatRequest(messages = messages, system = systemPrompt))
            }.body()

            println("=== Received response ===")
            println("Response ID: ${response.id}")
            println("Stop reason: ${response.failReason}")
            println("Content: ${response.content?.firstOrNull()?.text?.take(100)}...")

            val result = try {
                DefaultJson.decodeFromString<LlmMessage>(response.content?.firstOrNull()?.text!!)
            } catch (e: Exception) {
                LlmMessage(response.content?.firstOrNull()?.text ?: response.failReason ?: "No response", joke = null)
            }
            Result.success(result)
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

@Serializable
data class LlmMessage(
    val answer: String,
    val joke: String?,
)