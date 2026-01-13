package data.commands

import data.CodeAssistantSettings
import data.codeassistant.FileMatch
import data.codeassistant.FileSearchService
import data.codeassistant.ProjectAnalysisService
import data.codeassistant.ProjectInfo
import java.io.File

/**
 * Handler for /help command
 * Generates comprehensive project report for LLM to format
 */
class HelpCommandHandler(
    private val projectAnalysisService: ProjectAnalysisService,
    private val fileSearchService: FileSearchService
) : CommandHandler {

    override suspend fun execute(command: ChatCommand, settings: CodeAssistantSettings): CommandResult {
        val workingDir = settings.workingDirectory?.let { File(it) }
            ?: return CommandResult.Error("No working directory configured")

        if (!workingDir.exists()) {
            return CommandResult.Error("Working directory does not exist: ${workingDir.absolutePath}")
        }

        // Gather comprehensive project information
        val projectInfo = projectAnalysisService.analyzeProject(workingDir)
            ?: return CommandResult.Error("Failed to analyze project")

        // Get file tree
        val fileTree = fileSearchService.getFileTree(workingDir, settings)

        // Get sample files from each major category
        val sampleFiles = getSampleFiles(workingDir, settings, fileTree.fileStructure)

        // Build rich context report
        val report = buildHelpReport(projectInfo, fileTree, sampleFiles)

        // Return with sendToLlm=true so Claude formats it nicely
        return CommandResult.Success(report, sendToLlm = true)
    }

    private fun getSampleFiles(
        workingDir: File,
        settings: CodeAssistantSettings,
        fileStructure: Map<String, Int>
    ): Map<String, List<FileMatch>> {
        val samples = mutableMapOf<String, List<FileMatch>>()

        // Get samples from major file types (top 5 by count)
        val majorExtensions = fileStructure.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .filter { it != "(no extension)" } // Skip files without extension

        for (ext in majorExtensions) {
            val pattern = "**/*.$ext"
            try {
                val matches = fileSearchService.searchByPattern(pattern, workingDir, settings)
                    .take(3) // Max 3 samples per type
                samples[ext] = matches
            } catch (e: Exception) {
                // Skip if pattern fails
                continue
            }
        }

        return samples
    }

    private fun buildHelpReport(
        projectInfo: ProjectInfo,
        fileTree: ProjectInfo,
        sampleFiles: Map<String, List<FileMatch>>
    ): String {
        val report = StringBuilder()

        report.appendLine("=== PROJECT ANALYSIS REPORT ===")
        report.appendLine()

        // Project metadata
        report.appendLine("## Project Overview")
        report.appendLine("Type: ${projectInfo.projectType}")
        report.appendLine("Root: ${projectInfo.rootDirectory.absolutePath}")
        report.appendLine("Total Files: ${fileTree.totalFiles}")
        report.appendLine("Total Size: ${fileTree.totalSize / 1024} KB")
        report.appendLine()

        // Metadata
        if (projectInfo.metadata.isNotEmpty()) {
            report.appendLine("## Project Metadata")
            projectInfo.metadata.forEach { (key, value) ->
                report.appendLine("- $key: $value")
            }
            report.appendLine()
        }

        // README
        if (!projectInfo.readmeContent.isNullOrBlank()) {
            report.appendLine("## README Content")
            report.appendLine(projectInfo.readmeContent.take(1500))
            if (projectInfo.readmeContent.length > 1500) {
                report.appendLine("... (truncated)")
            }
            report.appendLine()
        }

        // File structure
        report.appendLine("## File Structure")
        fileTree.fileStructure.entries
            .sortedByDescending { it.value }
            .take(15)
            .forEach { (ext, count) ->
                report.appendLine("- $ext: $count files")
            }
        report.appendLine()

        // Code samples
        if (sampleFiles.isNotEmpty()) {
            report.appendLine("## Code Samples")
            sampleFiles.forEach { (ext, files) ->
                if (files.isNotEmpty()) {
                    report.appendLine("### $ext Files")
                    files.forEach { file ->
                        report.appendLine("#### ${file.relativePath}")
                        try {
                            val content = file.file.readText().take(600)
                            report.appendLine("```$ext")
                            report.appendLine(content)
                            if (file.file.readText().length > 600) {
                                report.appendLine("... (truncated)")
                            }
                            report.appendLine("```")
                            report.appendLine()
                        } catch (e: Exception) {
                            report.appendLine("(Unable to read file)")
                            report.appendLine()
                        }
                    }
                }
            }
        }

        report.appendLine("=== END REPORT ===")
        report.appendLine()
        report.appendLine("Please provide a comprehensive, well-formatted analysis of this project including:")
        report.appendLine("1. Project type and purpose (based on README and metadata)")
        report.appendLine("2. Key architectural patterns identified in the code samples")
        report.appendLine("3. Main technologies and frameworks used")
        report.appendLine("4. File organization and structure")
        report.appendLine("5. Notable code patterns, styles, or conventions from the samples")
        report.appendLine("6. Any interesting or unique aspects of the codebase")

        return report.toString()
    }
}
