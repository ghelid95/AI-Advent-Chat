package data

import data.mcp.McpServerManager
import data.mcp.McpTool
import kotlinx.coroutines.*

class TaskReminderManager(
    private val client: ClaudeClient,
    private val mcpServerManager: McpServerManager,
    private val scope: CoroutineScope
) {
    private var reminderJob: Job? = null
    private var lastTaskSummary: String? = null

    // Callback to show popup with task summary
    var onTaskSummaryReceived: ((String) -> Unit)? = null

    fun startReminder(availableTools: List<Pair<String, McpTool>>) {
        stopReminder()

        reminderJob = scope.launch {
            while (isActive) {
                try {
                    println("=== Task Reminder: Checking for tasks ===")
                    checkTasks(availableTools)
                } catch (e: Exception) {
                    println("Error in task reminder: ${e.message}")
                    e.printStackTrace()
                }

                // Wait 30 seconds before next check
                delay(30_000)
            }
        }
    }

    fun stopReminder() {
        reminderJob?.cancel()
        reminderJob = null
    }

    private suspend fun checkTasks(availableTools: List<Pair<String, McpTool>>) {
        // Convert MCP tools to Claude tools format
        val claudeTools = availableTools.map { (_, mcpTool) ->
            ClaudeTool(
                name = mcpTool.name,
                description = mcpTool.description,
                inputSchema = mcpTool.inputSchema
            )
        }

        // Step 1: Ask Claude about tasks with tools available
        val initialMessage = ChatMessage(
            role = "user",
            content = ChatMessageContent.Text("What tasks do I have for today?")
        )

        val result = client.sendMessage(
            messages = listOf(initialMessage),
            systemPrompt = "",
            temperature = 0.1f,
            model = "claude-sonnet-4-20250514",
            maxTokens = 1024,
            tools = claudeTools
        )

        result.onSuccess { response ->
            // Step 2: Check if Claude requested to use any tools
            if (response.stopReason == "tool_use" && !response.toolUses.isNullOrEmpty()) {
                println("=== Claude requested ${response.toolUses.size} tool(s) ===")

                // Execute all requested tools
                val toolResults = mutableListOf<ContentBlock.ToolResult>()
                response.toolUses.forEach { toolUse ->
                    println("[Task Reminder] Executing tool: ${toolUse.name}")

                    // Find which server has this tool
                    val serverToolPair = availableTools.find { it.second.name == toolUse.name }
                    if (serverToolPair != null) {
                        val (serverId, _) = serverToolPair

                        // Execute tool via MCP
                        val toolResult = mcpServerManager.callTool(serverId, toolUse.name, toolUse.input)
                        toolResult.onSuccess { mcpResult ->
                            val content = mcpResult.content.joinToString("\n") { it.text ?: "" }
                            toolResults.add(
                                ContentBlock.ToolResult(
                                    toolUseId = toolUse.id,
                                    content = content,
                                    isError = mcpResult.isError
                                )
                            )
                            println("[Task Reminder] Tool result: ${content.take(100)}...")
                        }.onFailure { error ->
                            toolResults.add(
                                ContentBlock.ToolResult(
                                    toolUseId = toolUse.id,
                                    content = "Error: ${error.message}",
                                    isError = true
                                )
                            )
                            println("[Task Reminder] Tool error: ${error.message}")
                        }
                    } else {
                        toolResults.add(
                            ContentBlock.ToolResult(
                                toolUseId = toolUse.id,
                                content = "Error: Tool '${toolUse.name}' not found",
                                isError = true
                            )
                        )
                    }
                }

                // Step 3: If we got tool results, send back to Claude for summarization
                if (toolResults.isNotEmpty()) {
                    val summary = summarizeTasks(initialMessage, response.toolUses, toolResults)
                    summary?.let {
                        lastTaskSummary = it
                        // Trigger the callback on the main thread
                        withContext(Dispatchers.Main) {
                            onTaskSummaryReceived?.invoke(it)
                        }
                    }
                }
            } else {
                // No tools requested, just display the direct response
                if (response.answer.isNotBlank()) {
                    lastTaskSummary = response.answer
                    withContext(Dispatchers.Main) {
                        onTaskSummaryReceived?.invoke(response.answer)
                    }
                }
            }
        }

        result.onFailure { error ->
            println("Task reminder API call failed: ${error.message}")
        }
    }

    private suspend fun summarizeTasks(
        initialMessage: ChatMessage,
        toolUses: List<ContentBlock.ToolUse>,
        toolResults: List<ContentBlock.ToolResult>
    ): String? {
        // Build the conversation with tool use and results
        val assistantMessageWithToolUse = ChatMessage(
            role = "assistant",
            content = ChatMessageContent.ContentBlocks(toolUses)
        )

        val toolResultMessage = ChatMessage(
            role = "user",
            content = ChatMessageContent.ContentBlocks(toolResults)
        )

        // Send back to Claude with a system prompt requesting summarization
        val summaryResult = client.sendMessage(
            messages = listOf(initialMessage, assistantMessageWithToolUse, toolResultMessage),
            systemPrompt = "You are a helpful assistant. Based on the tool results showing the user's tasks, " +
                    "provide a brief summary in 1-2 sentences. Keep it concise and actionable. " +
                    "If there are no tasks or the tool returned empty results, say 'No tasks scheduled for today.'",
            temperature = 0.1f,
            model = "claude-sonnet-4-20250514",
            maxTokens = 200,
            tools = null  // No tools needed for summarization
        )

        return summaryResult.getOrNull()?.answer
    }

    fun getLastTaskSummary(): String? = lastTaskSummary
}