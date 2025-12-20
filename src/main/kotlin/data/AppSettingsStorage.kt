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
    val pipelineMaxIterations: Int = 5
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
                if (!loadedSettings.mcpServers.any { it.id == "shell-command-server" }) {
                    println("[Settings] Shell command server not found, adding it automatically")
                    needsSave = true
                    addShellCommandServer(loadedSettings)
                } else {
                    loadedSettings
                }
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
            pipelineMaxIterations = 5
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
