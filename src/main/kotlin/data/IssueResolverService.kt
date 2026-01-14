package data

import data.mcp.McpServerManager
import data.mcp.McpTool

/**
 * Service to resolve issue tickets using AI pipeline.
 * Uses available MCP tools (git, shell, project docs, issue tickets) to analyze
 * the codebase and generate resolution plans.
 */
class IssueResolverService(
    private val client: ApiClient,
    private val mcpServerManager: McpServerManager,
    private val ticketStorage: IssueTicketStorage = IssueTicketStorage()
) {
    companion object {
        // Issue resolution requires more iterations than regular chat
        const val RESOLUTION_MAX_ITERATIONS = 12

        // Delay between pipeline iterations to avoid rate limits (in ms)
        // 30k tokens/min = 500 tokens/sec, so 4 sec delay helps stay under limit
        const val ITERATION_DELAY_MS = 4000L

        // Max tokens per request
        const val RESOLUTION_MAX_TOKENS = 3000

        // Tools to include for resolution (excludes shell commands which can be verbose)
        val RESOLUTION_TOOLS = setOf(
            // Issue tickets tools
            "list_tickets", "get_ticket", "get_ticket_stats",
            // Git tools
            "git_status", "git_diff", "git_log", "git_show", "git_branch",
            // Project docs tools
            "search_project_docs", "get_project_info"
        )
    }

    /**
     * Resolve a ticket using AI pipeline.
     *
     * @param ticket The ticket to resolve
     * @param availableTools List of available MCP tools with their server IDs
     * @param systemPrompt Optional system prompt override
     * @param model Model to use for resolution
     * @param maxIterations Maximum pipeline iterations (defaults to RESOLUTION_MAX_ITERATIONS)
     * @return Result containing the AI resolution
     */
    suspend fun resolveTicket(
        ticket: IssueTicket,
        availableTools: List<Pair<String, McpTool>>,
        systemPrompt: String = "",
        model: String = "claude-sonnet-4-20250514",
        maxIterations: Int = RESOLUTION_MAX_ITERATIONS
    ): Result<AIResolution> {
        return try {
            // Use higher iteration count for resolution tasks
            val effectiveMaxIterations = maxOf(maxIterations, RESOLUTION_MAX_ITERATIONS)

            val pipeline = McpPipeline(
                client = client,
                mcpServerManager = mcpServerManager,
                maxIterations = effectiveMaxIterations,
                iterationDelayMs = ITERATION_DELAY_MS  // Add delay to avoid rate limits
            )

            val prompt = buildResolutionPrompt(ticket)

            // Filter tools to include relevant ones for resolution
            // Excludes shell commands which can produce verbose output
            val relevantTools = availableTools.filter { (_, tool) ->
                tool.name in RESOLUTION_TOOLS
            }

            val result = pipeline.execute(
                initialPrompt = prompt,
                context = emptyList(),
                systemPrompt = buildSystemPrompt(systemPrompt),
                temperature = 0.2f,
                model = model,
                maxTokens = RESOLUTION_MAX_TOKENS,
                availableTools = relevantTools.ifEmpty { availableTools }  // Fallback to all if none match
            )

            result.map { pipelineResult ->
                // Extract related ticket IDs from tool executions
                val relatedTicketIds = extractRelatedTicketIds(pipelineResult)

                // Get unique tools used
                val toolsUsed = pipelineResult.uniqueToolsUsed.toList()

                AIResolution(
                    solution = pipelineResult.finalResponse,
                    relatedTicketIds = relatedTicketIds,
                    toolsUsed = toolsUsed,
                    generatedAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve a ticket and save the resolution to storage.
     */
    suspend fun resolveAndSave(
        ticket: IssueTicket,
        availableTools: List<Pair<String, McpTool>>,
        systemPrompt: String = "",
        model: String = "claude-sonnet-4-20250514",
        maxIterations: Int = RESOLUTION_MAX_ITERATIONS
    ): Result<AIResolution> {
        val result = resolveTicket(ticket, availableTools, systemPrompt, model, maxIterations)

        result.onSuccess { resolution ->
            ticketStorage.updateTicketResolution(ticket.id, resolution)
        }

        return result
    }

    private fun buildSystemPrompt(basePrompt: String): String {
        return """
You are an expert software engineer resolving issue tickets for a Kotlin/Compose Desktop application.

Available tools:
- list_tickets, get_ticket: Find similar issues
- git_status, git_diff, git_log: Check code changes and history
- search_project_docs, get_project_info: Search project documentation

Steps:
1. Use list_tickets to find similar issues (filter by type)
2. Use git tools to check recent changes related to the issue
3. Use project docs tools to understand architecture
4. Provide a detailed solution

IMPORTANT: After using tools, provide a complete solution with:
- Problem Analysis (root cause based on code/git analysis)
- Related Issues found
- Solution Plan with specific file paths and code changes
- Testing approach
${if (basePrompt.isNotBlank()) "\nContext: $basePrompt" else ""}
        """.trimIndent()
    }

    private fun buildResolutionPrompt(ticket: IssueTicket): String {
        return """
Resolve this ticket:

ID: ${ticket.id}
Title: ${ticket.title}
Type: ${ticket.type}
Priority: ${ticket.priority}
Description: ${ticket.description}

Steps to follow:
1. Use list_tickets with type=${ticket.type} to find similar issues
2. Use git_log or git_diff to check recent relevant changes
3. Use search_project_docs to find relevant architecture info
4. Provide complete solution with specific code changes
        """.trimIndent()
    }

    /**
     * Extract related ticket IDs from pipeline tool execution results.
     */
    private fun extractRelatedTicketIds(result: PipelineResult): List<String> {
        val ticketIdPattern = Regex("TICKET-\\d+")
        val relatedIds = mutableSetOf<String>()

        // Look through tool execution outputs for ticket IDs
        result.toolExecutions
            .filter { it.toolName == "list_tickets" || it.toolName == "get_ticket" }
            .forEach { execution ->
                ticketIdPattern.findAll(execution.output).forEach { match ->
                    relatedIds.add(match.value)
                }
            }

        return relatedIds.toList()
    }
}
