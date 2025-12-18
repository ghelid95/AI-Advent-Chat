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
            if (!settingsFile.exists()) {
                println("[Settings] No app settings file found, using defaults")
                return AppSettings()
            }
            val settings = json.decodeFromString<AppSettings>(settingsFile.readText())
            println("[Settings] Loaded app settings with ${settings.mcpServers.size} MCP servers")
            settings
        } catch (e: Exception) {
            println("[Settings] Error loading app settings: ${e.message}")
            e.printStackTrace()
            AppSettings()
        }
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
