package data.commands

import data.CodeAssistantSettings
import data.codeassistant.ContentMatch
import data.codeassistant.ContentSearchService
import data.codeassistant.FileMatch
import data.codeassistant.FileSearchService
import data.codeassistant.SearchOptions
import java.io.File
import java.util.Date

/**
 * Handler for /search command
 * Searches for files and code content
 */
class SearchCommandHandler(
    private val fileSearchService: FileSearchService,
    private val contentSearchService: ContentSearchService
) : CommandHandler {

    override suspend fun execute(command: ChatCommand, settings: CodeAssistantSettings): CommandResult {
        val searchCmd = command as? ChatCommand.Search
            ?: return CommandResult.Error("Invalid command type")

        val workingDir = settings.workingDirectory?.let { File(it) }
            ?: return CommandResult.Error("No working directory configured")

        val query = searchCmd.arguments.joinToString(" ")

        // Try as filename first
        val fileMatches = fileSearchService.searchByFileName(query, workingDir, settings, fuzzy = true)

        // Also search content
        val contentMatches = contentSearchService.searchContent(
            query,
            workingDir,
            settings,
            SearchOptions(caseSensitive = false, contextLines = 2, maxMatches = 50)
        )

        // Format results
        val output = formatSearchResults(query, fileMatches, contentMatches)

        // Client-side only, no LLM needed
        return CommandResult.Success(output, sendToLlm = false)
    }

    private fun formatSearchResults(
        query: String,
        fileMatches: List<FileMatch>,
        contentMatches: List<ContentMatch>
    ): String {
        val result = StringBuilder()

        result.appendLine("Search results for: \"$query\"")
        result.appendLine()

        if (fileMatches.isNotEmpty()) {
            result.appendLine("=== File Name Matches (${fileMatches.size}) ===")
            fileMatches.take(20).forEach { match ->
                result.appendLine("${match.relativePath}")
                result.appendLine("  Size: ${match.size} bytes | Modified: ${Date(match.lastModified)}")
                result.appendLine("  Reason: ${match.matchReason}")
                result.appendLine()
            }
            if (fileMatches.size > 20) {
                result.appendLine("... and ${fileMatches.size - 20} more file matches")
                result.appendLine()
            }
        }

        if (contentMatches.isNotEmpty()) {
            result.appendLine("=== Content Matches (${contentMatches.size}) ===")
            contentMatches.take(20).forEach { match ->
                result.appendLine("${match.relativePath}:${match.lineNumber}")
                result.appendLine("  ${match.matchedLine.trim()}")
                result.appendLine()
            }
            if (contentMatches.size > 20) {
                result.appendLine("... and ${contentMatches.size - 20} more content matches")
                result.appendLine()
            }
        }

        if (fileMatches.isEmpty() && contentMatches.isEmpty()) {
            result.appendLine("No results found.")
        }

        return result.toString()
    }
}
