package data.commands

import data.CodeAssistantSettings

/**
 * Handler for /context command
 * Toggles auto-context enrichment on/off
 */
class ContextCommandHandler : CommandHandler {

    override suspend fun execute(command: ChatCommand, settings: CodeAssistantSettings): CommandResult {
        val contextCmd = command as? ChatCommand.Context
            ?: return CommandResult.Error("Invalid command type")

        val action = contextCmd.arguments[0].lowercase()
        val newState = action == "on"

        val message = if (newState) {
            "[System] Auto-context enrichment enabled. Code context will be automatically added to your queries."
        } else {
            "[System] Auto-context enrichment disabled. Queries will be sent without automatic code context."
        }

        // This is a settings update command - needs special handling in ViewModel
        // The message indicates what should happen, and ViewModel will update settings
        return CommandResult.Success(message, sendToLlm = false)
    }
}
