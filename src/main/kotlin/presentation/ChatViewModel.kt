package presentation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    // Compaction settings and state
    val compactionEnabled = mutableStateOf(false)
    val messagesSinceLastCompaction = mutableStateOf(0)
    val isCompacting = mutableStateOf(false)
    private val internalMessages = mutableStateListOf<InternalMessage>()
    private val summaryExpansionState = mutableStateMapOf<Long, Boolean>()

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

    private fun syncToUIMessages() {
        messages.clear()
        internalMessages.forEach { internalMsg ->
            when (internalMsg) {
                is InternalMessage.Regular -> {
                    messages.add(Message(
                        content = internalMsg.content,
                        isUser = internalMsg.isUser,
                        timestamp = internalMsg.timestamp,
                        usage = internalMsg.usage
                    ))
                }
                is InternalMessage.CompactedSummary -> {
                    if (summaryExpansionState[internalMsg.timestamp] == true) {
                        // Show originals when expanded
                        internalMsg.originalMessages.forEach { original ->
                            messages.add(Message(
                                content = original.content,
                                isUser = original.isUser,
                                timestamp = original.timestamp,
                                usage = original.usage
                            ))
                        }
                    } else {
                        // Show expandable placeholder
                        messages.add(Message(
                            content = ">${internalMsg.summaryContent}\n[${internalMsg.originalMessages.size} messages compacted - Click to expand]",
                            isUser = false,
                            timestamp = internalMsg.timestamp,
                            usage = internalMsg.aggregatedUsage
                        ))
                    }
                }
            }
        }
    }

    private fun buildAPIMessageList(): List<ChatMessage> {
        return internalMessages.map { internalMsg ->
            when (internalMsg) {
                is InternalMessage.Regular -> ChatMessage(
                    role = if (internalMsg.isUser) "user" else "assistant",
                    content = internalMsg.content
                )
                is InternalMessage.CompactedSummary -> ChatMessage(
                    role = "assistant",
                    content = "[Previous conversation summary: ${internalMsg.summaryContent}]"
                )
            }
        }
    }

    private fun shouldTriggerAutoCompaction(): Boolean {
        return compactionEnabled.value &&
               messagesSinceLastCompaction.value >= 10 &&
               internalMessages.filterIsInstance<InternalMessage.Regular>().size >= 10
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        // Add user message to internal storage
        val userMessage = InternalMessage.Regular(
            content = content,
            isUser = true
        )
        internalMessages.add(userMessage)
        syncToUIMessages()

        isLoading.value = true
        errorMessage.value = null

        scope.launch {
            try {
                // Build API messages including compacted summaries
                val chatMessages = buildAPIMessageList()

                val result = client.sendMessage(chatMessages, systemPrompt.value, temperature.value, selectedModel.value, maxTokens.value)

                result.onSuccess { response ->
                    // Update the last user message with usage info
                    response.usage?.let { usage ->
                        val lastUserIndex = internalMessages.indexOfLast {
                            it is InternalMessage.Regular && it.isUser
                        }
                        if (lastUserIndex != -1) {
                            val lastUser = internalMessages[lastUserIndex] as InternalMessage.Regular
                            internalMessages[lastUserIndex] = lastUser.copy(usage = usage)
                        }
                    }

                    // Add assistant response to internal storage
                    val assistantMessage = InternalMessage.Regular(
                        content = response.answer,
                        isUser = false,
                        usage = response.usage
                    )
                    internalMessages.add(assistantMessage)
                    syncToUIMessages()

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

                    // Increment message counter (user + assistant = 2)
                    messagesSinceLastCompaction.value += 2

                    // Check if auto-compaction should trigger
                    if (shouldTriggerAutoCompaction()) {
                        performCompaction(auto = true)
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

    private fun buildCompactionPrompt(messagesToCompact: List<InternalMessage.Regular>): String {
        val conversationText = messagesToCompact.joinToString("\n\n") { msg ->
            val role = if (msg.isUser) "User" else "Assistant"
            "$role: ${msg.content}"
        }

        return """
You are summarizing a chat conversation for context preservation. Create a concise summary that:
1. Captures key topics discussed and decisions made
2. Preserves important context needed for future conversation
3. Maintains chronological flow
4. Focuses on facts and substance

Format as 3-5 paragraphs maximum.

Conversation to summarize:
$conversationText

Provide ONLY the summary text.
""".trimIndent()
    }

    private data class CompactionResult(
        val summary: String,
        val usage: UsageInfo?
    )

    private suspend fun requestCompactionFromLLM(
        messagesToCompact: List<InternalMessage.Regular>
    ): Result<CompactionResult> {
        return try {
            val compactionPrompt = buildCompactionPrompt(messagesToCompact)
            val chatMessages = listOf(ChatMessage(role = "user", content = compactionPrompt))

            val result = client.sendMessage(
                messages = chatMessages,
                systemPrompt = "You are a helpful assistant that creates concise, accurate summaries.",
                temperature = 0.3f,
                model = selectedModel.value,
                maxTokens = 1024
            )

            result.map { CompactionResult(it.answer, it.usage) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun aggregateUsageInfo(messages: List<InternalMessage.Regular>): UsageInfo {
        var totalInputTokens = 0
        var totalOutputTokens = 0
        var totalInputCost = 0.0
        var totalOutputCost = 0.0
        var totalRequestTime = 0L

        messages.forEach { msg ->
            msg.usage?.let { usage ->
                totalInputTokens += usage.inputTokens
                totalOutputTokens += usage.outputTokens
                totalInputCost += usage.estimatedInputCost
                totalOutputCost += usage.estimatedOutputCost
                totalRequestTime += usage.requestTimeMs
            }
        }

        return UsageInfo(
            inputTokens = totalInputTokens,
            outputTokens = totalOutputTokens,
            totalTokens = totalInputTokens + totalOutputTokens,
            estimatedInputCost = totalInputCost,
            estimatedOutputCost = totalOutputCost,
            estimatedCost = totalInputCost + totalOutputCost,
            requestTimeMs = totalRequestTime
        )
    }

    private fun selectMessagesForCompaction(auto: Boolean): List<InternalMessage.Regular> {
        val regularMessages = internalMessages.filterIsInstance<InternalMessage.Regular>()
        return if (auto) {
            regularMessages.take(10) // Auto: compact oldest 10
        } else {
            if (regularMessages.size > 4) regularMessages.dropLast(4) else emptyList()
        }
    }

    suspend fun performCompaction(auto: Boolean = false) {
        if (isCompacting.value) return

        try {
            isCompacting.value = true

            val messagesToCompact = selectMessagesForCompaction(auto)
            if (messagesToCompact.isEmpty()) {
                if (!auto) errorMessage.value = "Not enough messages to compact"
                return
            }

            val summaryResult = requestCompactionFromLLM(messagesToCompact)

            summaryResult.onSuccess { compactionResult ->
                val compactedSummary = InternalMessage.CompactedSummary(
                    summaryContent = compactionResult.summary,
                    originalMessages = messagesToCompact,
                    aggregatedUsage = aggregateUsageInfo(messagesToCompact)
                )

                // Replace originals with summary
                val firstIndex = internalMessages.indexOfFirst {
                    it is InternalMessage.Regular && it.timestamp == messagesToCompact.first().timestamp
                }
                val lastIndex = internalMessages.indexOfLast {
                    it is InternalMessage.Regular && it.timestamp == messagesToCompact.last().timestamp
                }

                if (firstIndex != -1 && lastIndex != -1) {
                    repeat(lastIndex - firstIndex + 1) { internalMessages.removeAt(firstIndex) }
                    internalMessages.add(firstIndex, compactedSummary)
                    messagesSinceLastCompaction.value = 0
                    syncToUIMessages()
                }

                // Add compaction request usage to session stats
                compactionResult.usage?.let { usage ->
                    val currentStats = sessionStats.value
                    sessionStats.value = SessionStats(
                        totalInputTokens = currentStats.totalInputTokens + usage.inputTokens,
                        totalOutputTokens = currentStats.totalOutputTokens + usage.outputTokens,
                        totalCost = currentStats.totalCost + usage.estimatedCost,
                        lastRequestTimeMs = currentStats.lastRequestTimeMs
                    )
                }
            }.onFailure { error ->
                errorMessage.value = "Compaction failed: ${error.message}"
            }
        } finally {
            isCompacting.value = false
        }
    }

    fun performManualCompaction() {
        scope.launch { performCompaction(auto = false) }
    }

    fun canCompact(): Boolean {
        val regularCount = internalMessages.filterIsInstance<InternalMessage.Regular>().size
        return regularCount > 4 && !isCompacting.value && !isLoading.value
    }

    fun toggleSummaryExpansion(timestamp: Long) {
        val isCurrentlyExpanded = summaryExpansionState[timestamp] ?: false
        summaryExpansionState[timestamp] = !isCurrentlyExpanded
        syncToUIMessages()
    }

    fun clearChat() {
        messages.clear()
        internalMessages.clear()
        summaryExpansionState.clear()
        messagesSinceLastCompaction.value = 0
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
