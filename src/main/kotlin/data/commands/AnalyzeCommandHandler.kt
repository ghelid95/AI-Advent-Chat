package data.commands

import data.CodeAssistantSettings
import java.io.File

/**
 * Handler for /analyze command
 * Analyzes a specific file in detail
 */
class AnalyzeCommandHandler : CommandHandler {

    override suspend fun execute(command: ChatCommand, settings: CodeAssistantSettings): CommandResult {
        val analyzeCmd = command as? ChatCommand.Analyze
            ?: return CommandResult.Error("Invalid command type")

        val workingDir = settings.workingDirectory?.let { File(it) }
            ?: return CommandResult.Error("No working directory configured")

        val filePath = analyzeCmd.arguments.joinToString(" ")

        // Resolve file path
        val targetFile = File(workingDir, filePath)
        if (!targetFile.exists()) {
            // Try absolute path
            val absoluteFile = File(filePath)
            if (!absoluteFile.exists()) {
                return CommandResult.Error("File not found: $filePath")
            }
            return analyzeFile(absoluteFile, workingDir, settings)
        }

        return analyzeFile(targetFile, workingDir, settings)
    }

    private fun analyzeFile(file: File, workingDir: File, settings: CodeAssistantSettings): CommandResult {
        if (!file.isFile) {
            return CommandResult.Error("Path is not a file: ${file.path}")
        }

        // Read file content
        val content = try {
            file.readText()
        } catch (e: Exception) {
            return CommandResult.Error("Unable to read file: ${e.message}")
        }

        if (content.length > settings.maxFileSize) {
            return CommandResult.Error("File too large (${content.length} chars, max ${settings.maxFileSize})")
        }

        // Build analysis context
        val analysis = buildAnalysisContext(file, workingDir, content)

        // Send to LLM for intelligent analysis
        return CommandResult.Success(analysis, sendToLlm = true)
    }

    private fun buildAnalysisContext(file: File, workingDir: File, content: String): String {
        val relativePath = try {
            file.relativeTo(workingDir).path.replace(File.separator, "/")
        } catch (e: Exception) {
            file.absolutePath
        }

        val context = StringBuilder()
        context.appendLine("=== FILE ANALYSIS REQUEST ===")
        context.appendLine()
        context.appendLine("File: $relativePath")
        context.appendLine("Size: ${content.length} characters")
        context.appendLine("Lines: ${content.lines().size}")
        context.appendLine("Extension: ${file.extension}")
        context.appendLine()
        context.appendLine("=== FILE CONTENT ===")
        context.appendLine("```${file.extension}")
        context.appendLine(content)
        context.appendLine("```")
        context.appendLine()
        context.appendLine("Please provide a detailed analysis of this file including:")
        context.appendLine("1. Purpose and responsibility of this file")
        context.appendLine("2. Key classes, functions, or components defined")
        context.appendLine("3. Dependencies and imports")
        context.appendLine("4. Notable patterns or architectural decisions")
        context.appendLine("5. Code quality observations")
        context.appendLine("6. Potential issues or improvements")

        return context.toString()
    }
}
