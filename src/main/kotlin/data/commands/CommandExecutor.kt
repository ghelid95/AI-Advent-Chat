package data.commands

import data.CodeAssistantSettings
import data.codeassistant.ContentSearchService
import data.codeassistant.FileSearchService
import data.codeassistant.ProjectAnalysisService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes chat commands
 */
class CommandExecutor(
    private val projectAnalysisService: ProjectAnalysisService,
    private val fileSearchService: FileSearchService,
    private val contentSearchService: ContentSearchService,
    private val gitRepositoryService: data.git.GitRepositoryService
) {
    private val handlers = mapOf<String, CommandHandler>(
        "help" to HelpCommandHandler(projectAnalysisService, fileSearchService),
        "search" to SearchCommandHandler(fileSearchService, contentSearchService),
        "analyze" to AnalyzeCommandHandler(),
        "context" to ContextCommandHandler(),
        "git" to GitCommandHandler(gitRepositoryService),
        "review-pr" to ReviewPrCommandHandler(gitRepositoryService)
    )

    /**
     * Execute a command
     *
     * @param command The command to execute
     * @param settings Current code assistant settings
     * @return CommandResult with success or error
     */
    suspend fun execute(command: ChatCommand, settings: CodeAssistantSettings): CommandResult {
        // Validate settings (except for context command which changes settings)
        if (command !is ChatCommand.Context) {
            val validationError = CommandValidator.validateSettings(settings)
            if (validationError != null) {
                return CommandResult.Error(validationError)
            }
        }

        val handler = handlers[command.name]
            ?: return CommandResult.Error("No handler for command: ${command.name}")

        return withContext(Dispatchers.IO) {
            try {
                handler.execute(command, settings)
            } catch (e: Exception) {
                e.printStackTrace()
                CommandResult.Error("Command execution failed: ${e.message}")
            }
        }
    }
}
