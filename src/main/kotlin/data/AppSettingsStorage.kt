package data

import data.mcp.McpServerConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppSettings(
    val mcpServers: List<McpServerConfig> = emptyList(),
    val pipelineEnabled: Boolean = true,
    val pipelineMaxIterations: Int = 5,
    val embeddingsEnabled: Boolean = false,
    val selectedEmbeddingFile: String? = null,
    val embeddingTopK: Int = 3,
    val embeddingThreshold: Float = 0.5f,
    val codeAssistantSettings: CodeAssistantSettings = CodeAssistantSettings()
)

class AppSettingsStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val settingsFile: File = run {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".ai-advent-chat")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        File(appDir, "app-settings.json")
    }

    fun loadSettings(): AppSettings {
        return try {
            val isNewFile = !settingsFile.exists()
            var needsSave = false

            val settings = if (isNewFile) {
                println("[Settings] No app settings file found, creating defaults with shell command server")
                needsSave = true
                createDefaultSettings()
            } else {
                val loadedSettings = json.decodeFromString<AppSettings>(settingsFile.readText())
                println("[Settings] Loaded app settings with ${loadedSettings.mcpServers.size} MCP servers")

                // Auto-add shell command server if not present
                var updatedSettings = if (!loadedSettings.mcpServers.any { it.id == "shell-command-server" }) {
                    println("[Settings] Shell command server not found, adding it automatically")
                    needsSave = true
                    addShellCommandServer(loadedSettings)
                } else {
                    loadedSettings
                }

                // Auto-add git server if not present
                updatedSettings = if (!updatedSettings.mcpServers.any { it.id == "git-server" }) {
                    println("[Settings] Git server not found, adding it automatically")
                    needsSave = true
                    addGitServer(updatedSettings)
                } else {
                    updatedSettings
                }

                // Auto-add project docs server if not present
                updatedSettings = if (!updatedSettings.mcpServers.any { it.id == "project-docs-server" }) {
                    println("[Settings] Project docs server not found, adding it automatically")
                    needsSave = true
                    addProjectDocsServer(updatedSettings)
                } else {
                    updatedSettings
                }

                // Auto-add issue tickets server if not present
                updatedSettings = if (!updatedSettings.mcpServers.any { it.id == "issue-tickets-server" }) {
                    println("[Settings] Issue tickets server not found, adding it automatically")
                    needsSave = true
                    addIssueTicketsServer(updatedSettings)
                } else {
                    updatedSettings
                }

                // Auto-add task board server if not present
                updatedSettings = if (!updatedSettings.mcpServers.any { it.id == "task-board-server" }) {
                    println("[Settings] Task board server not found, adding it automatically")
                    needsSave = true
                    addTaskBoardServer(updatedSettings)
                } else {
                    updatedSettings
                }

                // Auto-add GitHub server if not present
                updatedSettings = if (!updatedSettings.mcpServers.any { it.id == "github-server" }) {
                    println("[Settings] GitHub server not found, adding it automatically")
                    needsSave = true
                    addGitHubServer(updatedSettings)
                } else {
                    updatedSettings
                }

                updatedSettings
            }

            // Save settings if they were modified
            if (needsSave) {
                saveSettings(settings)
            }

            settings
        } catch (e: Exception) {
            println("[Settings] Error loading app settings: ${e.message}")
            e.printStackTrace()
            createDefaultSettings()
        }
    }

    private fun createDefaultSettings(): AppSettings {
        val shellCommandServer = createShellCommandServerConfig()
        return AppSettings(
            mcpServers = listOf(shellCommandServer),
            pipelineEnabled = true,
            pipelineMaxIterations = 5,
            embeddingsEnabled = false,
            selectedEmbeddingFile = null,
            embeddingTopK = 3,
            embeddingThreshold = 0.5f
        )
    }

    private fun addShellCommandServer(settings: AppSettings): AppSettings {
        val shellCommandServer = createShellCommandServerConfig()
        return settings.copy(
            mcpServers = settings.mcpServers + shellCommandServer
        )
    }

    private fun createShellCommandServerConfig(): McpServerConfig {
        val projectDir = detectProjectDirectory()
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val scriptName = if (isWindows) "run-mcp-shell-server.bat" else "run-mcp-shell-server.sh"
        val scriptPath = File(projectDir, scriptName).absolutePath

        println("[Settings] Creating shell command server config with script: $scriptPath")

        return McpServerConfig(
            id = "shell-command-server",
            name = "Shell Command Server",
            command = scriptPath,
            args = emptyList(),
            env = emptyMap(),
            enabled = true
        )
    }

    private fun addGitServer(settings: AppSettings): AppSettings {
        val gitServer = createGitServerConfig()
        return settings.copy(
            mcpServers = settings.mcpServers + gitServer
        )
    }

    private fun createGitServerConfig(): McpServerConfig {
        val projectDir = detectProjectDirectory()
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val scriptName = if (isWindows) "run-mcp-git-server.bat" else "run-mcp-git-server.sh"
        val scriptPath = File(projectDir, scriptName).absolutePath

        println("[Settings] Creating git server config with script: $scriptPath")

        return McpServerConfig(
            id = "git-server",
            name = "Git Server",
            command = scriptPath,
            args = emptyList(),
            env = emptyMap(),
            enabled = true
        )
    }

    private fun addProjectDocsServer(settings: AppSettings): AppSettings {
        val projectDocsServer = createProjectDocsServerConfig()
        return settings.copy(
            mcpServers = settings.mcpServers + projectDocsServer
        )
    }

    private fun addIssueTicketsServer(settings: AppSettings): AppSettings {
        val issueTicketsServer = createIssueTicketsServerConfig()
        return settings.copy(
            mcpServers = settings.mcpServers + issueTicketsServer
        )
    }

    private fun createIssueTicketsServerConfig(): McpServerConfig {
        val projectDir = detectProjectDirectory()
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val scriptName = if (isWindows) "run-mcp-issue-tickets-server.bat" else "run-mcp-issue-tickets-server.sh"
        val scriptPath = File(projectDir, scriptName).absolutePath

        println("[Settings] Creating issue tickets server config with script: $scriptPath")

        return McpServerConfig(
            id = "issue-tickets-server",
            name = "Issue Tickets Server",
            command = scriptPath,
            args = emptyList(),
            env = emptyMap(),
            enabled = true
        )
    }

    private fun createProjectDocsServerConfig(): McpServerConfig {
        val projectDir = detectProjectDirectory()
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val scriptName = if (isWindows) "run-mcp-project-docs-server.bat" else "run-mcp-project-docs-server.sh"
        val scriptPath = File(projectDir, scriptName).absolutePath

        println("[Settings] Creating project docs server config with script: $scriptPath")

        return McpServerConfig(
            id = "project-docs-server",
            name = "Project Documentation Server",
            command = scriptPath,
            args = emptyList(),
            env = emptyMap(),
            enabled = false  // Disabled by default, user enables when they want it
        )
    }

    private fun addTaskBoardServer(settings: AppSettings): AppSettings {
        val taskBoardServer = createTaskBoardServerConfig()
        return settings.copy(
            mcpServers = settings.mcpServers + taskBoardServer
        )
    }

    private fun createTaskBoardServerConfig(): McpServerConfig {
        val projectDir = detectProjectDirectory()
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val scriptName = if (isWindows) "run-mcp-task-board-server.bat" else "run-mcp-task-board-server.sh"
        val scriptPath = File(projectDir, scriptName).absolutePath

        println("[Settings] Creating task board server config with script: $scriptPath")

        return McpServerConfig(
            id = "task-board-server",
            name = "Task Board Server",
            command = scriptPath,
            args = emptyList(),
            env = emptyMap(),
            enabled = true
        )
    }

    private fun addGitHubServer(settings: AppSettings): AppSettings {
        val gitHubServer = createGitHubServerConfig()
        return settings.copy(
            mcpServers = settings.mcpServers + gitHubServer
        )
    }

    private fun createGitHubServerConfig(): McpServerConfig {
        val projectDir = detectProjectDirectory()
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val scriptName = if (isWindows) "run-mcp-github-server.bat" else "run-mcp-github-server.sh"
        val scriptPath = File(projectDir, scriptName).absolutePath

        println("[Settings] Creating GitHub server config with script: $scriptPath")

        return McpServerConfig(
            id = "github-server",
            name = "GitHub Server",
            command = scriptPath,
            args = emptyList(),
            env = emptyMap(),  // GITHUB_TOKEN should be set in system environment
            enabled = true
        )
    }

    private fun detectProjectDirectory(): String {
        // Try current working directory first
        val workingDir = File(System.getProperty("user.dir"))

        // Check if we're in the project directory by looking for gradlew or build.gradle.kts
        if (File(workingDir, "gradlew").exists() ||
            File(workingDir, "gradlew.bat").exists() ||
            File(workingDir, "build.gradle.kts").exists()) {
            println("[Settings] Detected project directory: ${workingDir.absolutePath}")
            return workingDir.absolutePath
        }

        // If not found, try to find it relative to the JAR location
        val jarLocation = File(
            AppSettingsStorage::class.java.protectionDomain.codeSource.location.toURI()
        )
        var currentDir = if (jarLocation.isDirectory) jarLocation else jarLocation.parentFile

        // Search up to 3 levels up
        repeat(3) {
            if (File(currentDir, "gradlew").exists() ||
                File(currentDir, "gradlew.bat").exists() ||
                File(currentDir, "build.gradle.kts").exists()) {
                println("[Settings] Detected project directory: ${currentDir.absolutePath}")
                return currentDir.absolutePath
            }
            currentDir = currentDir.parentFile ?: currentDir
        }

        // Fall back to working directory
        println("[Settings] Could not detect project directory, using working directory: ${workingDir.absolutePath}")
        return workingDir.absolutePath
    }

    fun saveSettings(settings: AppSettings) {
        try {
            val jsonString = json.encodeToString(settings)
            settingsFile.writeText(jsonString)
            println("[Settings] Saved app settings with ${settings.mcpServers.size} MCP servers")
        } catch (e: Exception) {
            println("[Settings] Error saving app settings: ${e.message}")
            e.printStackTrace()
        }
    }
}
