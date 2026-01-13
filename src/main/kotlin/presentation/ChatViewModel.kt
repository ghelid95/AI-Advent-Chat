package presentation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import data.*
import data.commands.ChatCommand
import data.commands.CommandParseResult
import data.commands.CommandResult
import data.mcp.McpServerManager
import data.mcp.McpTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Unified state for the Chat UI
 */
data class ChatUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val joke: String? = null,
    val systemPrompt: String = "",
    val temperature: Float = 0.1f,
    val maxTokens: Int = 1024,
    val selectedModel: String = "claude-sonnet-4-20250514",
    val isLoadingModels: Boolean = true,
    val currentVendor: Vendor = Vendor.ANTHROPIC,
    val sessionStats: SessionStats = SessionStats(),
    val lastResponseTime: Long? = null,
    val previousResponseTime: Long? = null,
    val compactionEnabled: Boolean = false,
    val messagesSinceLastCompaction: Int = 0,
    val isCompacting: Boolean = false,
    val currentSessionId: String? = null,
    val currentSessionName: String = "New Session",
    val appSettings: AppSettings = AppSettings(),
    val pipelineEnabled: Boolean = true,
    val pipelineMaxIterations: Int = 5,
    val embeddingsEnabled: Boolean = false,
    val selectedEmbeddingFile: String? = null,
    val embeddingTopK: Int = 3,
    val embeddingThreshold: Float = 0.5f,
    val codeAssistantEnabled: Boolean = false,
    val codeAssistantWorkingDir: String? = null,
    val codeAssistantSettings: CodeAssistantSettings = CodeAssistantSettings(),
    val showTaskReminderDialog: Boolean = false,
    val taskReminderSummary: String = ""
)

class ChatViewModel(apiKey: String, vendor: Vendor = Vendor.ANTHROPIC) {
    private var client: ApiClient = createClient(vendor, apiKey)
    private var currentApiKey = apiKey

    // Unified state
    val uiState = mutableStateOf(ChatUiState(currentVendor = vendor))

    // Helper function to update state immutably
    private fun updateState(block: ChatUiState.() -> ChatUiState) {
        uiState.value = block(uiState.value)
    }

    // Lists remain separate for performance
    val messages = mutableStateListOf<Message>()
    val availableModels = mutableStateListOf<Model>()
    val sessions = mutableStateListOf<SessionSummary>()
    val availableTools = mutableStateListOf<Pair<String, McpTool>>()
    private val internalMessages = mutableStateListOf<InternalMessage>()
    private val summaryExpansionState = mutableStateMapOf<Long, Boolean>()

    // Session management
    private val sessionStorage = SessionStorage()
    private val sessionsListMutex = Mutex()

    // MCP support
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mcpServerManager = McpServerManager(scope)
    private val appSettingsStorage = AppSettingsStorage()

    // Store last tool_use blocks for sending with tool results
    private var lastToolUseBlocks: List<ContentBlock.ToolUse>? = null

    // Pipeline support
    private var mcpPipeline: McpPipeline? = null

    // Embeddings support
    private var ollamaClient: OllamaClient? = null

    // Code assistant support
    private val fileSearchService = data.codeassistant.FileSearchService()
    private val contentSearchService = data.codeassistant.ContentSearchService()
    private val projectAnalysisService = data.codeassistant.ProjectAnalysisService(fileSearchService)
    private val autoContextService = data.codeassistant.AutoContextService(fileSearchService, contentSearchService)

    // Git support
    private val gitRepositoryService = data.git.GitRepositoryService()
    private val gitContextService = data.git.GitContextService(gitRepositoryService)

    // Project documentation RAG support
    private val projectDocsService = data.projectdocs.ProjectDocsService(ollamaClient = null)

    // Command support
    private val commandParser = data.commands.CommandParser()
    private val commandExecutor = data.commands.CommandExecutor(projectAnalysisService, fileSearchService, contentSearchService, gitRepositoryService)

    // Task reminder support
    private var taskReminderManager: TaskReminderManager? = null

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
            val settings = appSettingsStorage.loadSettings()
            updateState { copy(appSettings = settings) }
            mcpServerManager.startServers(uiState.value.appSettings.mcpServers)
            updateAvailableTools()

