package data.commands

import data.CodeAssistantSettings

/**
 * Interface for command handlers
 */
interface CommandHandler {
    /**
     * Execute the command
     *
     * @param command The command to execute
     * @param settings Current code assistant settings
     * @return CommandResult with success or error
     */
    suspend fun execute(command: ChatCommand, settings: CodeAssistantSettings): CommandResult
}
