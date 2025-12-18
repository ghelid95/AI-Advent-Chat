package data

import data.mcp.McpServerManager
import data.mcp.McpTool
import kotlinx.coroutines.delay

/**
 * Sequential Multi-Tool Pipeline that enables complex workflows
 * where LLM responses can trigger multiple rounds of tool execution.
 */
class McpPipeline(
    private val client: ApiClient,
    private val mcpServerManager: McpServerManager,
    private val maxIterations: Int = 5
) {
    /**
     * Execute a pipeline with iterative tool calls.
     *
     * @param initialPrompt The user's initial request
     * @param context Previous chat messages for context
     * @param systemPrompt System prompt for the LLM
     * @param temperature Temperature setting
     * @param model Model to use
     * @param maxTokens Max tokens for each LLM call
     * @param availableTools List of available MCP tools with their server IDs
     * @return PipelineResult containing final response and execution metadata
     */
    suspend fun execute(
        initialPrompt: String,
        context: List<ChatMessage> = emptyList(),
        systemPrompt: String = "",
        temperature: Float = 0.1f,
        model: String = "claude-sonnet-4-20250514",
        maxTokens: Int = 1024,
        availableTools: List<Pair<String, McpTool>> = emptyList()
    ): Result<PipelineResult> {
        return try {
            val history = context.toMutableList()
            val toolExecutions = mutableListOf<ToolExecution>()
            var iterations = 0
            var totalUsage = UsageInfo(0, 0, 0, 0.0, 0.0, 0.0, 0)
            var lastResponse: LlmMessage? = null

            // Add initial user message
            history.add(ChatMessage(role = "user", content = initialPrompt))

            // Convert MCP tools to Claude format
            val claudeTools = if (client.supportsTools() && availableTools.isNotEmpty()) {
                availableTools.map { (_, tool) ->
                    ClaudeTool(
                        name = tool.name,
                        description = tool.description,
                        inputSchema = tool.inputSchema
                    )
                }
            } else null

            while (iterations < maxIterations) {
                iterations++

                // Call LLM
                val result = client.sendMessage(
                    messages = history,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    model = model,
                    maxTokens = maxTokens,
                    tools = claudeTools
                )

                result.onFailure { error ->
                    return Result.failure(error)
                }

                val response = result.getOrThrow()
                lastResponse = response

                // Accumulate usage
                response.usage?.let { usage ->
                    totalUsage = UsageInfo(
                        inputTokens = totalUsage.inputTokens + usage.inputTokens,
                        outputTokens = totalUsage.outputTokens + usage.outputTokens,
                        totalTokens = totalUsage.totalTokens + usage.totalTokens,
                        estimatedInputCost = totalUsage.estimatedInputCost + usage.estimatedInputCost,
                        estimatedOutputCost = totalUsage.estimatedOutputCost + usage.estimatedOutputCost,
                        estimatedCost = totalUsage.estimatedCost + usage.estimatedCost,
                        requestTimeMs = totalUsage.requestTimeMs + usage.requestTimeMs
                    )
                }

                // Check if tool use was requested
                if (response.stopReason == "tool_use" && !response.toolUses.isNullOrEmpty()) {
                    // Add assistant message with tool_use blocks to history
                    history.add(ChatMessage(
                        role = "assistant",
                        content = ChatMessageContent.ContentBlocks(response.toolUses)
                    ))

                    // Execute all requested tools
                    val toolResults = mutableListOf<ContentBlock.ToolResult>()

                    for (toolUse in response.toolUses) {
                        val startTime = System.currentTimeMillis()

                        // Find which server has this tool
                        val serverToolPair = availableTools.find { it.second.name == toolUse.name }

                        if (serverToolPair != null) {
                            val (serverId, _) = serverToolPair

                            // Execute tool
                            val toolResult = mcpServerManager.callTool(serverId, toolUse.name, toolUse.input)
                            val executionTime = System.currentTimeMillis() - startTime

                            toolResult.onSuccess { mcpResult ->
                                val content = mcpResult.content.joinToString("\n") { it.text ?: "" }

                                toolResults.add(
                                    ContentBlock.ToolResult(
                                        toolUseId = toolUse.id,
                                        content = content,
                                        isError = mcpResult.isError
                                    )
                                )

                                toolExecutions.add(
                                    ToolExecution(
                                        toolName = toolUse.name,
                                        input = toolUse.input.toString(),
                                        output = content,
                                        isError = mcpResult.isError,
                                        executionTimeMs = executionTime,
                                        iteration = iterations
                                    )
                                )
                            }.onFailure { error ->
                                val errorMsg = "Error: ${error.message}"
                                toolResults.add(
                                    ContentBlock.ToolResult(
                                        toolUseId = toolUse.id,
                                        content = errorMsg,
                                        isError = true
                                    )
                                )

                                toolExecutions.add(
                                    ToolExecution(
                                        toolName = toolUse.name,
                                        input = toolUse.input.toString(),
                                        output = errorMsg,
                                        isError = true,
                                        executionTimeMs = executionTime,
                                        iteration = iterations
                                    )
                                )
                            }
                        } else {
                            // Tool not found
                            val errorMsg = "Error: Tool '${toolUse.name}' not found"
                            toolResults.add(
                                ContentBlock.ToolResult(
                                    toolUseId = toolUse.id,
                                    content = errorMsg,
                                    isError = true
                                )
                            )

                            toolExecutions.add(
                                ToolExecution(
                                    toolName = toolUse.name,
                                    input = toolUse.input.toString(),
                                    output = errorMsg,
                                    isError = true,
                                    executionTimeMs = System.currentTimeMillis() - startTime,
                                    iteration = iterations
                                )
                            )
                        }
                    }

                    // Add tool results to history for next iteration
                    history.add(ChatMessage(
                        role = "user",
                        content = ChatMessageContent.ContentBlocks(toolResults)
                    ))

                    // Small delay before next iteration to avoid rate limiting
                    delay(100)
                } else {
                    // No more tool use, pipeline complete
                    break
                }
            }

            val finalResponse = lastResponse?.answer ?: ""
            val hitMaxIterations = iterations >= maxIterations &&
                                   lastResponse?.stopReason == "tool_use"

            Result.success(
                PipelineResult(
                    finalResponse = finalResponse,
                    iterations = iterations,
                    toolExecutions = toolExecutions,
                    totalUsage = totalUsage,
                    conversationHistory = history,
                    hitMaxIterations = hitMaxIterations
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Result of a pipeline execution containing all metadata and execution details.
 */
data class PipelineResult(
    val finalResponse: String,
    val iterations: Int,
    val toolExecutions: List<ToolExecution>,
    val totalUsage: UsageInfo,
    val conversationHistory: List<ChatMessage>,
    val hitMaxIterations: Boolean
) {
    val totalToolCalls: Int get() = toolExecutions.size
    val uniqueToolsUsed: Set<String> get() = toolExecutions.map { it.toolName }.toSet()
    val hasErrors: Boolean get() = toolExecutions.any { it.isError }
    val totalToolExecutionTime: Long get() = toolExecutions.sumOf { it.executionTimeMs }

    fun getSummary(): String = buildString {
        appendLine("Pipeline Execution Summary:")
        appendLine("- Iterations: $iterations")
        appendLine("- Total tool calls: $totalToolCalls")
        appendLine("- Unique tools used: ${uniqueToolsUsed.joinToString(", ")}")
        appendLine("- Total tokens: ${totalUsage.totalTokens} (input: ${totalUsage.inputTokens}, output: ${totalUsage.outputTokens})")
        appendLine("- Total cost: $${String.format("%.4f", totalUsage.estimatedCost)}")
        appendLine("- LLM time: ${totalUsage.requestTimeMs}ms")
        appendLine("- Tool execution time: ${totalToolExecutionTime}ms")
        if (hitMaxIterations) {
            appendLine("- ⚠️ Hit max iterations limit")
        }
        if (hasErrors) {
            appendLine("- ⚠️ Some tool executions had errors")
        }
    }
}

/**
 * Details of a single tool execution within the pipeline.
 */
data class ToolExecution(
    val toolName: String,
    val input: String,
    val output: String,
    val isError: Boolean,
    val executionTimeMs: Long,
    val iteration: Int
)