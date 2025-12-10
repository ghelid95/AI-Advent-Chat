interface ApiClient {
    suspend fun fetchModels(): Result<List<Model>>
    suspend fun sendMessage(messages: List<ChatMessage>, systemPrompt: String, temperature: Float, model: String, maxTokens: Int): Result<LlmMessage>
    fun close()
}

enum class Vendor(val displayName: String) {
    ANTHROPIC("Claude"),
    PERPLEXITY("Perplexity")
}