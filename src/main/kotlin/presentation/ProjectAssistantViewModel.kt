package presentation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import data.*
import data.mcp.McpServerManager
import data.mcp.McpTool
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * ViewModel for the Project Assistant feature.
 * Provides an intelligent chat interface that's aware of the project context
 * including git, issues, tasks, documentation, and codebase.
 */
class ProjectAssistantViewModel(
    private val apiClient: ApiClient,
    private val mcpServerManager: McpServerManager
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // UI State
    val uiState = mutableStateOf(ProjectAssistantUiState())

    // Messages for display
    val messages = mutableStateListOf<ProjectAssistantMessage>()

    // Internal conversation history for API calls
    private val conversationHistory = mutableListOf<ChatMessage>()

    // Available MCP tools
    private var availableTools = listOf<Pair<String, McpTool>>()

    // Services
    private val chatService = ChatService(apiClient, mcpServerManager)
    private val contextCache = ContextCache(maxAgeMs = 5 * 60 * 1000, maxEntries = 30)
    private val contextSummarizer = ContextSummarizer(chatService)

    // JSON serializer
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    // Storage file
    private val conversationFile: File = run {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".ai-advent-chat")
        if (!appDir.exists()) appDir.mkdirs()
        File(appDir, "project-assistant-conversation.json")
    }

    init {
        loadTools()
        loadConversation()
    }

    private fun loadTools() {
        viewModelScope.launch {
            // Wait for MCP servers to be ready with stabilization check
            var previousToolCount = -1
            var stableChecks = 0
            val requiredStableChecks = 2 // Wait for 2 consecutive checks with same count
            val maxChecks = 10
            val delayBetweenChecks = 1500L
            var checks = 0

            println("[ProjectAssistant] Starting tool loading...")

            while (checks < maxChecks) {
                delay(delayBetweenChecks)
                availableTools = mcpServerManager.getAllTools()
                val currentToolCount = availableTools.size
                val readyServers = mcpServerManager.getReadyServers().size
                val allServers = mcpServerManager.getAllServers().size

                println("[ProjectAssistant] Check ${checks + 1}/$maxChecks: Found $currentToolCount tools from $readyServers/$allServers servers")

                // Check if tool count has stabilized
                if (currentToolCount > 0 && currentToolCount == previousToolCount) {
                    stableChecks++
                    if (stableChecks >= requiredStableChecks) {
                        // Tools have stabilized
                        updateState { copy(toolsLoaded = true) }
                        println("[ProjectAssistant] Tools stabilized! Loaded ${availableTools.size} tools:")
                        availableTools.forEach { (serverId, tool) ->
                            println("  - $serverId: ${tool.name}")
                        }
                        break
                    }
                } else {
                    // Tool count changed, reset stable counter
                    stableChecks = 0
                }

                previousToolCount = currentToolCount
                checks++
            }

            if (availableTools.isEmpty()) {
                println("[ProjectAssistant] WARNING: No tools loaded after $maxChecks checks!")
                updateState { copy(toolsLoaded = false, errorMessage = "Failed to load MCP tools. Check if servers are running.") }
            } else if (!uiState.value.toolsLoaded) {
                // We have tools but didn't stabilize - use what we have
                println("[ProjectAssistant] Tools didn't fully stabilize, using ${availableTools.size} available tools")
                updateState { copy(toolsLoaded = true) }
            }
        }
    }

    private fun updateState(block: ProjectAssistantUiState.() -> ProjectAssistantUiState) {
        uiState.value = block(uiState.value)
    }

    /**
     * Send a message to the project assistant.
     */
    fun sendMessage(content: String) {
        if (content.isBlank() || uiState.value.isLoading) return

        viewModelScope.launch {
            updateState { copy(isLoading = true, errorMessage = null) }

            try {
                // Check if tools are available, try to reload if not
                if (availableTools.isEmpty()) {
                    println("[ProjectAssistant] No tools available, attempting to reload...")
                    availableTools = mcpServerManager.getAllTools()
                    updateState { copy(toolsLoaded = availableTools.isNotEmpty()) }

                    if (availableTools.isEmpty()) {
                        println("[ProjectAssistant] Still no tools available after reload")
                    } else {
                        println("[ProjectAssistant] Reloaded ${availableTools.size} tools")
                    }
                }

                // Add user message
                val userMessage = ProjectAssistantMessage(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
                messages.add(userMessage)

                // Build context-aware prompt
                val enrichedPrompt = buildContextAwarePrompt(content)

                // Add to conversation history
                conversationHistory.add(ChatMessage(role = "user", content = enrichedPrompt))

                // Execute with pipeline for tool access
                val result = executePipeline(enrichedPrompt)

                result.onSuccess { pipelineResult ->
                    // Add assistant response
                    val assistantMessage = ProjectAssistantMessage(
                        id = UUID.randomUUID().toString(),
                        content = pipelineResult.finalResponse,
                        isUser = false,
                        timestamp = System.currentTimeMillis(),
                        toolsUsed = pipelineResult.uniqueToolsUsed.toList(),
                        usage = pipelineResult.totalUsage
                    )
                    messages.add(assistantMessage)

                    // Add to conversation history
                    conversationHistory.add(ChatMessage(role = "assistant", content = pipelineResult.finalResponse))

                    // Update stats
                    updateStats(pipelineResult.totalUsage)

                    // Check if we need to compact history
                    maybeCompactHistory()

                    // Save conversation
                    saveConversation()
                }

                result.onFailure { error ->
                    updateState { copy(errorMessage = error.message) }
                }

            } catch (e: Exception) {
                updateState { copy(errorMessage = e.message) }
            } finally {
                updateState { copy(isLoading = false) }
            }
        }
    }

    /**
     * Build a context-aware prompt that includes relevant project information.
     */
    private suspend fun buildContextAwarePrompt(userMessage: String): String {
        val contextParts = mutableListOf<String>()
        updateState { copy(gatheringContext = true) }

        try {
            // Detect what context is needed based on the message
            val needsGit = userMessage.contains(Regex("git|commit|branch|change|diff|push|pull|merge", RegexOption.IGNORE_CASE))
            val needsIssues = userMessage.contains(Regex("issue|bug|ticket|problem|error|fix", RegexOption.IGNORE_CASE))
            val needsTasks = userMessage.contains(Regex("task|todo|backlog|feature|improvement|work", RegexOption.IGNORE_CASE))
            val needsDocs = userMessage.contains(Regex("doc|readme|how|what|explain|architecture", RegexOption.IGNORE_CASE))
            val needsCode = userMessage.contains(Regex("code|file|function|class|implement|refactor", RegexOption.IGNORE_CASE))

            // Gather context in parallel
            val deferreds = mutableListOf<Deferred<String?>>()

            if (needsGit || isFirstMessage()) {
                deferreds.add(viewModelScope.async { gatherGitContext() })
            }

            if (needsIssues) {
                deferreds.add(viewModelScope.async { gatherIssuesContext() })
            }

            if (needsTasks) {
                deferreds.add(viewModelScope.async { gatherTasksContext() })
            }

            if (needsDocs || isFirstMessage()) {
                deferreds.add(viewModelScope.async { gatherDocsContext() })
            }

            // Wait for all context gathering
            deferreds.awaitAll().filterNotNull().forEach { context ->
                if (context.isNotBlank()) {
                    contextParts.add(context)
                }
            }

        } finally {
            updateState { copy(gatheringContext = false) }
        }

        // Combine context with user message
        return if (contextParts.isNotEmpty()) {
            """Here is relevant project context:

${contextParts.joinToString("\n\n---\n\n")}

---

User Question: $userMessage"""
        } else {
            userMessage
        }
    }

    private fun isFirstMessage(): Boolean = messages.isEmpty()

    /**
     * Gather git context using MCP tools.
     */
    private suspend fun gatherGitContext(): String? {
        // Check cache first
        val cachedGit = contextCache.get("git_status")
        if (cachedGit != null) return cachedGit

        val gitServerId = availableTools.find { it.second.name == "git_status" }?.first ?: return null

        return try {
            val statusResult = mcpServerManager.callTool(gitServerId, "git_status", null)
            val branchResult = mcpServerManager.callTool(gitServerId, "git_branch", null)

            val context = buildString {
                appendLine("## Git Status")
                statusResult.onSuccess { result ->
                    appendLine(result.content.joinToString("\n") { it.text ?: "" })
                }
                branchResult.onSuccess { result ->
                    appendLine("\n## Current Branch")
                    appendLine(result.content.joinToString("\n") { it.text ?: "" })
                }
            }

            // Cache for 2 minutes
            contextCache.put("git_status", context)
            context
        } catch (e: Exception) {
            println("[ProjectAssistant] Error gathering git context: ${e.message}")
            null
        }
    }

    /**
     * Gather issues context using MCP tools.
     */
    private suspend fun gatherIssuesContext(): String? {
        val cachedIssues = contextCache.get("issues")
        if (cachedIssues != null) return cachedIssues

        val issuesServerId = availableTools.find { it.second.name == "list_tickets" }?.first ?: return null

        return try {
            val result = mcpServerManager.callTool(issuesServerId, "list_tickets", null)
            val context = buildString {
                appendLine("## Issue Tickets")
                result.onSuccess { r ->
                    val content = r.content.joinToString("\n") { it.text ?: "" }
                    // Summarize if too long
                    if (content.length > 3000) {
                        appendLine(content.take(3000) + "...\n[Truncated for brevity]")
                    } else {
                        appendLine(content)
                    }
                }
            }

            contextCache.put("issues", context)
            context
        } catch (e: Exception) {
            println("[ProjectAssistant] Error gathering issues context: ${e.message}")
            null
        }
    }

    /**
     * Gather tasks context using MCP tools.
     */
    private suspend fun gatherTasksContext(): String? {
        val cachedTasks = contextCache.get("tasks")
        if (cachedTasks != null) return cachedTasks

        val tasksServerId = availableTools.find { it.second.name == "list_tasks" }?.first ?: return null

        return try {
            val result = mcpServerManager.callTool(tasksServerId, "list_tasks", null)
            val context = buildString {
                appendLine("## Task Board")
                result.onSuccess { r ->
                    val content = r.content.joinToString("\n") { it.text ?: "" }
                    if (content.length > 3000) {
                        appendLine(content.take(3000) + "...\n[Truncated for brevity]")
                    } else {
                        appendLine(content)
                    }
                }
            }

            contextCache.put("tasks", context)
            context
        } catch (e: Exception) {
            println("[ProjectAssistant] Error gathering tasks context: ${e.message}")
            null
        }
    }

    /**
     * Gather documentation context using MCP tools.
     */
    private suspend fun gatherDocsContext(): String? {
        val cachedDocs = contextCache.get("docs")
        if (cachedDocs != null) return cachedDocs

        // Try to find project docs tool (search_project_docs)
        val docsServerId = availableTools.find { it.second.name == "search_project_docs" }?.first ?: return null

        return try {
            // Search for general project info
            val args = kotlinx.serialization.json.buildJsonObject {
                put("query", kotlinx.serialization.json.JsonPrimitive("project overview architecture"))
                put("limit", kotlinx.serialization.json.JsonPrimitive(3))
            }
            val result = mcpServerManager.callTool(docsServerId, "search_project_docs", args)
            val context = buildString {
                appendLine("## Project Documentation")
                result.onSuccess { r ->
                    val content = r.content.joinToString("\n") { it.text ?: "" }
                    if (content.length > 2000) {
                        appendLine(content.take(2000) + "...\n[Truncated for brevity]")
                    } else {
                        appendLine(content)
                    }
                }
                result.onFailure { e ->
                    appendLine("(Documentation not available: ${e.message})")
                }
            }

            contextCache.put("docs", context)
            context
        } catch (e: Exception) {
            println("[ProjectAssistant] Error gathering docs context: ${e.message}")
            null
        }
    }

    /**
     * Execute the pipeline for multi-tool interactions.
     */
    private suspend fun executePipeline(prompt: String): Result<PipelineResult> {
        // Use ALL available tools including code context tools (read_file, list_directory)
        // We only exclude shell execute and write commands for safety
        val projectTools = availableTools.filter { (_, tool) ->
            tool.name !in listOf("execute_command", "write_file") // Exclude only dangerous tools
        }

        println("[ProjectAssistant] Available tools for pipeline (${projectTools.size}): ${projectTools.map { it.second.name }}")

        if (projectTools.isEmpty()) {
            println("[ProjectAssistant] WARNING: No tools available! Check if MCP servers are running.")
        }

        // Build conversation context (with rolling summary if needed)
        val contextMessages = buildConversationContext()

        val pipeline = chatService.createPipeline(
            maxIterations = uiState.value.maxIterations,
            iterationDelayMs = 150L
        )

        return pipeline.execute(
            initialPrompt = prompt,
            context = contextMessages,
            systemPrompt = getSystemPrompt(),
            temperature = 0.2f,
            model = uiState.value.model,
            maxTokens = 4096,
            availableTools = projectTools
        )
    }

    /**
     * Build conversation context with token optimization.
     */
    private suspend fun buildConversationContext(): List<ChatMessage> {
        val maxContextMessages = 6 // Keep last N messages in full
        val estimatedTokensPerMessage = 500

        if (conversationHistory.size <= maxContextMessages) {
            return conversationHistory.toList()
        }

        // Summarize older messages
        val summaryResult = contextSummarizer.createRollingContextSummary(
            conversationHistory,
            maxContextMessages,
            uiState.value.model
        )

        return summaryResult.map { summary ->
            if (summary.isNotBlank()) {
                listOf(
                    ChatMessage(role = "user", content = "[Previous conversation summary: $summary]")
                ) + conversationHistory.takeLast(maxContextMessages)
            } else {
                conversationHistory.takeLast(maxContextMessages)
            }
        }.getOrElse {
            conversationHistory.takeLast(maxContextMessages)
        }
    }

    private fun getSystemPrompt(): String = """You are a knowledgeable project assistant for a software development project.
You have access to tools that let you:
- Query Git repository status, commits, branches, and diffs
- View issue tickets and bug reports
- Manage task board with project tasks
- Search project documentation
- Read files and list directory contents to understand code structure

Your role is to help developers understand their project, track progress, find issues, and provide guidance.

Guidelines:
1. Use tools proactively to gather information before answering questions
2. Reference specific tickets, tasks, or commits when relevant
3. Use read_file and list_directory to explore code when needed
4. Provide actionable advice based on project state
5. Be concise but thorough in your responses
6. If you need more information, ask clarifying questions
7. When discussing code changes, reference the relevant files and line numbers

Current project context may be provided at the start of each message. Use this context to inform your responses."""

    /**
     * Update session statistics.
     */
    private fun updateStats(usage: UsageInfo) {
        updateState {
            copy(
                totalTokens = totalTokens + usage.totalTokens,
                totalInputTokens = totalInputTokens + usage.inputTokens,
                totalOutputTokens = totalOutputTokens + usage.outputTokens,
                totalCost = totalCost + usage.estimatedCost,
                messageCount = messages.size
            )
        }
    }

    /**
     * Compact history if it's getting too long.
     */
    private suspend fun maybeCompactHistory() {
        if (conversationHistory.size > 20) {
            val summary = contextSummarizer.createRollingContextSummary(
                conversationHistory,
                10,
                uiState.value.model
            )

            summary.onSuccess { summaryText ->
                if (summaryText.isNotBlank()) {
                    val oldCount = conversationHistory.size
                    val recentMessages = conversationHistory.takeLast(10)
                    conversationHistory.clear()
                    conversationHistory.add(ChatMessage(role = "assistant", content = "[Conversation summary: $summaryText]"))
                    conversationHistory.addAll(recentMessages)

                    println("[ProjectAssistant] Compacted history from $oldCount to ${conversationHistory.size} messages")
                }
            }
        }
    }

    /**
     * Clear the conversation.
     */
    fun clearConversation() {
        messages.clear()
        conversationHistory.clear()
        contextCache.invalidateAll()
        updateState {
            copy(
                totalTokens = 0,
                totalInputTokens = 0,
                totalOutputTokens = 0,
                totalCost = 0.0,
                messageCount = 0
            )
        }
        saveConversation()
    }

    /**
     * Refresh context caches and reload tools.
     */
    fun refreshContext() {
        contextCache.invalidateAll()
        viewModelScope.launch {
            updateState { copy(gatheringContext = true, errorMessage = null, toolsLoaded = false) }
            try {
                // Reload tools with stabilization check
                var previousToolCount = -1
                var stableChecks = 0
                val requiredStableChecks = 2
                val maxChecks = 6
                val delayBetweenChecks = 1000L

                println("[ProjectAssistant] Refreshing tools...")

                for (check in 1..maxChecks) {
                    availableTools = mcpServerManager.getAllTools()
                    val currentToolCount = availableTools.size
                    val readyServers = mcpServerManager.getReadyServers().size
                    val allServers = mcpServerManager.getAllServers().size

                    println("[ProjectAssistant] Refresh check $check/$maxChecks: $currentToolCount tools from $readyServers/$allServers servers")

                    if (currentToolCount > 0 && currentToolCount == previousToolCount) {
                        stableChecks++
                        if (stableChecks >= requiredStableChecks) {
                            break
                        }
                    } else {
                        stableChecks = 0
                    }

                    previousToolCount = currentToolCount
                    if (check < maxChecks) {
                        delay(delayBetweenChecks)
                    }
                }

                updateState { copy(toolsLoaded = availableTools.isNotEmpty()) }
                println("[ProjectAssistant] Refreshed tools: ${availableTools.size} available")
                availableTools.forEach { (serverId, tool) ->
                    println("  - $serverId: ${tool.name}")
                }

                // Pre-fetch common contexts
                gatherGitContext()
                gatherDocsContext()
            } catch (e: Exception) {
                println("[ProjectAssistant] Error refreshing context: ${e.message}")
                updateState { copy(errorMessage = "Error refreshing context: ${e.message}") }
            } finally {
                updateState { copy(gatheringContext = false) }
            }
        }
    }

    /**
     * Update settings.
     */
    fun updateSettings(model: String, maxIterations: Int) {
        updateState {
            copy(
                model = model,
                maxIterations = maxIterations
            )
        }
    }

    /**
     * Start the deployment pipeline.
     * This triggers an interactive flow where the agent:
     * 1. Builds the project artifacts
     * 2. Gathers commit history for release notes
     * 3. Asks user about release details
     * 4. Shows draft release notes
     * 5. Publishes the release to GitHub
     */
    fun startDeployPipeline() {
        if (uiState.value.isLoading || uiState.value.deployInProgress) return

        viewModelScope.launch {
            updateState { copy(deployInProgress = true, isLoading = true, errorMessage = null, gatheringContext = true) }

            try {
                // Add a system message indicating deploy mode
                val deployMessage = ProjectAssistantMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    content = "🚀 Starting deployment pipeline...",
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
                messages.add(deployMessage)

                // Gather project context first (like regular chat does)
                val contextParts = mutableListOf<String>()

                // Gather all relevant context in parallel
                val deferreds = listOf(
                    viewModelScope.async { gatherGitContext() },
                    viewModelScope.async { gatherTasksContext() },
                    viewModelScope.async { gatherDocsContext() }
                )

                deferreds.awaitAll().filterNotNull().forEach { context ->
                    if (context.isNotBlank()) {
                        contextParts.add(context)
                    }
                }

                updateState { copy(gatheringContext = false) }

                // Create the deploy prompt with gathered context
                val deployPrompt = createDeployPrompt(contextParts)
                conversationHistory.add(ChatMessage(role = "user", content = deployPrompt))

                // Execute with deploy-specific settings (more iterations for the multi-step process)
                val result = executeDeployPipeline(deployPrompt)

                result.onSuccess { pipelineResult ->
                    val assistantMessage = ProjectAssistantMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        content = pipelineResult.finalResponse,
                        isUser = false,
                        timestamp = System.currentTimeMillis(),
                        toolsUsed = pipelineResult.uniqueToolsUsed.toList(),
                        usage = pipelineResult.totalUsage
                    )
                    messages.add(assistantMessage)
                    conversationHistory.add(ChatMessage(role = "assistant", content = pipelineResult.finalResponse))
                    updateStats(pipelineResult.totalUsage)
                    saveConversation()
                }

                result.onFailure { error ->
                    updateState { copy(errorMessage = "Deploy failed: ${error.message}") }
                }

            } catch (e: Exception) {
                updateState { copy(errorMessage = "Deploy error: ${e.message}") }
            } finally {
                updateState { copy(deployInProgress = false, isLoading = false, gatheringContext = false) }
            }
        }
    }

    /**
     * Create the deploy prompt that instructs the agent on the deployment workflow.
     */
    private fun createDeployPrompt(contextParts: List<String>): String {
        val contextSection = if (contextParts.isNotEmpty()) {
            """
## Pre-gathered Project Context
Here is the current project context that was automatically gathered:

${contextParts.joinToString("\n\n---\n\n")}

---

"""
        } else {
            ""
        }

        return """You are now in DEPLOYMENT MODE. Please execute the following deployment workflow step by step.

$contextSection
## Step 1: Gather Additional Information (if needed)
Some context has already been gathered above. Now gather any additional information needed:
- Use `github_get_repo_info` to get repository details (owner and repo name - REQUIRED for release)
- Use `github_list_releases` to check existing releases and determine next version
- Use `github_list_commits` to get recent commit history for release notes (last 10-20 commits)
- Use `list_build_artifacts` to check if there are existing build artifacts

## Step 2: Ask About Release Details
Based on the gathered information, ask the user these questions:
1. What version number should this release have? (suggest based on previous releases if any)
2. What type of release is this? (major feature, minor update, bug fix, etc.)
3. Should this be a pre-release or full release?
4. Are there any specific highlights or breaking changes to mention?

Wait for user response before proceeding.

## Step 3: Build Platform Installers
After getting user input about version, ask the user which platforms to build for:
- **Windows**: Use `build_project` with task "packageMsi" (creates .msi installer) or "packageExe" (creates .exe installer)
- **macOS**: Use `build_project` with task "packageDmg" (creates .dmg disk image)
- **Linux**: Use `build_project` with task "packageDeb" (creates .deb package)

For a multiplatform release, you may need to build multiple times with different tasks.
IMPORTANT: Do NOT build JAR files - always build native installers (.msi, .exe, .dmg, .deb)

After each build:
- If build fails, report the error and ask if user wants to retry or abort
- Use `list_build_artifacts` to find the generated installer files
- Look for artifacts in: build/compose/binaries/main/msi/, build/compose/binaries/main/dmg/, build/compose/binaries/main/exe/, build/compose/binaries/main/deb/

## Step 4: Draft Release Notes
Create draft release notes including:
- Version number and release title
- Summary of changes based on commit history
- List of new features, improvements, and bug fixes
- Any breaking changes or migration notes
- Present the draft to the user and ask for approval or modifications

## Step 5: Publish Release
After user approves:
- Use `github_create_release` to create the release with the approved notes
- Use `github_upload_release_asset` to upload the build artifacts
- Confirm successful deployment with links to the release

IMPORTANT GUIDELINES:
- Always wait for user confirmation before building or publishing
- If any step fails, explain what went wrong and ask how to proceed
- Be conversational and helpful throughout the process
- Show progress at each step

Please start by gathering project information (Step 1)."""
    }

    /**
     * Execute the deploy pipeline with special settings.
     * NOTE: We use empty context to avoid compaction issues - all context is in the prompt itself.
     */
    private suspend fun executeDeployPipeline(prompt: String): Result<PipelineResult> {
        // For deploy, we want ALL tools including build and GitHub tools
        val deployTools = availableTools.filter { (_, tool) ->
            tool.name !in listOf("execute_command", "write_file") // Only exclude dangerous tools
        }

        println("[ProjectAssistant] Deploy pipeline using ${deployTools.size} tools")

        // IMPORTANT: Use empty context for deployment to avoid compaction removing critical info
        // All necessary context is included in the deploy prompt itself
        val contextMessages = emptyList<ChatMessage>()

        val pipeline = chatService.createPipeline(
            maxIterations = 20, // More iterations for deploy workflow (increased from 15)
            iterationDelayMs = 200L
        )

        return pipeline.execute(
            initialPrompt = prompt,
            context = contextMessages,
            systemPrompt = getDeploySystemPrompt(),
            temperature = 0.3f,
            model = uiState.value.model,
            maxTokens = 4096,
            availableTools = deployTools
        )
    }

    /**
     * System prompt specifically for deployment mode.
     */
    private fun getDeploySystemPrompt(): String = """You are a deployment assistant for a Kotlin/Compose Desktop multiplatform project.
Your role is to guide the user through building native installers and publishing releases to GitHub.

You have access to these tool categories:
- GitHub tools: Create releases, upload assets, get repo info, list commits, list releases
- Build tools: Build project with gradle, list build artifacts
- Git tools: Check git status, branches, and commits

CRITICAL - BUILD NATIVE INSTALLERS, NOT JARs:
This is a desktop application that needs native installers for distribution:
- For Windows: Use task "packageMsi" (creates .msi installer) - PREFERRED
- For Windows alternative: Use task "packageExe" (creates .exe installer)
- For macOS: Use task "packageDmg" (creates .dmg disk image)
- For Linux: Use task "packageDeb" (creates .deb package)

NEVER build or upload JAR files for release. Users expect native installers.

Artifact locations after build:
- Windows MSI: build/compose/binaries/main/msi/
- Windows EXE: build/compose/binaries/main/exe/
- macOS DMG: build/compose/binaries/main/dmg/
- Linux DEB: build/compose/binaries/main/deb/

IMPORTANT BEHAVIORS:
1. Always be conversational and explain what you're doing
2. Ask which platforms the user wants to build for (Windows/macOS/Linux)
3. Ask for confirmation before any destructive or publishing actions
4. If something fails, explain clearly and offer solutions
5. Show progress and celebrate successes
6. Format release notes in proper markdown
7. Upload ALL built installers to the release (not just one)

Current project context may be provided. Use this to inform your actions."""

    /**
     * Save conversation to disk.
     */
    private fun saveConversation() {
        try {
            val data = ProjectAssistantData(
                messages = messages.toList(),
                stats = ProjectAssistantStats(
                    totalTokens = uiState.value.totalTokens,
                    totalInputTokens = uiState.value.totalInputTokens,
                    totalOutputTokens = uiState.value.totalOutputTokens,
                    totalCost = uiState.value.totalCost
                )
            )
            conversationFile.writeText(json.encodeToString(data), Charsets.UTF_8)
        } catch (e: Exception) {
            println("[ProjectAssistant] Error saving conversation: ${e.message}")
        }
    }

    /**
     * Load conversation from disk.
     */
    private fun loadConversation() {
        try {
            if (conversationFile.exists()) {
                val data = json.decodeFromString<ProjectAssistantData>(conversationFile.readText(Charsets.UTF_8))
                messages.clear()
                messages.addAll(data.messages)

                // Rebuild conversation history from messages
                data.messages.forEach { msg ->
                    conversationHistory.add(
                        ChatMessage(
                            role = if (msg.isUser) "user" else "assistant",
                            content = msg.content
                        )
                    )
                }

                updateState {
                    copy(
                        totalTokens = data.stats.totalTokens,
                        totalInputTokens = data.stats.totalInputTokens,
                        totalOutputTokens = data.stats.totalOutputTokens,
                        totalCost = data.stats.totalCost,
                        messageCount = messages.size
                    )
                }

                println("[ProjectAssistant] Loaded ${messages.size} messages")
            }
        } catch (e: Exception) {
            println("[ProjectAssistant] Error loading conversation: ${e.message}")
        }
    }

    fun dispose() {
        viewModelScope.cancel()
    }
}

/**
 * UI State for Project Assistant.
 */
data class ProjectAssistantUiState(
    val isLoading: Boolean = false,
    val gatheringContext: Boolean = false,
    val errorMessage: String? = null,
    val toolsLoaded: Boolean = false,
    val model: String = "claude-3-haiku-20240307",
    val maxIterations: Int = 8,
    val deployInProgress: Boolean = false,
    // Stats
    val totalTokens: Int = 0,
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalCost: Double = 0.0,
    val messageCount: Int = 0
)

/**
 * Message in the Project Assistant conversation.
 */
@Serializable
data class ProjectAssistantMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val toolsUsed: List<String> = emptyList(),
    val usage: UsageInfo? = null
)

/**
 * Persisted data for Project Assistant.
 */
@Serializable
data class ProjectAssistantData(
    val messages: List<ProjectAssistantMessage>,
    val stats: ProjectAssistantStats
)

@Serializable
data class ProjectAssistantStats(
    val totalTokens: Int,
    val totalInputTokens: Int,
    val totalOutputTokens: Int,
    val totalCost: Double
)
