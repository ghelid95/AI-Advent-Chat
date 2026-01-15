package mcp

import data.mcp.JsonRpcRequest
import data.mcp.JsonRpcResponse
import data.mcp.JsonRpcError
import data.mcp.McpServerInfo
import data.mcp.McpServerCapabilities
import data.mcp.McpInitializeResult
import data.mcp.McpTool
import data.mcp.McpToolListResult
import data.mcp.McpToolContent
import data.mcp.McpToolCallResult
import data.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * MCP Server for Task Board Management
 * Provides tools to add, get, edit, list, and manage project tasks
 */
class TaskBoardMcpServer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serverInfo = McpServerInfo(
        name = "task-board-server",
        version = "1.0.0"
    )

    private val capabilities = McpServerCapabilities()
    private val taskStorage = TaskBoardStorage()

    private fun log(message: String) {
        System.err.println("[TaskBoardMcpServer] $message")
        System.err.flush()
    }

    private fun sendResponse(response: JsonRpcResponse) {
        val jsonStr = json.encodeToString(response)
        println(jsonStr)
        System.out.flush()
        log("Sent response: $jsonStr")
    }

    private fun sendError(requestId: Int?, code: Int, message: String) {
        val response = JsonRpcResponse(
            id = requestId,
            error = JsonRpcError(code, message)
        )
        sendResponse(response)
    }

    private fun handleInitialize(requestId: Int?, params: JsonObject?): JsonRpcResponse {
        log("Handling initialize with params: $params")
        val result = McpInitializeResult(
            protocolVersion = "2024-11-05",
            serverInfo = serverInfo,
            capabilities = capabilities
        )
        return JsonRpcResponse(
            id = requestId,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun handleToolsList(requestId: Int?): JsonRpcResponse {
        log("Handling tools/list")

        val tools = listOf(
            McpTool(
                name = "list_tasks",
                description = "List all tasks on the task board, optionally filtered by status, category, or priority",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("status", buildJsonObject {
                            put("type", "string")
                            put("description", "Filter by status: BACKLOG, TODO, IN_PROGRESS, REVIEW, DONE")
                        })
                        put("category", buildJsonObject {
                            put("type", "string")
                            put("description", "Filter by category: FEATURE, IMPROVEMENT, BUG_FIX, REFACTORING, DOCUMENTATION, TESTING, INFRASTRUCTURE")
                        })
                        put("priority", buildJsonObject {
                            put("type", "string")
                            put("description", "Filter by priority: LOW, MEDIUM, HIGH, CRITICAL")
                        })
                    })
                }
            ),
            McpTool(
                name = "get_task1",
                description = "Get details of a specific task by ID",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("taskId", buildJsonObject {
                            put("type", "string")
                            put("description", "The task ID (e.g., TASK-001)")
                        })
                    })
                    put("required", buildJsonArray { add("taskId") })
                }
            ),
            McpTool(
                name = "add_task1",
                description = "Add a new task to the task board",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("title", buildJsonObject {
                            put("type", "string")
                            put("description", "The title of the task")
                        })
                        put("description", buildJsonObject {
                            put("type", "string")
                            put("description", "Detailed description of the task")
                        })
                        put("category", buildJsonObject {
                            put("type", "string")
                            put("description", "Category: FEATURE, IMPROVEMENT, BUG_FIX, REFACTORING, DOCUMENTATION, TESTING, INFRASTRUCTURE")
                        })
                        put("priority", buildJsonObject {
                            put("type", "string")
                            put("description", "Priority: LOW, MEDIUM, HIGH, CRITICAL")
                        })
                        put("status", buildJsonObject {
                            put("type", "string")
                            put("description", "Initial status: BACKLOG, TODO, IN_PROGRESS, REVIEW, DONE (defaults to BACKLOG)")
                        })
                        put("tags", buildJsonObject {
                            put("type", "string")
                            put("description", "Comma-separated list of tags")
                        })
                        put("estimatedHours", buildJsonObject {
                            put("type", "integer")
                            put("description", "Estimated hours to complete the task")
                        })
                    })
                    put("required", buildJsonArray {
                        add("title")
                        add("description")
                        add("category")
                        add("priority")
                    })
                }
            ),
            McpTool(
                name = "update_task1",
                description = "Update an existing task on the task board",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("taskId", buildJsonObject {
                            put("type", "string")
                            put("description", "The task ID to update (e.g., TASK-001)")
                        })
                        put("title", buildJsonObject {
                            put("type", "string")
                            put("description", "New title (optional)")
                        })
                        put("description", buildJsonObject {
                            put("type", "string")
                            put("description", "New description (optional)")
                        })
                        put("category", buildJsonObject {
                            put("type", "string")
                            put("description", "New category (optional)")
                        })
                        put("priority", buildJsonObject {
                            put("type", "string")
                            put("description", "New priority (optional)")
                        })
                        put("status", buildJsonObject {
                            put("type", "string")
                            put("description", "New status (optional)")
                        })
                        put("tags", buildJsonObject {
                            put("type", "string")
                            put("description", "New comma-separated tags (optional)")
                        })
                        put("estimatedHours", buildJsonObject {
                            put("type", "integer")
                            put("description", "New estimated hours (optional)")
                        })
                    })
                    put("required", buildJsonArray { add("taskId") })
                }
            ),
            McpTool(
                name = "delete_task1",
                description = "Delete a task from the task board",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("taskId", buildJsonObject {
                            put("type", "string")
                            put("description", "The task ID to delete (e.g., TASK-001)")
                        })
                    })
                    put("required", buildJsonArray { add("taskId") })
                }
            ),
            McpTool(
                name = "move_task1",
                description = "Move a task to a different status column",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("taskId", buildJsonObject {
                            put("type", "string")
                            put("description", "The task ID to move (e.g., TASK-001)")
                        })
                        put("status", buildJsonObject {
                            put("type", "string")
                            put("description", "Target status: BACKLOG, TODO, IN_PROGRESS, REVIEW, DONE")
                        })
                    })
                    put("required", buildJsonArray {
                        add("taskId")
                        add("status")
                    })
                }
            ),
            McpTool(
                name = "get_task_stats1",
                description = "Get statistics about all tasks (counts by status, category, priority)",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                }
            )
        )

        val result = McpToolListResult(tools)
        return JsonRpcResponse(
            id = requestId,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun formatTask(task: BoardTask): String {
        return buildString {
            appendLine("ID: ${task.id}")
            appendLine("Title: ${task.title}")
            appendLine("Category: ${task.category}")
            appendLine("Priority: ${task.priority}")
            appendLine("Status: ${task.status}")
            appendLine("Description: ${task.description}")
            if (task.tags.isNotEmpty()) {
                appendLine("Tags: ${task.tags.joinToString(", ")}")
            }
            task.estimatedHours?.let { appendLine("Estimated Hours: $it") }
            appendLine("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(task.createdAt))}")
            appendLine("Updated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(task.updatedAt))}")
            task.completedAt?.let {
                appendLine("Completed: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(it))}")
            }
        }
    }

    private fun listTasksTool(arguments: JsonObject): McpToolCallResult {
        val statusFilter = arguments["status"]?.jsonPrimitive?.contentOrNull?.let {
            try { TaskStatus.valueOf(it.uppercase().replace(" ", "_")) } catch (e: Exception) { null }
        }
        val categoryFilter = arguments["category"]?.jsonPrimitive?.contentOrNull?.let {
            try { TaskCategory.valueOf(it.uppercase().replace(" ", "_")) } catch (e: Exception) { null }
        }
        val priorityFilter = arguments["priority"]?.jsonPrimitive?.contentOrNull?.let {
            try { TaskPriority.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }

        var tasks = taskStorage.loadTasks()

        if (statusFilter != null) {
            tasks = tasks.filter { it.status == statusFilter }
        }
        if (categoryFilter != null) {
            tasks = tasks.filter { it.category == categoryFilter }
        }
        if (priorityFilter != null) {
            tasks = tasks.filter { it.priority == priorityFilter }
        }

        val output = if (tasks.isEmpty()) {
            "No tasks found matching the criteria."
        } else {
            buildString {
                appendLine("Found ${tasks.size} task(s):")
                appendLine()
                tasks.sortedByDescending { it.priority.ordinal }.forEach { task ->
                    appendLine("---")
                    append(formatTask(task))
                }
            }
        }

        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = output)),
            isError = false
        )
    }

    private fun getTaskTool(arguments: JsonObject): McpToolCallResult {
        val taskId = arguments["taskId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: taskId is required")),
                isError = true
            )

        val task = taskStorage.getTaskById(taskId)
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Task not found: $taskId")),
                isError = true
            )

        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = formatTask(task))),
            isError = false
        )
    }

    private fun addTaskTool(arguments: JsonObject): McpToolCallResult {
        val title = arguments["title"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: title is required")),
                isError = true
            )

        val description = arguments["description"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: description is required")),
                isError = true
            )

        val categoryStr = arguments["category"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: category is required")),
                isError = true
            )

        val category = try {
            TaskCategory.valueOf(categoryStr.uppercase().replace(" ", "_"))
        } catch (e: Exception) {
            return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: Invalid category. Valid values: ${TaskCategory.entries.joinToString()}")),
                isError = true
            )
        }

        val priorityStr = arguments["priority"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: priority is required")),
                isError = true
            )

        val priority = try {
            TaskPriority.valueOf(priorityStr.uppercase())
        } catch (e: Exception) {
            return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: Invalid priority. Valid values: ${TaskPriority.entries.joinToString()}")),
                isError = true
            )
        }

        val status = arguments["status"]?.jsonPrimitive?.contentOrNull?.let {
            try { TaskStatus.valueOf(it.uppercase().replace(" ", "_")) } catch (e: Exception) { TaskStatus.BACKLOG }
        } ?: TaskStatus.BACKLOG

        val tags = arguments["tags"]?.jsonPrimitive?.contentOrNull
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val estimatedHours = arguments["estimatedHours"]?.jsonPrimitive?.intOrNull

        val task = taskStorage.createTask(
            title = title,
            description = description,
            category = category,
            priority = priority,
            status = status,
            tags = tags,
            estimatedHours = estimatedHours
        )

        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = "Task created successfully:\n\n${formatTask(task)}")),
            isError = false
        )
    }

    private fun updateTaskTool(arguments: JsonObject): McpToolCallResult {
        val taskId = arguments["taskId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: taskId is required")),
                isError = true
            )

        val existingTask = taskStorage.getTaskById(taskId)
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Task not found: $taskId")),
                isError = true
            )

        val newTitle = arguments["title"]?.jsonPrimitive?.contentOrNull
        val newDescription = arguments["description"]?.jsonPrimitive?.contentOrNull
        val newCategory = arguments["category"]?.jsonPrimitive?.contentOrNull?.let {
            try { TaskCategory.valueOf(it.uppercase().replace(" ", "_")) } catch (e: Exception) { null }
        }
        val newPriority = arguments["priority"]?.jsonPrimitive?.contentOrNull?.let {
            try { TaskPriority.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }
        val newStatus = arguments["status"]?.jsonPrimitive?.contentOrNull?.let {
            try { TaskStatus.valueOf(it.uppercase().replace(" ", "_")) } catch (e: Exception) { null }
        }
        val newTags = arguments["tags"]?.jsonPrimitive?.contentOrNull?.let {
            it.split(",").map { tag -> tag.trim() }.filter { tag -> tag.isNotEmpty() }
        }
        val newEstimatedHours = arguments["estimatedHours"]?.jsonPrimitive?.intOrNull

        val success = taskStorage.updateTask(taskId) { task ->
            task.copy(
                title = newTitle ?: task.title,
                description = newDescription ?: task.description,
                category = newCategory ?: task.category,
                priority = newPriority ?: task.priority,
                status = newStatus ?: task.status,
                tags = newTags ?: task.tags,
                estimatedHours = newEstimatedHours ?: task.estimatedHours,
                completedAt = if (newStatus == TaskStatus.DONE && task.status != TaskStatus.DONE) {
                    System.currentTimeMillis()
                } else if (newStatus != null && newStatus != TaskStatus.DONE) {
                    null
                } else {
                    task.completedAt
                }
            )
        }

        if (!success) {
            return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Failed to update task: $taskId")),
                isError = true
            )
        }

        val updatedTask = taskStorage.getTaskById(taskId)!!
        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = "Task updated successfully:\n\n${formatTask(updatedTask)}")),
            isError = false
        )
    }

    private fun deleteTaskTool(arguments: JsonObject): McpToolCallResult {
        val taskId = arguments["taskId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: taskId is required")),
                isError = true
            )

        val success = taskStorage.deleteTask(taskId)

        return if (success) {
            McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Task deleted successfully: $taskId")),
                isError = false
            )
        } else {
            McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Task not found: $taskId")),
                isError = true
            )
        }
    }

    private fun moveTaskTool(arguments: JsonObject): McpToolCallResult {
        val taskId = arguments["taskId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: taskId is required")),
                isError = true
            )

        val statusStr = arguments["status"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: status is required")),
                isError = true
            )

        val newStatus = try {
            TaskStatus.valueOf(statusStr.uppercase().replace(" ", "_"))
        } catch (e: Exception) {
            return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: Invalid status. Valid values: ${TaskStatus.entries.joinToString()}")),
                isError = true
            )
        }

        val success = taskStorage.updateTaskStatus(taskId, newStatus)

        if (!success) {
            return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Task not found: $taskId")),
                isError = true
            )
        }

        val task = taskStorage.getTaskById(taskId)!!
        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = "Task moved to ${newStatus}:\n\n${formatTask(task)}")),
            isError = false
        )
    }

    private fun getTaskStatsTool(arguments: JsonObject): McpToolCallResult {
        val stats = taskStorage.getTaskStats()

        val output = buildString {
            appendLine("Task Board Statistics:")
            appendLine()
            appendLine("Total tasks: ${stats.totalTasks}")
            appendLine("Completed this week: ${stats.completedThisWeek}")
            appendLine()
            appendLine("By Status:")
            stats.byStatus.forEach { (status, count) ->
                appendLine("  ${status.name.replace("_", " ")}: $count")
            }
            appendLine()
            appendLine("By Category:")
            stats.byCategory.forEach { (category, count) ->
                appendLine("  ${category.name.replace("_", " ")}: $count")
            }
            appendLine()
            appendLine("By Priority:")
            stats.byPriority.forEach { (priority, count) ->
                appendLine("  $priority: $count")
            }
        }

        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = output)),
            isError = false
        )
    }

    private fun handleToolsCall(requestId: Int?, params: JsonObject?): JsonRpcResponse {
        if (params == null) {
            return JsonRpcResponse(
                id = requestId,
                error = JsonRpcError(-32602, "Invalid params")
            )
        }

        val toolName = params["name"]?.jsonPrimitive?.contentOrNull
        val arguments = params["arguments"]?.jsonObject ?: buildJsonObject { }

        log("Handling tools/call for $toolName with args: $arguments")

        val result = when (toolName) {
            "list_tasks" -> listTasksTool(arguments)
            "get_task1" -> getTaskTool(arguments)
            "add_task1" -> addTaskTool(arguments)
            "update_task1" -> updateTaskTool(arguments)
            "delete_task1" -> deleteTaskTool(arguments)
            "move_task1" -> moveTaskTool(arguments)
            "get_task_stats1" -> getTaskStatsTool(arguments)
            else -> {
                return JsonRpcResponse(
                    id = requestId,
                    error = JsonRpcError(-32601, "Unknown tool: $toolName")
                )
            }
        }

        return JsonRpcResponse(
            id = requestId,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun handleRequest(request: JsonRpcRequest) {
        val requestId = request.id
        val method = request.method
        val params = request.params

        log("Received request: $method")

        try {
            val response = when (method) {
                "initialize" -> handleInitialize(requestId, params)
                "tools/list" -> handleToolsList(requestId)
                "tools/call" -> handleToolsCall(requestId, params)
                else -> {
                    sendError(requestId, -32601, "Method not found: $method")
                    return
                }
            }

            sendResponse(response)
        } catch (e: Exception) {
            log("Error handling request: ${e.message}")
            e.printStackTrace(System.err)
            sendError(requestId, -32603, "Internal error: ${e.message}")
        }
    }

    fun run() {
        log("MCP Task Board Server starting...")
        log("Kotlin version: ${KotlinVersion.CURRENT}")
        log("Waiting for requests on stdin...")

        try {
            while (true) {
                val line = readlnOrNull() ?: break
                val trimmedLine = line.trim()

                if (trimmedLine.isEmpty()) {
                    continue
                }

                try {
                    val request = json.decodeFromString<JsonRpcRequest>(trimmedLine)
                    handleRequest(request)
                } catch (e: SerializationException) {
                    log("Invalid JSON received: ${e.message}")
                    sendError(null, -32700, "Parse error")
                }
            }
        } catch (e: Exception) {
            log("Fatal error: ${e.message}")
            e.printStackTrace(System.err)
            throw e
        }

        log("Server shutting down...")
    }
}

fun main() {
    val server = TaskBoardMcpServer()
    server.run()
}
