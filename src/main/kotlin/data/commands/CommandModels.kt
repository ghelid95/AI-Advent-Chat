package data.commands

/**
 * Result of executing a command
 */
sealed class CommandResult {
    /**
     * Command executed successfully
     * @param output The result text to display
     * @param sendToLlm If true, send output to LLM for intelligent formatting; if false, display directly
     */
    data class Success(val output: String, val sendToLlm: Boolean = false) : CommandResult()

    /**
     * Command execution failed
     * @param message Error message to display to user
     */
    data class Error(val message: String) : CommandResult()
}

/**
 * Represents a chat command parsed from user input
 */
sealed class ChatCommand {
    abstract val name: String
    abstract val arguments: List<String>

    /**
     * /help - Show detailed project information
     */
    data class Help(override val arguments: List<String> = emptyList()) : ChatCommand() {
        override val name = "help"
    }

    /**
     * /search <query> - Search for files or code
     */
    data class Search(override val arguments: List<String>) : ChatCommand() {
        override val name = "search"
    }

    /**
     * /analyze <file> - Analyze a specific file
     */
    data class Analyze(override val arguments: List<String>) : ChatCommand() {
        override val name = "analyze"
    }

    /**
     * /context on|off - Toggle auto-context enrichment
     */
    data class Context(override val arguments: List<String>) : ChatCommand() {
        override val name = "context"
    }

    /**
     * /git status - Show repository status
     */
    data class GitStatus(override val arguments: List<String> = emptyList()) : ChatCommand() {
        override val name = "git"
    }

    /**
     * /git diff [file] - Show diffs
     */
    data class GitDiff(override val arguments: List<String>) : ChatCommand() {
        override val name = "git"
    }

    /**
     * /git log - Show commit history
     */
    data class GitLog(override val arguments: List<String> = emptyList()) : ChatCommand() {
        override val name = "git"
    }

    /**
     * /git branch - Show current branch info
     */
    data class GitBranch(override val arguments: List<String> = emptyList()) : ChatCommand() {
        override val name = "git"
    }
}

/**
 * Result of parsing user input as a command
 */
data class CommandParseResult(
    val command: ChatCommand?,
    val isCommand: Boolean,
    val error: String? = null
)
