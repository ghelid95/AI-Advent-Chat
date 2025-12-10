import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Message(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val usage: UsageInfo? = null
)

class ChatViewModel(apiKey: String, vendor: Vendor = Vendor.ANTHROPIC) {
    private var client: ApiClient = createClient(vendor, apiKey)
    private var currentApiKey = apiKey

    val messages = mutableStateListOf<Message>()
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val joke = mutableStateOf<String?>(null)
    val systemPrompt = mutableStateOf("")
    val temperature = mutableStateOf(1.0f)
    val maxTokens = mutableStateOf(1024)
    val selectedModel = mutableStateOf("claude-sonnet-4-20250514")
    val availableModels = mutableStateListOf<Model>()
    val isLoadingModels = mutableStateOf(true)
    val currentVendor = mutableStateOf(vendor)
    val sessionStats = mutableStateOf(SessionStats())
    val lastResponseTime = mutableStateOf<Long?>(null)
    val previousResponseTime = mutableStateOf<Long?>(null)

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

                val result = client.sendMessage(chatMessages, systemPrompt.value, temperature.value, selectedModel.value, maxTokens.value)

                result.onSuccess { response ->
                    // Update the last user message with usage info
                    response.usage?.let { usage ->
                        val lastUserMessageIndex = messages.indexOfLast { it.isUser }
                        if (lastUserMessageIndex != -1) {
                            val lastUserMessage = messages[lastUserMessageIndex]
                            messages[lastUserMessageIndex] = lastUserMessage.copy(usage = usage)
                        }
                    }

                    messages.add(Message(response.answer, isUser = false, usage = response.usage))
                    joke.value = response.joke

                    // Update session stats and last response time
                    response.usage?.let { usage ->
                        val currentStats = sessionStats.value
                        sessionStats.value = SessionStats(
                            totalInputTokens = currentStats.totalInputTokens + usage.inputTokens,
                            totalOutputTokens = currentStats.totalOutputTokens + usage.outputTokens,
                            totalCost = currentStats.totalCost + usage.estimatedCost,
                            lastRequestTimeMs = usage.requestTimeMs
                        )
                        // Store current as previous before updating
                        previousResponseTime.value = lastResponseTime.value
                        lastResponseTime.value = usage.requestTimeMs
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

        // Reset last response time when switching vendors
        lastResponseTime.value = null
        previousResponseTime.value = null

        clearChat()
    }

    fun cleanup() {
        client.close()
    }
}