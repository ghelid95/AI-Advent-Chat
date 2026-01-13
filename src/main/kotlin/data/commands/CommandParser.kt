package data.commands

/**
 * Parses user input into chat commands
 */
class CommandParser {

    /**
     * Parse user input to determine if it's a command and which type
     *
     * @param input The raw user input
     * @return CommandParseResult with parsed command, error, or indication it's not a command
     */
    fun parse(input: String): CommandParseResult {
        // Not a command if doesn't start with /
        if (!input.startsWith("/")) {
            return CommandParseResult(null, false)
        }

        // Split by whitespace (supports multiple spaces/tabs)
        val parts = input.substring(1).split(Regex("\\s+")).filter { it.isNotBlank() }

        if (parts.isEmpty()) {
            return CommandParseResult(null, true, "Empty command")
        }

        val commandName = parts[0].lowercase()
        val args = parts.drop(1)

        return when (commandName) {
            "help" -> CommandParseResult(ChatCommand.Help(args), true)

            "search" -> {
                if (args.isEmpty()) {
                    CommandParseResult(null, true, "Search requires a query argument. Usage: /search <query>")
                } else {
                    CommandParseResult(ChatCommand.Search(args), true)
                }
            }

            "analyze" -> {
                if (args.isEmpty()) {
                    CommandParseResult(null, true, "Analyze requires a file path. Usage: /analyze <file>")
                } else {
                    CommandParseResult(ChatCommand.Analyze(args), true)
                }
            }

            "context" -> {
                if (args.isEmpty()) {
                    CommandParseResult(null, true, "Context requires 'on' or 'off' argument. Usage: /context on|off")
                } else if (args[0].lowercase() != "on" && args[0].lowercase() != "off") {
                    CommandParseResult(null, true, "Context argument must be 'on' or 'off'. Usage: /context on|off")
                } else {
                    CommandParseResult(ChatCommand.Context(args), true)
                }
            }

            "git" -> parseGitCommand(args)

            else -> CommandParseResult(null, true, "Unknown command: /$commandName. Available commands: /help, /search, /analyze, /context, /git")
        }
    }

    /**
     * Parse git subcommands
     */
    private fun parseGitCommand(args: List<String>): CommandParseResult {
        if (args.isEmpty()) {
            return CommandParseResult(null, true, "Usage: /git <status|diff|log|branch>")
        }

        return when (args[0].lowercase()) {
            "status" -> CommandParseResult(ChatCommand.GitStatus(args.drop(1)), true)
            "diff" -> CommandParseResult(ChatCommand.GitDiff(args.drop(1)), true)
            "log" -> CommandParseResult(ChatCommand.GitLog(args.drop(1)), true)
            "branch" -> CommandParseResult(ChatCommand.GitBranch(args.drop(1)), true)
            else -> CommandParseResult(null, true, "Unknown git subcommand: ${args[0]}. Usage: /git <status|diff|log|branch>")
        }
    }
}