            // Load pipeline settings
            updateState {
                copy(
                    pipelineEnabled = uiState.value.appSettings.pipelineEnabled,
                    pipelineMaxIterations = uiState.value.appSettings.pipelineMaxIterations,
                    embeddingsEnabled = uiState.value.appSettings.embeddingsEnabled,
                    selectedEmbeddingFile = uiState.value.appSettings.selectedEmbeddingFile,
                    embeddingTopK = uiState.value.appSettings.embeddingTopK,
                    embeddingThreshold = uiState.value.appSettings.embeddingThreshold,
                    codeAssistantEnabled = uiState.value.appSettings.codeAssistantSettings.enabled,
                    codeAssistantWorkingDir = uiState.value.appSettings.codeAssistantSettings.workingDirectory,
                    codeAssistantSettings = uiState.value.appSettings.codeAssistantSettings
                )
            }

            // Initialize Ollama client if embeddings or project docs enabled
            if (uiState.value.embeddingsEnabled || uiState.value.appSettings.codeAssistantSettings.projectDocsEnabled) {
                ollamaClient = OllamaClient()
                projectDocsService.updateOllamaClient(ollamaClient!!)
            }

//            initializeTaskReminder()
        }
    }

    private fun initializeTaskReminder() {
        // Only initialize if client is ClaudeClient
        if (client is ClaudeClient) {
            taskReminderManager = TaskReminderManager(
                client = client as ClaudeClient,
                mcpServerManager = mcpServerManager,
                scope = scope
            )
            taskReminderManager?.onTaskSummaryReceived = { summary ->
                updateState { copy(taskReminderSummary = summary, showTaskReminderDialog = true) }
            }
            // Start the reminder immediately
            taskReminderManager?.startReminder(availableTools.toList())
        }
    }

    fun stopTaskReminder() {
        taskReminderManager?.stopReminder()
    }

    fun startTaskReminder() {
        taskReminderManager?.startReminder(availableTools.toList())
    }

    fun dismissTaskReminderDialog() {
        updateState { copy(showTaskReminderDialog = false) }
    }

    fun clearJoke() {
        updateState { copy(joke = null) }
    }

    fun updateSettingsFields(
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        selectedModel: String,
        compactionEnabled: Boolean
    ) {
        updateState {
            copy(
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = maxTokens,
                selectedModel = selectedModel,
                compactionEnabled = compactionEnabled
            )
        }
    }

    private fun updateAvailableTools() {
        availableTools.clear()
        availableTools.addAll(mcpServerManager.getAllTools())
    }

    /**
     * Updates the last user message in the conversation with usage information.
     * This is used to track token usage and costs for user messages.
     */
    private fun updateLastUserMessageWithUsage(usage: UsageInfo) {
        val lastUserIndex = internalMessages.indexOfLast {
            it is InternalMessage.Regular && it.isUser
        }
        if (lastUserIndex != -1) {
            val lastUser = internalMessages[lastUserIndex] as InternalMessage.Regular
            internalMessages[lastUserIndex] = lastUser.copy(usage = usage)
        }
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
                updateState { copy(isLoadingModels = false) }
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
                    role = when (uiState.value.currentVendor) {
                        Vendor.ANTHROPIC -> "assistant"
                        Vendor.PERPLEXITY -> "system"
                    },
                    content = "[Previous conversation summary: ${internalMsg.summaryContent}]"
                )
            }
        }
    }

    private fun shouldTriggerAutoCompaction(): Boolean {
        return uiState.value.compactionEnabled &&
               uiState.value.messagesSinceLastCompaction >= 10 &&
               internalMessages.filterIsInstance<InternalMessage.Regular>().size >= 10
    }

    private suspend fun enrichMessageWithProjectDocs(content: String): String {
        val settings = uiState.value.codeAssistantSettings

        // Skip if disabled or no working directory
        if (!settings.enabled || !settings.projectDocsEnabled ||
            settings.workingDirectory.isNullOrBlank() || ollamaClient == null) {
            return content
        }

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val workingDir = java.io.File(settings.workingDirectory)

                // Get or initialize project docs embedding
                var embeddingFile = projectDocsService.getProjectDocsEmbeddingFile(workingDir)

                // Check staleness
                val isStale = embeddingFile == null ||
                             projectDocsService.isProjectDocsStale(workingDir)

                if (isStale) {
                    // Silent background initialization
                    println("[ProjectDocs] Initializing project documentation embeddings...")
                    val result = projectDocsService.initializeProjectDocs(workingDir)
                    result.onSuccess { file ->
                        embeddingFile = file
                        println("[ProjectDocs] Successfully initialized: ${file.name}")
                        // Update settings metadata
                        updateProjectDocsMetadata(workingDir)
                    }.onFailure { error ->
                        println("[ProjectDocs] Initialization failed: ${error.message}")
                        // Don't block user - continue without project docs
                        return@withContext content
                    }
                }

                if (embeddingFile == null || !embeddingFile.exists()) {
                    return@withContext content
                }

                // Search project docs
                println("[ProjectDocs] Searching project documentation...")
                val searchResults = EmbeddingSearch.searchSimilarChunks(
                    query = content,
                    embeddingFile = embeddingFile,
                    ollamaClient = ollamaClient!!,
                    topK = 2,  // User preference: 2 chunks
                    threshold = 0.6f,  // Higher threshold for documentation
                    useMmr = true,
                    mmrLambda = 0.7f  // Favor relevance over diversity
                )

                if (searchResults.isEmpty()) {
                    println("[ProjectDocs] No relevant documentation found")
                    return@withContext content
                }

                // Format context
                val contextString = buildString {
                    appendLine("=== Project Documentation Context ===")
                    appendLine()
                    searchResults.forEachIndexed { index, result ->
                        appendLine("Doc ${index + 1}: ${result.embeddingFileName}")
                        appendLine("  Similarity: ${"%.3f".format(result.similarity)}")
                        appendLine()
                        appendLine(result.chunk.text)
                        appendLine()
                    }
                    appendLine("=== End Project Documentation ===")
                    appendLine()
                }

                return@withContext "$contextString$content"

            } catch (e: Exception) {
                println("[ProjectDocs] Error enriching with project docs: ${e.message}")
                e.printStackTrace()
                return@withContext content
            }
        }
    }

    private fun updateProjectDocsMetadata(workingDir: java.io.File) {
        scope.launch {
            try {
                val docs = projectDocsService.discoverProjectDocs(workingDir)
                val newSettings = uiState.value.codeAssistantSettings.copy(
                    projectDocsLastInitialized = System.currentTimeMillis(),
                    projectDocsSourceFiles = docs.map { it.path }
                )
                updateCodeAssistantSettings(newSettings)
            } catch (e: Exception) {
                println("[ProjectDocs] Failed to update metadata: ${e.message}")
            }
        }
    }

    private suspend fun enrichMessageWithEmbeddings(content: String): String {
        if (!uiState.value.embeddingsEnabled || uiState.value.selectedEmbeddingFile == null || ollamaClient == null) {
            return content
        }

        try {
            val embeddingFile = java.io.File(uiState.value.selectedEmbeddingFile!!)
            if (!embeddingFile.exists()) {
                println("[Embeddings] Embedding file not found: ${embeddingFile.absolutePath}")
                return content
            }

            println("[Embeddings] Searching for relevant context...")
            val searchResults = EmbeddingSearch.searchSimilarChunks(
                query = content,
                embeddingFile = embeddingFile,
                ollamaClient = ollamaClient!!,
                topK = uiState.value.embeddingTopK,
                threshold = uiState.value.embeddingThreshold,
                useMmr = true,  // Enable MMR for diverse results
                mmrLambda = 0.0f // Balance between relevance (1.0) and diversity (0.0)
            )

            if (searchResults.isEmpty()) {
                println("[Embeddings] No relevant chunks found above threshold")
                return content
            }

            val contextString = EmbeddingSearch.formatSearchResultsAsContext(searchResults)
            val instruction = """
                |
                |IMPORTANT: When answering the user's query below, you MUST:
                |1. Use the retrieved context above to inform your answer
                |2. Clearly indicate which source(s) you used (e.g., "Based on context from [source name]...")
                |3. Reference specific chunk numbers when citing information
                |4. If the context doesn't contain relevant information, state this clearly
                |
                """.trimMargin()
            return "$contextString$instruction\nUser Query: $content"
        } catch (e: Exception) {
            println("[Embeddings] Error during search: ${e.message}")
            e.printStackTrace()
            return content
        }
    }

    private suspend fun enrichMessageWithCodeContext(content: String): String {
        val settings = uiState.value.codeAssistantSettings

        if (!settings.enabled || settings.workingDirectory.isNullOrBlank()) {
            return content
        }

        if (!settings.autoContextEnabled) {
            return content
        }

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                println("[Code Assistant] Analyzing query for code references...")
                autoContextService.enrichQueryWithCodeContext(content, settings)
            } catch (e: Exception) {
                println("[Code Assistant] Error enriching with code context: ${e.message}")
                e.printStackTrace()
                content
            }
        }
    }

    private suspend fun enrichMessageWithGitContext(content: String): String {
        val settings = uiState.value.codeAssistantSettings

        if (!settings.gitEnabled || settings.workingDirectory.isNullOrBlank()) {
            return content
        }

        // Check if git keywords detected (if auto-detect enabled)
        if (settings.gitAutoDetectEnabled) {
            val hasGitReferences = autoContextService.detectGitReferences(content)
            if (!hasGitReferences) {
                return content // No git keywords, skip enrichment
            }
        }

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                println("[Git] Enriching query with git context...")
                gitContextService.enrichQueryWithGitContext(content, settings)
            } catch (e: Exception) {
                println("[Git] Error enriching with git context: ${e.message}")
                e.printStackTrace()
                content
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        updateState { copy(isLoading = true, errorMessage = null) }

        scope.launch {
            try {
                // Check if this is a command
                val parseResult = commandParser.parse(content)

                if (parseResult.isCommand) {
                    handleCommand(content, parseResult)
                    return@launch
                }

                // Normal message processing continues...
                // Enrich message with project documentation if enabled
                val enrichedWithProjectDocs = enrichMessageWithProjectDocs(content)

                // Enrich message with embeddings if enabled
                val enrichedWithEmbeddings = enrichMessageWithEmbeddings(enrichedWithProjectDocs)

                // Enrich message with code context if enabled
                val enrichedWithCodeContext = enrichMessageWithCodeContext(enrichedWithEmbeddings)

                // Enrich message with git context if enabled
                val fullyEnrichedContent = enrichMessageWithGitContext(enrichedWithCodeContext)

                // Add enriched user message to internal storage
                val userMessage = InternalMessage.Regular(
                    content = fullyEnrichedContent,
                    isUser = true
                )
                internalMessages.add(userMessage)
                syncToUIMessages()

                // Check if pipeline mode is enabled and tools are available
                if (uiState.value.pipelineEnabled && client.supportsTools() && availableTools.isNotEmpty()) {
                    executePipeline(fullyEnrichedContent)
                } else {
                    executeSingleRound()
                }
            } catch (e: Exception) {
                updateState { copy(errorMessage = "Error: ${e.message}", isLoading = false) }
            }
        }
    }

    private suspend fun handleCommand(originalInput: String, parseResult: data.commands.CommandParseResult) {
        try {
            // Add user command to messages
            val userMessage = InternalMessage.Regular(
                content = originalInput,
                isUser = true
            )
            internalMessages.add(userMessage)
            syncToUIMessages()

            // Handle parse errors
            if (parseResult.error != null) {
                val errorMessage = InternalMessage.Regular(
                    content = "[Command Error] ${parseResult.error}",
                    isUser = false
                )
                internalMessages.add(errorMessage)
                syncToUIMessages()
                updateState { copy(isLoading = false) }
                saveCurrentSession()
                return
            }

            val command = parseResult.command ?: return

            // Special handling for context command
            if (command is data.commands.ChatCommand.Context) {
                val enabled = command.arguments[0].lowercase() == "on"
                val newSettings = uiState.value.codeAssistantSettings.copy(
                    autoContextEnabled = enabled
                )
                updateCodeAssistantSettings(newSettings)

                val message = if (enabled) {
                    "[System] Auto-context enrichment enabled"
                } else {
                    "[System] Auto-context enrichment disabled"
                }

                val systemMessage = InternalMessage.Regular(
                    content = message,
                    isUser = false
                )
                internalMessages.add(systemMessage)
                syncToUIMessages()
                updateState { copy(isLoading = false) }
                saveCurrentSession()
                return
            }

            // Execute command
            val result = commandExecutor.execute(command, uiState.value.codeAssistantSettings)

            when (result) {
                is data.commands.CommandResult.Success -> {
                    if (result.sendToLlm) {
                        // Send output to LLM for formatting
                        sendCommandResultToLlm(result.output)
                    } else {
                        // Display directly
                        val resultMessage = InternalMessage.Regular(
                            content = result.output,
                            isUser = false
                        )
                        internalMessages.add(resultMessage)
                        syncToUIMessages()
                        updateState { copy(isLoading = false) }
                        saveCurrentSession()
                    }
                }
                is data.commands.CommandResult.Error -> {
                    val errorMessage = InternalMessage.Regular(
                        content = "[Command Error] ${result.message}",
                        isUser = false
                    )
                    internalMessages.add(errorMessage)
                    syncToUIMessages()
                    updateState { copy(isLoading = false) }
                    saveCurrentSession()
                }
            }
        } catch (e: Exception) {
            val errorMessage = InternalMessage.Regular(
                content = "[Command Error] ${e.message}",
                isUser = false
            )
            internalMessages.add(errorMessage)
            syncToUIMessages()
            updateState { copy(errorMessage = "Command failed: ${e.message}", isLoading = false) }
            saveCurrentSession()
        }
    }

    private suspend fun sendCommandResultToLlm(commandOutput: String) {
        // Apply full enrichment chain to command output
        val enrichedWithProjectDocs = enrichMessageWithProjectDocs(commandOutput)
        val enrichedWithEmbeddings = enrichMessageWithEmbeddings(enrichedWithProjectDocs)
        val enrichedWithCodeContext = enrichMessageWithCodeContext(enrichedWithEmbeddings)
        val fullyEnrichedContent = enrichMessageWithGitContext(enrichedWithCodeContext)

        // Update the last user message with the fully enriched content
        val lastUserIndex = internalMessages.indexOfLast {
            it is InternalMessage.Regular && it.isUser
        }
        if (lastUserIndex != -1) {
            val lastUser = internalMessages[lastUserIndex] as InternalMessage.Regular
            internalMessages[lastUserIndex] = lastUser.copy(content = fullyEnrichedContent)
        }

        // Use pipeline if enabled and tools available, otherwise single round
        if (uiState.value.pipelineEnabled && client.supportsTools() && availableTools.isNotEmpty()) {
            executePipeline(fullyEnrichedContent)
        } else {
            executeSingleRound()
        }
    }

    private suspend fun executeSingleRound() {
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
                uiState.value.systemPrompt,
                uiState.value.temperature,
                uiState.value.selectedModel,
                uiState.value.maxTokens,
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
                updateState { copy(errorMessage = "Error: ${error.message}") }
            }
        } catch (e: Exception) {
            updateState { copy(errorMessage = "Error: ${e.message}") }
        } finally {
            updateState { copy(isLoading = false) }
        }
    }

    private suspend fun executePipeline(content: String) {
        try {
            // Initialize pipeline if needed
            if (mcpPipeline == null) {
                mcpPipeline = McpPipeline(
                    client = client,
                    mcpServerManager = mcpServerManager,
                    maxIterations = uiState.value.pipelineMaxIterations
                )
            }

            // Build context from current conversation (excluding the just-added user message)
            val context = buildAPIMessageList().dropLast(1)

            // Execute pipeline
            val result = mcpPipeline!!.execute(
                initialPrompt = content,
                context = context,
                systemPrompt = uiState.value.systemPrompt,
                temperature = uiState.value.temperature,
                model = uiState.value.selectedModel,
                maxTokens = uiState.value.maxTokens,
                availableTools = availableTools.toList()
            )

            result.onSuccess { pipelineResult ->
                handlePipelineResult(pipelineResult)
            }.onFailure { error ->
                updateState { copy(errorMessage = "Pipeline error: ${error.message}") }
            }
        } catch (e: Exception) {
            updateState { copy(errorMessage = "Pipeline error: ${e.message}") }
        } finally {
            updateState { copy(isLoading = false) }
        }
    }

    private suspend fun handlePipelineResult(result: PipelineResult) {
        // Update the last user message with total usage info
        updateLastUserMessageWithUsage(result.totalUsage)

        // Add tool execution details to UI
        if (result.toolExecutions.isNotEmpty()) {
            val toolSummary = buildString {
                appendLine("[Pipeline executed ${result.iterations} iteration(s)]")
                appendLine("[Total tool calls: ${result.totalToolCalls}]")
                appendLine("[Tools used: ${result.uniqueToolsUsed.joinToString(", ")}]")
                appendLine()
                result.toolExecutions.forEach { execution ->
                    appendLine("Iteration ${execution.iteration} - Tool: ${execution.toolName}")
                    if (execution.isError) {
                        appendLine("  Error: ${execution.output}")
                    } else {
                        appendLine("  Result: ${execution.output.take(200)}${if (execution.output.length > 200) "..." else ""}")
                    }
                }
            }
            val pipelineMessage = InternalMessage.Regular(
                content = toolSummary,
                isUser = false
            )
            internalMessages.add(pipelineMessage)
            syncToUIMessages()
        }

        // Add final response
        val assistantMessage = InternalMessage.Regular(
            content = result.finalResponse,
            isUser = false,
            usage = result.totalUsage
        )
        internalMessages.add(assistantMessage)
        syncToUIMessages()

        // Update session stats
        val currentStats = uiState.value.sessionStats
        updateState {
            copy(
                sessionStats = SessionStats(
                    totalInputTokens = currentStats.totalInputTokens + result.totalUsage.inputTokens,
                    totalOutputTokens = currentStats.totalOutputTokens + result.totalUsage.outputTokens,
                    totalCost = currentStats.totalCost + result.totalUsage.estimatedCost,
                    lastRequestTimeMs = result.totalUsage.requestTimeMs
                ),
                previousResponseTime = uiState.value.lastResponseTime,
                lastResponseTime = result.totalUsage.requestTimeMs,
                messagesSinceLastCompaction = uiState.value.messagesSinceLastCompaction + 2
            )
        }

        // Check if auto-compaction should trigger
        if (shouldTriggerAutoCompaction()) {
            performCompaction(auto = true)
        }

        // Auto-save session after successful message
        saveCurrentSession()

        // Print pipeline summary to console
        println(result.getSummary())
    }

    private suspend fun handleToolUse(response: LlmMessage) {
        // Update the last user message with usage info
        response.usage?.let { usage ->
            updateLastUserMessageWithUsage(usage)
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
                uiState.value.systemPrompt,
                uiState.value.temperature,
                uiState.value.selectedModel,
                uiState.value.maxTokens,
                null // No tools in follow-up request
            )

            result.onSuccess { response ->
                // Clear stored tool_use blocks
                lastToolUseBlocks = null
                handleTextResponse(response)
            }.onFailure { error ->
                updateState { copy(errorMessage = "Error: ${error.message}") }
                lastToolUseBlocks = null
            }
        } catch (e: Exception) {
            updateState { copy(errorMessage = "Error: ${e.message}") }
            lastToolUseBlocks = null
        }
    }

    private suspend fun handleTextResponse(response: LlmMessage) {
        // Update the last user message with usage info
        response.usage?.let { usage ->
            updateLastUserMessageWithUsage(usage)
        }

        // Add assistant response to internal storage
        val assistantMessage = InternalMessage.Regular(
            content = response.answer,
            isUser = false,
            usage = response.usage
        )
        internalMessages.add(assistantMessage)
        syncToUIMessages()

        updateState { copy(joke = response.joke) }

        // Update session stats and last response time
        response.usage?.let { usage ->
            val currentStats = uiState.value.sessionStats
            updateState {
                copy(
                    sessionStats = SessionStats(
                        totalInputTokens = currentStats.totalInputTokens + usage.inputTokens,
                        totalOutputTokens = currentStats.totalOutputTokens + usage.outputTokens,
                        totalCost = currentStats.totalCost + usage.estimatedCost,
                        lastRequestTimeMs = usage.requestTimeMs
                    ),
                    previousResponseTime = uiState.value.lastResponseTime,
                    lastResponseTime = usage.requestTimeMs,
                    messagesSinceLastCompaction = uiState.value.messagesSinceLastCompaction + 2
                )
            }
        }

        // Check if auto-compaction should trigger
        if (shouldTriggerAutoCompaction()) {
            performCompaction(auto = true)
        }

        // Auto-save session after successful message
        saveCurrentSession()
    }

    fun updateMcpServers(newServers: List<data.mcp.McpServerConfig>) {
        scope.launch {
            val newSettings = uiState.value.appSettings.copy(mcpServers = newServers)
            updateState { copy(appSettings = newSettings) }
            appSettingsStorage.saveSettings(newSettings)
            mcpServerManager.startServers(newServers)
            updateAvailableTools()
        }
    }

    fun updatePipelineSettings(enabled: Boolean, maxIterations: Int) {
        updateState {
            copy(
                pipelineEnabled = enabled,
                pipelineMaxIterations = maxIterations
            )
        }

        // Reset pipeline instance when settings change
        if (mcpPipeline != null) {
            mcpPipeline = null
        }

        // Save to persistent storage
        scope.launch {
            val newSettings = uiState.value.appSettings.copy(
                pipelineEnabled = enabled,
                pipelineMaxIterations = maxIterations
            )
            updateState { copy(appSettings = newSettings) }
            appSettingsStorage.saveSettings(newSettings)
        }
    }

    fun updateEmbeddingSettings(enabled: Boolean, embeddingFile: String?, topK: Int, threshold: Float) {
        updateState {
            copy(
                embeddingsEnabled = enabled,
                selectedEmbeddingFile = embeddingFile,
                embeddingTopK = topK,
                embeddingThreshold = threshold
            )
        }

        // Initialize or close Ollama client based on enabled state
        if (enabled && ollamaClient == null) {
            ollamaClient = OllamaClient()
        } else if (!enabled && ollamaClient != null) {
            ollamaClient?.close()
            ollamaClient = null
        }

        // Save to persistent storage
        scope.launch {
            val newSettings = uiState.value.appSettings.copy(
                embeddingsEnabled = enabled,
                selectedEmbeddingFile = embeddingFile,
                embeddingTopK = topK,
                embeddingThreshold = threshold
            )
            updateState { copy(appSettings = newSettings) }
            appSettingsStorage.saveSettings(newSettings)
        }
    }

    fun updateCodeAssistantSettings(settings: CodeAssistantSettings) {
        updateState {
            copy(
                codeAssistantEnabled = settings.enabled,
                codeAssistantWorkingDir = settings.workingDirectory,
                codeAssistantSettings = settings
            )
        }

        // Initialize Ollama client if project docs enabled but not yet initialized
        if (settings.projectDocsEnabled && ollamaClient == null) {
            ollamaClient = OllamaClient()
            projectDocsService.updateOllamaClient(ollamaClient!!)
            println("[ProjectDocs] Initialized OllamaClient for project documentation")
        }

        // Save to persistent storage
        scope.launch(Dispatchers.IO) {
            val newAppSettings = uiState.value.appSettings.copy(
                codeAssistantSettings = settings
            )
            updateState { copy(appSettings = newAppSettings) }
            appSettingsStorage.saveSettings(newAppSettings)
            println("[Code Assistant] Settings saved: enabled=${settings.enabled}, workingDir=${settings.workingDirectory}")
        }
    }

    fun analyzeProject(): data.codeassistant.ProjectInfo? {
        val workingDir = uiState.value.codeAssistantWorkingDir ?: return null
        return projectAnalysisService.analyzeProject(java.io.File(workingDir))
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
                model = uiState.value.selectedModel,
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
        if (uiState.value.isCompacting) return

        try {
            updateState { copy(isCompacting = true) }

            val messagesToCompact = selectMessagesForCompaction(auto)
            if (messagesToCompact.isEmpty()) {
                if (!auto) updateState { copy(errorMessage = "Not enough messages to compact") }
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
                    updateState { copy(messagesSinceLastCompaction = 0) }
                    syncToUIMessages()
                }

                // Add compaction request usage to session stats
                compactionResult.usage?.let { usage ->
                    val currentStats = uiState.value.sessionStats
                    updateState {
                        copy(
                            sessionStats = SessionStats(
                                totalInputTokens = currentStats.totalInputTokens + usage.inputTokens,
                                totalOutputTokens = currentStats.totalOutputTokens + usage.outputTokens,
                                totalCost = currentStats.totalCost + usage.estimatedCost,
                                lastRequestTimeMs = currentStats.lastRequestTimeMs
                            )
                        )
                    }
                }

                // Auto-save session after compaction
                saveCurrentSession()
            }.onFailure { error ->
                updateState { copy(errorMessage = "Compaction failed: ${error.message}") }
            }
        } finally {
            updateState { copy(isCompacting = false) }
        }
    }

    fun performManualCompaction() {
        scope.launch { performCompaction(auto = false) }
    }

    fun canCompact(): Boolean {
        val regularCount = internalMessages.filterIsInstance<InternalMessage.Regular>().size
        return regularCount > 4 && !uiState.value.isCompacting && !uiState.value.isLoading
    }

    fun toggleSummaryExpansion(timestamp: Long) {
        val isCurrentlyExpanded = summaryExpansionState[timestamp] ?: false
        summaryExpansionState[timestamp] = !isCurrentlyExpanded
        syncToUIMessages()
    }

    fun clearChat() {
        val currentId = uiState.value.currentSessionId

        messages.clear()
        internalMessages.clear()
        summaryExpansionState.clear()
        updateState {
            copy(
                messagesSinceLastCompaction = 0,
                errorMessage = null,
                sessionStats = SessionStats()
            )
        }

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
        currentApiKey = apiKey
        client = createClient(vendor, apiKey)

        // Reset models
        availableModels.clear()

        // Set appropriate default model and update state
        val defaultModel = when (vendor) {
            Vendor.ANTHROPIC -> "claude-sonnet-4-20250514"
            Vendor.PERPLEXITY -> "sonar"
        }

        updateState {
            copy(
                currentVendor = vendor,
                isLoadingModels = true,
                selectedModel = defaultModel,
                lastResponseTime = null,
                previousResponseTime = null
            )
        }

        // Load new models
        loadModels()

        // Create new session with the new vendor
        createNewSession("New ${vendor.displayName} Session")
    }

    fun cleanup() {
        saveCurrentSession() // Save before cleanup
        taskReminderManager?.stopReminder() // Stop task reminder
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
                    vendor = uiState.value.currentVendor,
                    selectedModel = uiState.value.selectedModel,
                    systemPrompt = uiState.value.systemPrompt,
                    temperature = uiState.value.temperature,
                    maxTokens = uiState.value.maxTokens,
                    compactionEnabled = uiState.value.compactionEnabled
                ),
                messages = emptyList(),
                sessionStats = SessionStats()
            )

            sessionStorage.saveSession(newSession)

            updateState {
                copy(
                    currentSessionId = sessionId,
                    currentSessionName = name,
                    messagesSinceLastCompaction = 0,
                    errorMessage = null,
                    sessionStats = SessionStats()
                )
            }

            // Clear current chat
            internalMessages.clear()
            messages.clear()
            summaryExpansionState.clear()

            loadSessionsList()
        }
    }

    fun loadSession(sessionId: String) {
        scope.launch {
            val session = sessionStorage.loadSession(sessionId)
            if (session != null) {
                // Switch vendor if needed (without clearing chat)
                if (uiState.value.currentVendor != session.settings.vendor) {
                    client.close()
                    currentApiKey = when (session.settings.vendor) {
                        Vendor.ANTHROPIC -> System.getenv("CLAUDE_API_KEY") ?: ""
                        Vendor.PERPLEXITY -> System.getenv("PERPLEXITY_API_KEY") ?: ""
                    }
                    client = createClient(session.settings.vendor, currentApiKey)
                    availableModels.clear()
                    updateState { copy(isLoadingModels = true) }
                    loadModels()
                }

                // Restore settings
                updateState {
                    copy(
                        currentVendor = session.settings.vendor,
                        selectedModel = session.settings.selectedModel,
                        systemPrompt = session.settings.systemPrompt,
                        temperature = session.settings.temperature,
                        maxTokens = session.settings.maxTokens,
                        compactionEnabled = session.settings.compactionEnabled,
                        sessionStats = session.sessionStats,
                        currentSessionId = session.id,
                        currentSessionName = session.name
                    )
                }

                // Restore messages
                internalMessages.clear()
                internalMessages.addAll(session.messages.map { it.toInternal() })
                syncToUIMessages()


            }
        }
    }

    fun saveCurrentSession() {
        val sessionId = uiState.value.currentSessionId ?: return

        // Capture current values BEFORE launching coroutine to avoid race conditions
        val capturedVendor = uiState.value.currentVendor
        val capturedModel = uiState.value.selectedModel
        val capturedSystemPrompt = uiState.value.systemPrompt
        val capturedTemperature = uiState.value.temperature
        val capturedMaxTokens = uiState.value.maxTokens
        val capturedCompactionEnabled = uiState.value.compactionEnabled
        val capturedSessionName = uiState.value.currentSessionName
        val capturedMessages = internalMessages.map { it.toSerializable() }
        val capturedStats = uiState.value.sessionStats

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
                if (uiState.value.currentSessionId == sessionId) {
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

                if (uiState.value.currentSessionId == sessionId) {
                    updateState { copy(currentSessionName = newName) }
                }

                loadSessionsList()
            }
        }
    }
}
