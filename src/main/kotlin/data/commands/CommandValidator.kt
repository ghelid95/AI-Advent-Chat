package data.commands

import data.CodeAssistantSettings
import java.io.File

/**
 * Validates code assistant settings for command execution
 */
object CommandValidator {

    /**
     * Validate that code assistant settings are properly configured
     *
     * @param settings The settings to validate
     * @return Error message if invalid, null if valid
     */
    fun validateSettings(settings: CodeAssistantSettings): String? {
        if (!settings.enabled) {
            return "Code Assistant is not enabled. Please enable it in Code Assistant settings (Code icon in toolbar)."
        }

        if (settings.workingDirectory.isNullOrBlank()) {
            return "No working directory configured. Please set it in Code Assistant settings."
        }

        val dir = File(settings.workingDirectory)
        if (!dir.exists()) {
            return "Working directory does not exist: ${settings.workingDirectory}"
        }

        if (!dir.isDirectory) {
            return "Working directory path is not a directory: ${settings.workingDirectory}"
        }

        return null // Valid
    }
}
