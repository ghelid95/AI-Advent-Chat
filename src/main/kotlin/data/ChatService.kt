package data

import data.mcp.McpServerManager
import data.mcp.McpTool
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared chat service providing common functionality for chat-based features.
 * Used by both ChatViewModel and ProjectAssistantViewModel.
 */
class ChatService(
    private val apiClient: ApiClient,
    private val mcpServerManager: McpServerManager
) {
    private val mutex = Mutex()

    /**
     * Send a single message to the LLM with optional tools.
     */
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float = 0.1f,
        model: String = "claude-sonnet-4-20250514",
        maxTokens: Int = 4096,
        tools: List<ClaudeTool>? = null
    ): Result<LlmMessage> = mutex.withLock {
        apiClient.sendMessage(
            messages = messages,
            systemPrompt = systemPrompt,
            temperature = temperature,
            model = model,
            maxTokens = maxTokens,
            tools = tools
        )
    }

    /**
     * Execute a tool via MCP server.
     */
    suspend fun executeTool(
        serverId: String,
        toolName: String,
        arguments: kotlinx.serialization.json.JsonObject?
    ): Result<data.mcp.McpToolCallResult> {
        return mcpServerManager.callTool(serverId, toolName, arguments)
    }

    /**
     * Get all available tools from MCP servers.
     */
    fun getAvailableTools(): List<Pair<String, McpTool>> {
        return mcpServerManager.getAllTools()
    }

    /**
     * Convert MCP tools to Claude format.
     */
    fun convertToolsToClaudeFormat(tools: List<Pair<String, McpTool>>): List<ClaudeTool> {
        return tools.map { (_, tool) ->
            ClaudeTool(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema
            )
        }
    }

    /**
     * Create a pipeline instance for multi-tool execution.
     */
    fun createPipeline(maxIterations: Int = 5, iterationDelayMs: Long = 100L): McpPipeline {
        return McpPipeline(
            client = apiClient,
            mcpServerManager = mcpServerManager,
            maxIterations = maxIterations,
            iterationDelayMs = iterationDelayMs
        )
    }

    /**
     * Aggregate usage info from multiple sources.
     */
    fun aggregateUsage(usages: List<UsageInfo>): UsageInfo {
        return UsageInfo(
            inputTokens = usages.sumOf { it.inputTokens },
            outputTokens = usages.sumOf { it.outputTokens },
            totalTokens = usages.sumOf { it.totalTokens },
            estimatedInputCost = usages.sumOf { it.estimatedInputCost },
            estimatedOutputCost = usages.sumOf { it.estimatedOutputCost },
            estimatedCost = usages.sumOf { it.estimatedCost },
            requestTimeMs = usages.sumOf { it.requestTimeMs }
        )
    }
}

/**
 * Context cache for storing and managing project context.
 * Helps reduce token usage by caching expensive context lookups.
 */
class ContextCache(
    private val maxAgeMs: Long = 5 * 60 * 1000, // 5 minutes default TTL
    private val maxEntries: Int = 50
) {
    private val cache = mutableMapOf<String, CacheEntry>()

    data class CacheEntry(
        val content: String,
        val timestamp: Long,
        val tokenEstimate: Int
    )

    fun get(key: String): String? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > maxAgeMs) {
            cache.remove(key)
            return null
        }
        return entry.content
    }

    fun put(key: String, content: String, tokenEstimate: Int = estimateTokens(content)) {
        // Evict oldest entries if at capacity
        if (cache.size >= maxEntries) {
            val oldest = cache.entries.minByOrNull { it.value.timestamp }
            oldest?.let { cache.remove(it.key) }
        }

        cache[key] = CacheEntry(
            content = content,
            timestamp = System.currentTimeMillis(),
            tokenEstimate = tokenEstimate
        )
    }

    fun invalidate(key: String) {
        cache.remove(key)
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun getTotalTokenEstimate(): Int {
        return cache.values.sumOf { it.tokenEstimate }
    }

    /**
     * Estimate tokens for a string (rough approximation: ~4 chars per token)
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }
}

/**
 * Context summarizer for reducing token usage while preserving important information.
 */
class ContextSummarizer(private val chatService: ChatService) {

    companion object {
        const val MAX_CONTEXT_TOKENS = 8000 // Target max tokens for context
        const val SUMMARY_TARGET_RATIO = 0.3 // Summarize to 30% of original
    }

    /**
     * Summarize long content to reduce token usage.
     */
    suspend fun summarizeIfNeeded(
        content: String,
        maxTokens: Int = MAX_CONTEXT_TOKENS,
        model: String = "claude-sonnet-4-20250514"
    ): Result<String> {
        val estimatedTokens = content.length / 4

        if (estimatedTokens <= maxTokens) {
            return Result.success(content)
        }

        val targetTokens = (maxTokens * SUMMARY_TARGET_RATIO).toInt()

        return chatService.sendMessage(
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = """Summarize the following content concisely while preserving all important technical details, file paths, function names, and key information. Target length: approximately $targetTokens tokens.

Content to summarize:
$content"""
                )
            ),
            systemPrompt = "You are a technical summarizer. Provide concise summaries that preserve key technical details like file paths, function names, error messages, and important context. Be brief but accurate.",
            temperature = 0.1f,
            model = model,
            maxTokens = targetTokens
        ).map { it.answer }
    }

    /**
     * Summarize tool execution results.
     */
    suspend fun summarizeToolResults(
        toolExecutions: List<ToolExecution>,
        model: String = "claude-sonnet-4-20250514"
    ): Result<String> {
        if (toolExecutions.isEmpty()) {
            return Result.success("")
        }

        val combinedResults = toolExecutions.joinToString("\n\n") { execution ->
            """Tool: ${execution.toolName}
Input: ${execution.input.take(200)}...
Output: ${execution.output.take(1000)}...
${if (execution.isError) "ERROR" else "SUCCESS"}"""
        }

        return summarizeIfNeeded(combinedResults, MAX_CONTEXT_TOKENS / 2, model)
    }

    /**
     * Create a rolling context summary from conversation history.
     */
    suspend fun createRollingContextSummary(
        messages: List<ChatMessage>,
        maxMessages: Int = 10,
        model: String = "claude-sonnet-4-20250514"
    ): Result<String> {
        if (messages.size <= maxMessages) {
            return Result.success("")
        }

        val oldMessages = messages.dropLast(maxMessages)
        val conversationText = oldMessages.joinToString("\n\n") { msg ->
            val content = when (msg.content) {
                is ChatMessageContent.Text -> msg.content.text
                is ChatMessageContent.ContentBlocks -> msg.content.blocks.filterIsInstance<ContentBlock.Text>()
                    .joinToString("\n") { it.text }
            }
            "${msg.role.uppercase()}: ${content.take(500)}"
        }

        return summarizeIfNeeded(
            "Previous conversation summary:\n$conversationText",
            MAX_CONTEXT_TOKENS / 3,
            model
        )
    }
}
