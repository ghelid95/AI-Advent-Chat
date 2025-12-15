package presentation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import data.*
import data.mcp.McpServerManager
import data.mcp.McpTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ChatViewModel(apiKey: String, vendor: Vendor = Vendor.ANTHROPIC) {
    private var client: ApiClient = createClient(vendor, apiKey)
    private var currentApiKey = apiKey

    val messages = mutableStateListOf<Message>()
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val joke = mutableStateOf<String?>(null)
    val systemPrompt = mutableStateOf("")
    val temperature = mutableStateOf(0.1f)
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

    // Session management
    private val sessionStorage = SessionStorage()
    val currentSessionId = mutableStateOf<String?>(null)
    val currentSessionName = mutableStateOf("New Session")
    val sessions = mutableStateListOf<SessionSummary>()
    private val sessionsListMutex = Mutex()

    // MCP support
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mcpServerManager = McpServerManager(scope)
    private val appSettingsStorage = AppSettingsStorage()
    val appSettings = mutableStateOf(AppSettings())
    val availableTools = mutableStateListOf<Pair<String, McpTool>>()

    // Store last tool_use blocks for sending with tool results
    private var lastToolUseBlocks: List<ContentBlock.ToolUse>? = null

    private fun createClient(vendor: Vendor, apiKey: String): ApiClient {
        return when (vendor) {
            Vendor.ANTHROPIC -> ClaudeClient(apiKey)
            Vendor.PERPLEXITY -> PerplexityClient(apiKey)
        }
    }

    init {
        loadModels()
        loadSessionsList()
        loadLastSession()
        loadMcpServers()
    }

    private fun loadMcpServers() {
        scope.launch {
            appSettings.value = appSettingsStorage.loadSettings()
            mcpServerManager.startServers(appSettings.value.mcpServers)
            updateAvailableTools()
        }
    }

    private fun updateAvailableTools() {
        availableTools.clear()
        availableTools.addAll(mcpServerManager.getAllTools())
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
                    // Claude uses "assistant" for summaries, Perplexity needs "system"
                    role = when (currentVendor.value) {
                        Vendor.ANTHROPIC -> "assistant"
                        Vendor.PERPLEXITY -> "system"
                    },
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

                // Build tools list if vendor supports tools
                val tools = if (client.supportsTools() && availableTools.isNotEmpty()) {
                    availableTools.map { (_, tool) ->
                        ClaudeTool(
                            name = tool.name,
                            description = tool.description,
                            inputSchema = tool.inputSchema
                        )
                    }
                } else null

                val result = client.sendMessage(
                    chatMessages,
                    systemPrompt.value,
                    temperature.value,
                    selectedModel.value,
                    maxTokens.value,
                    tools
                )

                result.onSuccess { response ->
                    // Check if tool use was requested
                    if (response.stopReason == "tool_use" && !response.toolUses.isNullOrEmpty()) {
                        handleToolUse(response)
                    } else {
                        handleTextResponse(response)
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

    private suspend fun handleToolUse(response: LlmMessage) {
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

        // Store tool_use blocks for later use
        lastToolUseBlocks = response.toolUses

        // Add assistant message with tool_use indication (for UI display)
        val toolNames = response.toolUses?.joinToString(", ") { it.name } ?: ""
        val assistantMessage = InternalMessage.Regular(
            content = "[Using tools: $toolNames]",
            isUser = false,
            usage = response.usage
        )
        internalMessages.add(assistantMessage)
        syncToUIMessages()

        // Execute each tool
        val toolResults = mutableListOf<ContentBlock.ToolResult>()
        response.toolUses?.forEach { toolUse ->
            println("[MCP] Executing tool: ${toolUse.name}")

            // Find which server has this tool
            val serverToolPair = availableTools.find { it.second.name == toolUse.name }
            if (serverToolPair != null) {
                val (serverId, _) = serverToolPair

                // Execute tool
                val result = mcpServerManager.callTool(serverId, toolUse.name, toolUse.input)
                result.onSuccess { mcpResult ->
                    val content = mcpResult.content.joinToString("\n") { it.text ?: "" }
                    toolResults.add(
                        ContentBlock.ToolResult(
                            toolUseId = toolUse.id,
                            content = content,
                            isError = mcpResult.isError
                        )
                    )

                    // Add tool result to UI
                    val toolResultMessage = InternalMessage.Regular(
                        content = "Tool '${toolUse.name}' result:\n$content",
                        isUser = false
                    )
                    internalMessages.add(toolResultMessage)
                    syncToUIMessages()
                }.onFailure { error ->
                    println("[MCP] Tool execution failed: ${error.message}")
                    toolResults.add(
                        ContentBlock.ToolResult(
                            toolUseId = toolUse.id,
                            content = "Error: ${error.message}",
                            isError = true
                        )
                    )

                    // Add error to UI
                    val errorMessage = InternalMessage.Regular(
                        content = "Tool '${toolUse.name}' error: ${error.message}",
                        isUser = false
                    )
                    internalMessages.add(errorMessage)
                    syncToUIMessages()
                }
            } else {
                println("[MCP] Tool not found: ${toolUse.name}")
                toolResults.add(
                    ContentBlock.ToolResult(
                        toolUseId = toolUse.id,
                        content = "Error: Tool not found",
                        isError = true
                    )
                )
            }
        }

        // Send tool results back to LLM for final response
        if (toolResults.isNotEmpty()) {
            sendMessageWithToolResults(toolResults)
        }
    }

    private suspend fun sendMessageWithToolResults(toolResults: List<ContentBlock.ToolResult>) {
        try {
            // Build messages including the assistant's tool_use and user's tool_result
            val chatMessages = buildAPIMessageList().toMutableList()

            // Add assistant message with tool_use blocks
            if (lastToolUseBlocks != null && lastToolUseBlocks!!.isNotEmpty()) {
                chatMessages.add(ChatMessage(
                    role = "assistant",
                    content = ChatMessageContent.ContentBlocks(lastToolUseBlocks!!)
                ))
            }

            // Add user message with tool_result blocks
            chatMessages.add(ChatMessage(
                role = "user",
                content = ChatMessageContent.ContentBlocks(toolResults)
            ))

            val result = client.sendMessage(
                chatMessages,
                systemPrompt.value,
                temperature.value,
                selectedModel.value,
                maxTokens.value,
                null // No tools in follow-up request
            )

            result.onSuccess { response ->
                // Clear stored tool_use blocks
                lastToolUseBlocks = null
                handleTextResponse(response)
            }.onFailure { error ->
                errorMessage.value = "Error: ${error.message}"
                lastToolUseBlocks = null
            }
        } catch (e: Exception) {
            errorMessage.value = "Error: ${e.message}"
            lastToolUseBlocks = null
        }
    }

    private suspend fun handleTextResponse(response: LlmMessage) {
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

        // Auto-save session after successful message
        saveCurrentSession()
    }

    fun updateMcpServers(newServers: List<data.mcp.McpServerConfig>) {
        scope.launch {
            appSettings.value = appSettings.value.copy(mcpServers = newServers)
            appSettingsStorage.saveSettings(appSettings.value)
            mcpServerManager.startServers(newServers)
            updateAvailableTools()
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

                // Auto-save session after compaction
                saveCurrentSession()
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
        val currentId = currentSessionId.value

        messages.clear()
        internalMessages.clear()
        summaryExpansionState.clear()
        messagesSinceLastCompaction.value = 0
        errorMessage.value = null
        sessionStats.value = SessionStats()

        // Delete the current session file instead of saving empty state
        if (currentId != null) {
            deleteSession(currentId)
        }
    }

    fun getChatHistory(): String {
        return messages.joinToString("\n\n") { message ->
            val role = if (message.isUser) "User" else "Assistant"
            "<$role>: ${message.content}"
        }
    }

    fun switchVendor(vendor: Vendor, apiKey: String) {
        // Save current session before switching
        saveCurrentSession()

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

        // Create new session with the new vendor
        createNewSession("New ${vendor.displayName} Session")
    }

    fun cleanup() {
        saveCurrentSession() // Save before cleanup
        scope.launch {
            mcpServerManager.stopAll()
        }
        client.close()
    }

    // Session Management Methods

    private fun loadSessionsList() {
        scope.launch {
            // Use mutex to prevent concurrent updates to sessions list
            sessionsListMutex.withLock {
                val allSessions = sessionStorage.getAllSessions()
                // Update list on main thread to avoid race conditions
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    sessions.clear()
                    sessions.addAll(allSessions)
                }
            }
        }
    }

    private fun loadLastSession() {
        scope.launch {
            val lastSessionId = sessionStorage.getLastActiveSessionId()
            if (lastSessionId != null) {
                loadSession(lastSessionId)
            } else {
                // Create a new session if no previous session exists
                createNewSession()
            }
        }
    }

    fun createNewSession(name: String = "New Session") {
        scope.launch {
            val sessionId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            val newSession = SessionData(
                id = sessionId,
                name = name,
                createdAt = timestamp,
                lastModified = timestamp,
                settings = SessionSettings(
                    vendor = currentVendor.value,
                    selectedModel = selectedModel.value,
                    systemPrompt = systemPrompt.value,
                    temperature = temperature.value,
                    maxTokens = maxTokens.value,
                    compactionEnabled = compactionEnabled.value
                ),
                messages = emptyList(),
                sessionStats = SessionStats()
            )

            sessionStorage.saveSession(newSession)

            currentSessionId.value = sessionId
            currentSessionName.value = name

            // Clear current chat
            internalMessages.clear()
            messages.clear()
            summaryExpansionState.clear()
            messagesSinceLastCompaction.value = 0
            errorMessage.value = null
            sessionStats.value = SessionStats()

            loadSessionsList()
        }
    }

    fun loadSession(sessionId: String) {
        scope.launch {
            val session = sessionStorage.loadSession(sessionId)
            if (session != null) {
                // Switch vendor if needed (without clearing chat)
                if (currentVendor.value != session.settings.vendor) {
                    client.close()
                    currentApiKey = when (session.settings.vendor) {
                        Vendor.ANTHROPIC -> System.getenv("CLAUDE_API_KEY") ?: ""
                        Vendor.PERPLEXITY -> System.getenv("PERPLEXITY_API_KEY") ?: ""
                    }
                    client = createClient(session.settings.vendor, currentApiKey)
                    availableModels.clear()
                    isLoadingModels.value = true
                    loadModels()
                }
                // Restore settings
                currentVendor.value = session.settings.vendor
                selectedModel.value = session.settings.selectedModel
                systemPrompt.value = session.settings.systemPrompt
                temperature.value = session.settings.temperature
                maxTokens.value = session.settings.maxTokens
                compactionEnabled.value = session.settings.compactionEnabled

                // Restore messages
                internalMessages.clear()
                internalMessages.addAll(session.messages.map { it.toInternal() })
                syncToUIMessages()

                // Restore session stats
                sessionStats.value = session.sessionStats

                // Update current session
                currentSessionId.value = session.id
                currentSessionName.value = session.name


            }
        }
    }

    fun saveCurrentSession() {
        val sessionId = currentSessionId.value ?: return

        // Capture current values BEFORE launching coroutine to avoid race conditions
        val capturedVendor = currentVendor.value
        val capturedModel = selectedModel.value
        val capturedSystemPrompt = systemPrompt.value
        val capturedTemperature = temperature.value
        val capturedMaxTokens = maxTokens.value
        val capturedCompactionEnabled = compactionEnabled.value
        val capturedSessionName = currentSessionName.value
        val capturedMessages = internalMessages.map { it.toSerializable() }
        val capturedStats = sessionStats.value

        scope.launch {
            val session = SessionData(
                id = sessionId,
                name = capturedSessionName,
                createdAt = sessionStorage.loadSession(sessionId)?.createdAt ?: System.currentTimeMillis(),
                lastModified = System.currentTimeMillis(),
                settings = SessionSettings(
                    vendor = capturedVendor,
                    selectedModel = capturedModel,
                    systemPrompt = capturedSystemPrompt,
                    temperature = capturedTemperature,
                    maxTokens = capturedMaxTokens,
                    compactionEnabled = capturedCompactionEnabled
                ),
                messages = capturedMessages,
                sessionStats = capturedStats
            )

            sessionStorage.saveSession(session)
            loadSessionsList()
        }
    }

    fun deleteSession(sessionId: String) {
        scope.launch {
            val success = sessionStorage.deleteSession(sessionId)
            if (success) {
                loadSessionsList()

                // If we deleted the current session, create a new one
                if (currentSessionId.value == sessionId) {
                    createNewSession()
                }
            }
        }
    }

    fun renameSession(sessionId: String, newName: String) {
        scope.launch {
            val session = sessionStorage.loadSession(sessionId)
            if (session != null) {
                val updatedSession = session.copy(
                    name = newName,
                    lastModified = System.currentTimeMillis()
                )
                sessionStorage.saveSession(updatedSession)

                if (currentSessionId.value == sessionId) {
                    currentSessionName.value = newName
                }

                loadSessionsList()
            }
        }
    }
}
