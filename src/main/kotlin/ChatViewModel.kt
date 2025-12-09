import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Message(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatViewModel(apiKey: String, vendor: Vendor = Vendor.ANTHROPIC) {
    private var client: ApiClient = createClient(vendor, apiKey)
    private var currentApiKey = apiKey

    val messages = mutableStateListOf<Message>()
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val joke = mutableStateOf<String?>(null)
    val systemPrompt = mutableStateOf("""
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
    """.trimIndent())
    val temperature = mutableStateOf(1.0f)
    val selectedModel = mutableStateOf("claude-sonnet-4-20250514")
    val availableModels = mutableStateListOf<Model>()
    val isLoadingModels = mutableStateOf(true)
    val currentVendor = mutableStateOf(vendor)
    val sessionStats = mutableStateOf(SessionStats())

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun createClient(vendor: Vendor, apiKey: String): ApiClient {
        return when (vendor) {
            Vendor.ANTHROPIC -> ClaudeClient(apiKey)
            Vendor.PERPLEXITY -> PerplexityClient(apiKey)
        }
    }

    init {
        loadModels()
    }

    private fun loadModels() {
        scope.launch {
            try {
                val result = client.fetchModels()
                result.onSuccess { models ->
                    availableModels.clear()
                    availableModels.addAll(models)
                    println("Successfully loaded ${models.size} models")
                }.onFailure { error ->
                    println("Failed to load models: ${error.message}")
                }
            } catch (e: Exception) {
                println("Exception loading models: ${e.message}")
            } finally {
                isLoadingModels.value = false
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        messages.add(Message(content, isUser = true))
        isLoading.value = true
        errorMessage.value = null

        scope.launch {
            try {
                val chatMessages = messages.map {
                    ChatMessage(
                        role = if (it.isUser) "user" else "assistant",
                        content = it.content
                    )
                }

                val result = client.sendMessage(chatMessages, systemPrompt.value, temperature.value, selectedModel.value)

                result.onSuccess { response ->
                    messages.add(Message(response.answer, isUser = false))
                    joke.value = response.joke

                    // Update session stats
                    response.usage?.let { usage ->
                        val currentStats = sessionStats.value
                        sessionStats.value = SessionStats(
                            totalInputTokens = currentStats.totalInputTokens + usage.inputTokens,
                            totalOutputTokens = currentStats.totalOutputTokens + usage.outputTokens,
                            totalCost = currentStats.totalCost + usage.estimatedCost,
                            lastRequestTimeMs = usage.requestTimeMs
                        )
                    }
                }.onFailure { error ->
                    errorMessage.value = "Error: ${error.message}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun clearChat() {
        messages.clear()
        errorMessage.value = null
        sessionStats.value = SessionStats()
    }

    fun getChatHistory(): String {
        return messages.joinToString("\n\n") { message ->
            val role = if (message.isUser) "User" else "Assistant"
            "<$role>: ${message.content}"
        }
    }

    fun switchVendor(vendor: Vendor, apiKey: String) {
        // Close old client
        client.close()

        // Create new client
        currentVendor.value = vendor
        currentApiKey = apiKey
        client = createClient(vendor, apiKey)

        // Reset models
        availableModels.clear()
        isLoadingModels.value = true

        // Set appropriate default model
        selectedModel.value = when (vendor) {
            Vendor.ANTHROPIC -> "claude-sonnet-4-20250514"
            Vendor.PERPLEXITY -> "sonar"
        }

        // Load new models
        loadModels()

        clearChat()
    }

    fun cleanup() {
        client.close()
    }
}