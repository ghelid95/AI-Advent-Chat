package data

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Utility for loading developer personalization from external JSON file.
 * This allows users to edit their preferences without recompiling the application.
 */
class PersonalizationLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Loads personalization from the developer-personalization.json file
     * in the project root directory.
     *
     * @param projectDir The project directory containing the configuration file
     * @return DeveloperPersonalization instance or null if file doesn't exist
     */
    fun loadFromFile(projectDir: File = File(System.getProperty("user.dir"))): DeveloperPersonalization? {
        val configFile = File(projectDir, "developer-personalization.json")

        return try {
            if (!configFile.exists()) {
                println("[Personalization] Configuration file not found at: ${configFile.absolutePath}")
                return null
            }

            val personalization = json.decodeFromString<DeveloperPersonalization>(configFile.readText(Charsets.UTF_8))
            println("[Personalization] Loaded developer personalization (enabled: ${personalization.enabled})")
            personalization
        } catch (e: Exception) {
            println("[Personalization] Error loading personalization: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads personalization from app settings and applies external config if available.
     * External config takes precedence over app settings.
     *
     * @param appSettings Current application settings
     * @param projectDir Optional project directory to search for config file
     * @return DeveloperPersonalization to use
     */
    fun loadPersonalization(
        appSettings: AppSettings,
        projectDir: File? = null
    ): DeveloperPersonalization {
        // Try to load from external file first
        val externalPersonalization = if (projectDir != null) {
            loadFromFile(projectDir)
        } else {
            loadFromFile()
        }

        // Return external config if available and enabled, otherwise use app settings
        return if (externalPersonalization?.enabled == true) {
            println("[Personalization] Using external configuration from developer-personalization.json")
            externalPersonalization
        } else {
            println("[Personalization] Using configuration from app settings")
            appSettings.developerPersonalization
        }
    }

    /**
     * Saves personalization to the external JSON file.
     *
     * @param personalization The personalization to save
     * @param projectDir The project directory
     * @return True if saved successfully, false otherwise
     */
    fun saveToFile(
        personalization: DeveloperPersonalization,
        projectDir: File = File(System.getProperty("user.dir"))
    ): Boolean {
        val configFile = File(projectDir, "developer-personalization.json")

        return try {
            val jsonString = json.encodeToString(DeveloperPersonalization.serializer(), personalization)
            configFile.writeText(jsonString, Charsets.UTF_8)
            println("[Personalization] Saved developer personalization to: ${configFile.absolutePath}")
            true
        } catch (e: Exception) {
            println("[Personalization] Error saving personalization: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Creates a template configuration file with default values.
     * Useful for first-time setup.
     *
     * @param projectDir The project directory
     * @return True if created successfully, false if file already exists or error occurred
     */
    fun createTemplateFile(projectDir: File = File(System.getProperty("user.dir"))): Boolean {
        val configFile = File(projectDir, "developer-personalization.json")

        if (configFile.exists()) {
            println("[Personalization] Configuration file already exists at: ${configFile.absolutePath}")
            return false
        }

        val defaultPersonalization = PersonalizationInitializer.createDefaultPersonalization()
        return saveToFile(defaultPersonalization.copy(enabled = true), projectDir)
    }
}
