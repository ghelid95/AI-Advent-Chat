package data

interface ApiClient {
    suspend fun fetchModels(): Result<List<Model>>
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        model: String,
        maxTokens: Int,
        tools: List<ClaudeTool>? = null
    ): Result<LlmMessage>
    fun close()

    // Check if this vendor supports tools
    fun supportsTools(): Boolean
}

enum class Vendor(val displayName: String) {
    ANTHROPIC("Claude"),
    PERPLEXITY("Perplexity"),
    OLLAMA("Ollama")
}
