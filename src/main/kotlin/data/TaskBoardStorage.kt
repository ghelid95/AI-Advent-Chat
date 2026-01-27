package data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class BoardTask(
    val id: String,
    val title: String,
    val description: String,
    val category: TaskCategory,
    val priority: TaskPriority,
    val status: TaskStatus,
    val tags: List<String> = emptyList(),
    val estimatedHours: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val dueDate: Long? = null,
    val completedAt: Long? = null
)

@Serializable
enum class TaskCategory {
    FEATURE,
    IMPROVEMENT,
    BUG_FIX,
    REFACTORING,
    DOCUMENTATION,
    TESTING,
    INFRASTRUCTURE
}

@Serializable
enum class TaskPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
enum class TaskStatus {
    BACKLOG, TODO, IN_PROGRESS, REVIEW, DONE
}

@Serializable
data class TaskBoardData(
    val tasks: List<BoardTask>
)

class TaskBoardStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val tasksFile: File = run {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".ai-advent-chat")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        File(appDir, "task-board.json")
    }

    fun loadTasks(): List<BoardTask> {
        return try {
            if (!tasksFile.exists()) {
                println("[TaskBoard] No tasks file found, creating sample data")
                val sampleTasks = createProjectTasks()
                saveTasks(sampleTasks)
                sampleTasks
            } else {
                val data = json.decodeFromString<TaskBoardData>(tasksFile.readText(Charsets.UTF_8))
                println("[TaskBoard] Loaded ${data.tasks.size} tasks")
                data.tasks
            }
        } catch (e: Exception) {
            println("[TaskBoard] Error loading tasks: ${e.message}")
            e.printStackTrace()
            createProjectTasks()
        }
    }

    fun saveTasks(tasks: List<BoardTask>) {
        try {
            val data = TaskBoardData(tasks)
            val jsonString = json.encodeToString(data)
            tasksFile.writeText(jsonString, Charsets.UTF_8)
            println("[TaskBoard] Saved ${tasks.size} tasks")
        } catch (e: Exception) {
            println("[TaskBoard] Error saving tasks: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getTaskById(id: String): BoardTask? {
        return loadTasks().find { it.id == id }
    }

    fun getTasksByStatus(status: TaskStatus): List<BoardTask> {
        return loadTasks().filter { it.status == status }
    }

    fun getTasksByCategory(category: TaskCategory): List<BoardTask> {
        return loadTasks().filter { it.category == category }
    }

    fun getTasksByPriority(priority: TaskPriority): List<BoardTask> {
        return loadTasks().filter { it.priority == priority }
    }

    fun addTask(task: BoardTask): BoardTask {
        val tasks = loadTasks().toMutableList()
        tasks.add(task)
        saveTasks(tasks)
        return task
    }

    fun updateTask(taskId: String, updater: (BoardTask) -> BoardTask): Boolean {
        val tasks = loadTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return false

        tasks[index] = updater(tasks[index]).copy(updatedAt = System.currentTimeMillis())
        saveTasks(tasks)
        return true
    }

    fun deleteTask(taskId: String): Boolean {
        val tasks = loadTasks().toMutableList()
        val removed = tasks.removeIf { it.id == taskId }
        if (removed) {
            saveTasks(tasks)
        }
        return removed
    }

    fun updateTaskStatus(taskId: String, newStatus: TaskStatus): Boolean {
        return updateTask(taskId) { task ->
            task.copy(
                status = newStatus,
                completedAt = if (newStatus == TaskStatus.DONE) System.currentTimeMillis() else null
            )
        }
    }

    fun getTaskStats(): TaskBoardStats {
        val tasks = loadTasks()
        return TaskBoardStats(
            totalTasks = tasks.size,
            byStatus = TaskStatus.entries.associateWith { status -> tasks.count { it.status == status } },
            byCategory = TaskCategory.entries.associateWith { category -> tasks.count { it.category == category } },
            byPriority = TaskPriority.entries.associateWith { priority -> tasks.count { it.priority == priority } },
            completedThisWeek = tasks.count {
                it.status == TaskStatus.DONE &&
                it.completedAt != null &&
                it.completedAt > System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
            }
        )
    }

    fun createTask(
        title: String,
        description: String,
        category: TaskCategory,
        priority: TaskPriority,
        status: TaskStatus = TaskStatus.BACKLOG,
        tags: List<String> = emptyList(),
        estimatedHours: Int? = null,
        dueDate: Long? = null
    ): BoardTask {
        val now = System.currentTimeMillis()
        val task = BoardTask(
            id = "TASK-${UUID.randomUUID().toString().take(8).uppercase()}",
            title = title,
            description = description,
            category = category,
            priority = priority,
            status = status,
            tags = tags,
            estimatedHours = estimatedHours,
            createdAt = now,
            updatedAt = now,
            dueDate = dueDate
        )
        return addTask(task)
    }

    private fun createProjectTasks(): List<BoardTask> {
        val now = System.currentTimeMillis()
        val day = 86400000L

        return listOf(
            // Feature tasks
            BoardTask(
                id = "TASK-001",
                title = "Add voice input support",
                description = "Implement speech-to-text functionality for chat input. Use system microphone to capture audio and transcribe to text using Whisper API or similar.",
                category = TaskCategory.FEATURE,
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.BACKLOG,
                tags = listOf("voice", "accessibility", "input"),
                estimatedHours = 16,
                createdAt = now - day * 10,
                updatedAt = now - day * 10
            ),
            BoardTask(
                id = "TASK-002",
                title = "Implement chat export to PDF",
                description = "Allow users to export chat conversations to PDF format. Include message timestamps, user/assistant labels, and preserve code formatting with syntax highlighting.",
                category = TaskCategory.FEATURE,
                priority = TaskPriority.LOW,
                status = TaskStatus.BACKLOG,
                tags = listOf("export", "pdf", "sharing"),
                estimatedHours = 8,
                createdAt = now - day * 8,
                updatedAt = now - day * 8
            ),
            BoardTask(
                id = "TASK-003",
                title = "Add multi-model chat comparison",
                description = "Create a split-view mode where the same prompt can be sent to multiple models simultaneously. Display responses side-by-side for easy comparison of model outputs.",
                category = TaskCategory.FEATURE,
                priority = TaskPriority.HIGH,
                status = TaskStatus.TODO,
                tags = listOf("comparison", "multi-model", "ui"),
                estimatedHours = 24,
                createdAt = now - day * 5,
                updatedAt = now - day * 3
            ),
            BoardTask(
                id = "TASK-004",
                title = "Implement prompt templates library",
                description = "Create a library of reusable prompt templates. Users can save, categorize, and quickly insert common prompts. Include variables/placeholders that can be filled in before sending.",
                category = TaskCategory.FEATURE,
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.IN_PROGRESS,
                tags = listOf("templates", "productivity", "prompts"),
                estimatedHours = 12,
                createdAt = now - day * 15,
                updatedAt = now - day * 1
            ),
            BoardTask(
                id = "TASK-005",
                title = "Add conversation branching",
                description = "Allow users to branch off from any message in the conversation to explore alternative responses. Implement a tree-view for navigating between branches.",
                category = TaskCategory.FEATURE,
                priority = TaskPriority.HIGH,
                status = TaskStatus.BACKLOG,
                tags = listOf("branching", "exploration", "ux"),
                estimatedHours = 32,
                createdAt = now - day * 12,
                updatedAt = now - day * 12
            ),

            // Improvement tasks
            BoardTask(
                id = "TASK-006",
                title = "Improve code syntax highlighting",
                description = "Enhance code block rendering with better syntax highlighting support. Add language detection, line numbers, and copy-to-clipboard button for code blocks.",
                category = TaskCategory.IMPROVEMENT,
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.TODO,
                tags = listOf("code", "highlighting", "ui"),
                estimatedHours = 8,
                createdAt = now - day * 7,
                updatedAt = now - day * 4
            ),
            BoardTask(
                id = "TASK-007",
                title = "Add session search functionality",
                description = "Implement full-text search across all chat sessions. Allow users to find specific conversations by searching message content, with result highlighting.",
                category = TaskCategory.IMPROVEMENT,
                priority = TaskPriority.HIGH,
                status = TaskStatus.TODO,
                tags = listOf("search", "sessions", "productivity"),
                estimatedHours = 10,
                createdAt = now - day * 6,
                updatedAt = now - day * 2
            ),
            BoardTask(
                id = "TASK-008",
                title = "Optimize startup performance",
                description = "Reduce application startup time by lazy-loading components and implementing efficient caching. Target sub-2-second cold start time.",
                category = TaskCategory.IMPROVEMENT,
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.BACKLOG,
                tags = listOf("performance", "startup", "optimization"),
                estimatedHours = 6,
                createdAt = now - day * 20,
                updatedAt = now - day * 20
            ),
            BoardTask(
                id = "TASK-009",
                title = "Enhance keyboard navigation",
                description = "Add comprehensive keyboard shortcuts for all main actions. Include shortcuts for sending messages, switching sessions, toggling settings, and navigating chat history.",
                category = TaskCategory.IMPROVEMENT,
                priority = TaskPriority.LOW,
                status = TaskStatus.IN_PROGRESS,
                tags = listOf("keyboard", "accessibility", "shortcuts"),
                estimatedHours = 6,
                createdAt = now - day * 9,
                updatedAt = now - day * 1
            ),
            BoardTask(
                id = "TASK-010",
                title = "Add message reactions/feedback",
                description = "Allow users to mark messages as helpful, incorrect, or save favorites. This data can be used to improve future interactions and create a favorites list.",
                category = TaskCategory.IMPROVEMENT,
                priority = TaskPriority.LOW,
                status = TaskStatus.BACKLOG,
                tags = listOf("feedback", "reactions", "ux"),
                estimatedHours = 8,
                createdAt = now - day * 14,
                updatedAt = now - day * 14
            ),

            // Bug fix tasks
            BoardTask(
                id = "TASK-011",
                title = "Fix message streaming interruption",
                description = "When network connection is unstable, streaming messages sometimes stop mid-response. Implement retry logic and partial message recovery.",
                category = TaskCategory.BUG_FIX,
                priority = TaskPriority.HIGH,
                status = TaskStatus.TODO,
                tags = listOf("streaming", "network", "reliability"),
                estimatedHours = 6,
                createdAt = now - day * 3,
                updatedAt = now - day * 2
            ),
            BoardTask(
                id = "TASK-012",
                title = "Resolve memory leak in session switching",
                description = "Application memory grows when switching between sessions repeatedly. Old session data appears to remain in memory. Profile and fix memory management.",
                category = TaskCategory.BUG_FIX,
                priority = TaskPriority.CRITICAL,
                status = TaskStatus.IN_PROGRESS,
                tags = listOf("memory", "leak", "performance"),
                estimatedHours = 8,
                createdAt = now - day * 4,
                updatedAt = now - day * 1
            ),

            // Refactoring tasks
            BoardTask(
                id = "TASK-013",
                title = "Refactor ChatViewModel state management",
                description = "Split the large ChatUiState into smaller, focused state objects. Improve state update efficiency and make the codebase more maintainable.",
                category = TaskCategory.REFACTORING,
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.BACKLOG,
                tags = listOf("architecture", "state", "viewmodel"),
                estimatedHours = 12,
                createdAt = now - day * 25,
                updatedAt = now - day * 25
            ),
            BoardTask(
                id = "TASK-014",
                title = "Extract MCP tools to separate modules",
                description = "Reorganize MCP server implementations into separate modules/packages for better code organization and potential plugin architecture.",
                category = TaskCategory.REFACTORING,
                priority = TaskPriority.LOW,
                status = TaskStatus.BACKLOG,
                tags = listOf("mcp", "modules", "architecture"),
                estimatedHours = 10,
                createdAt = now - day * 18,
                updatedAt = now - day * 18
            ),

            // Documentation tasks
            BoardTask(
                id = "TASK-015",
                title = "Create user guide documentation",
                description = "Write comprehensive user documentation covering all features, keyboard shortcuts, and configuration options. Include screenshots and examples.",
                category = TaskCategory.DOCUMENTATION,
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.TODO,
                tags = listOf("docs", "user-guide", "help"),
                estimatedHours = 16,
                createdAt = now - day * 11,
                updatedAt = now - day * 5
            ),
            BoardTask(
                id = "TASK-016",
                title = "Document MCP server development",
                description = "Create developer documentation for building custom MCP servers. Include API reference, examples, and integration guide.",
                category = TaskCategory.DOCUMENTATION,
                priority = TaskPriority.LOW,
                status = TaskStatus.BACKLOG,
                tags = listOf("docs", "mcp", "developer"),
                estimatedHours = 12,
                createdAt = now - day * 22,
                updatedAt = now - day * 22
            ),

            // Testing tasks
            BoardTask(
                id = "TASK-017",
                title = "Add unit tests for storage classes",
                description = "Write comprehensive unit tests for SessionStorage, AppSettingsStorage, and other storage classes. Cover edge cases and error scenarios.",
                category = TaskCategory.TESTING,
                priority = TaskPriority.HIGH,
                status = TaskStatus.TODO,
                tags = listOf("tests", "unit-tests", "storage"),
                estimatedHours = 10,
                createdAt = now - day * 16,
                updatedAt = now - day * 8
            ),
            BoardTask(
                id = "TASK-018",
                title = "Implement UI integration tests",
                description = "Create integration tests for main UI flows using Compose testing framework. Test session management, chat interactions, and settings changes.",
                category = TaskCategory.TESTING,
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.BACKLOG,
                tags = listOf("tests", "integration", "ui"),
                estimatedHours = 20,
                createdAt = now - day * 30,
                updatedAt = now - day * 30
            ),

            // Infrastructure tasks
            BoardTask(
                id = "TASK-019",
                title = "Set up CI/CD pipeline",
                description = "Configure GitHub Actions for automated builds, tests, and releases. Include cross-platform builds for Windows, macOS, and Linux.",
                category = TaskCategory.INFRASTRUCTURE,
                priority = TaskPriority.HIGH,
                status = TaskStatus.TODO,
                tags = listOf("ci-cd", "automation", "github"),
                estimatedHours = 8,
                createdAt = now - day * 13,
                updatedAt = now - day * 6
            ),
            BoardTask(
                id = "TASK-020",
                title = "Add application auto-update mechanism",
                description = "Implement automatic update checking and installation. Notify users of new versions and allow one-click updates without manual download.",
                category = TaskCategory.INFRASTRUCTURE,
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.BACKLOG,
                tags = listOf("updates", "deployment", "ux"),
                estimatedHours = 16,
                createdAt = now - day * 28,
                updatedAt = now - day * 28
            )
        )
    }
}

@Serializable
data class TaskBoardStats(
    val totalTasks: Int,
    val byStatus: Map<TaskStatus, Int>,
    val byCategory: Map<TaskCategory, Int>,
    val byPriority: Map<TaskPriority, Int>,
    val completedThisWeek: Int
)
