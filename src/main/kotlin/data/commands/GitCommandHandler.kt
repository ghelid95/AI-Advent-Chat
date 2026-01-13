package data.commands

import data.CodeAssistantSettings
import data.git.GitRepositoryService
import java.io.File

/**
 * Handler for /git commands
 * Provides git repository inspection capabilities
 */
class GitCommandHandler(
    private val gitRepositoryService: GitRepositoryService
) : CommandHandler {

    override suspend fun execute(command: ChatCommand, settings: CodeAssistantSettings): CommandResult {
        val workingDir = settings.workingDirectory?.let { File(it) }
            ?: return CommandResult.Error("No working directory configured")

        if (!settings.gitEnabled) {
            return CommandResult.Error("Git integration is not enabled. Enable it in Code Assistant settings.")
        }

        if (!gitRepositoryService.isGitRepository(workingDir)) {
            return CommandResult.Error("Working directory is not a git repository")
        }

        return when (command) {
            is ChatCommand.GitStatus -> handleStatus(workingDir)
            is ChatCommand.GitDiff -> handleDiff(workingDir, command.arguments)
            is ChatCommand.GitLog -> handleLog(workingDir, settings)
            is ChatCommand.GitBranch -> handleBranch(workingDir)
            else -> CommandResult.Error("Invalid git command")
        }
    }

    /**
     * Handle /git status command
     */
    private fun handleStatus(workingDir: File): CommandResult {
        val result = gitRepositoryService.getRepositoryStatus(workingDir)

        return result.fold(
            onSuccess = { status ->
                val output = buildString {
                    appendLine("=".repeat(60))
                    appendLine("Git Repository Status")
                    appendLine("=".repeat(60))
                    appendLine()
                    appendLine("Branch: ${status.branch}")
                    if (status.remoteUrl != null) {
                        appendLine("Remote: ${status.remoteUrl}")
                    }
                    appendLine()

                    if (status.isClean) {
                        appendLine("✓ Working directory is clean")
                    } else {
                        appendLine("Changes present:")
                        appendLine()

                        if (status.stagedFiles.isNotEmpty()) {
                            appendLine("Staged files (${status.stagedFiles.size}):")
                            status.stagedFiles.forEach { appendLine("  A  $it") }
                            appendLine()
                        }

                        if (status.modifiedFiles.isNotEmpty()) {
                            appendLine("Modified files (${status.modifiedFiles.size}):")
                            status.modifiedFiles.forEach { appendLine("  M  $it") }
                            appendLine()
                        }

                        if (status.untrackedFiles.isNotEmpty()) {
                            appendLine("Untracked files (${status.untrackedFiles.size}):")
                            status.untrackedFiles.forEach { appendLine("  ?? $it") }
                            appendLine()
                        }
                    }

                    if (status.lastCommit != null) {
                        appendLine("Last commit:")
                        appendLine("  ${status.lastCommit.shortHash} - ${status.lastCommit.message}")
                        appendLine("  ${status.lastCommit.author} - ${status.lastCommit.date}")
                    }

                    appendLine()
                    appendLine("=".repeat(60))
                }

                CommandResult.Success(output, sendToLlm = false)
            },
            onFailure = { error ->
                CommandResult.Error("Failed to get git status: ${error.message}")
            }
        )
    }

    /**
     * Handle /git diff [file] command
     */
    private fun handleDiff(workingDir: File, args: List<String>): CommandResult {
        val filePath = args.firstOrNull()
        val result = gitRepositoryService.getDiffContent(workingDir, filePath)

        return result.fold(
            onSuccess = { diffs ->
                if (diffs.isEmpty()) {
                    CommandResult.Success("No changes to show", sendToLlm = false)
                } else {
                    val output = buildString {
                        appendLine("=".repeat(60))
                        if (filePath != null) {
                            appendLine("Git Diff for: $filePath")
                        } else {
                            appendLine("Git Diff (All Changes)")
                        }
                        appendLine("=".repeat(60))
                        appendLine()

                        for (diff in diffs) {
                            appendLine("File: ${diff.file} (${diff.changeType})")
                            appendLine("Changes: +${diff.addedLines} -${diff.removedLines}")
                            appendLine()
                            appendLine(diff.diffText)
                            appendLine()
                            appendLine("-".repeat(60))
                            appendLine()
                        }
                    }

                    CommandResult.Success(output, sendToLlm = false)
                }
            },
            onFailure = { error ->
                CommandResult.Error("Failed to get git diff: ${error.message}")
            }
        )
    }

    /**
     * Handle /git log command
     */
    private fun handleLog(workingDir: File, settings: CodeAssistantSettings): CommandResult {
        val result = gitRepositoryService.getCommitHistory(workingDir, settings.gitMaxCommits)

        return result.fold(
            onSuccess = { commits ->
                if (commits.isEmpty()) {
                    CommandResult.Success("No commits found", sendToLlm = false)
                } else {
                    val output = buildString {
                        appendLine("=".repeat(60))
                        appendLine("Git Commit History")
                        appendLine("=".repeat(60))
                        appendLine()

                        commits.forEach { commit ->
                            appendLine("${commit.shortHash} - ${commit.message}")
                            appendLine("  Author: ${commit.author}")
                            appendLine("  Date: ${commit.date}")
                            if (commit.filesChanged > 0) {
                                appendLine("  Files changed: ${commit.filesChanged}")
                            }
                            appendLine()
                        }

                        appendLine("=".repeat(60))
                    }

                    CommandResult.Success(output, sendToLlm = false)
                }
            },
            onFailure = { error ->
                CommandResult.Error("Failed to get git log: ${error.message}")
            }
        )
    }

    /**
     * Handle /git branch command
     */
    private fun handleBranch(workingDir: File): CommandResult {
        val branchName = gitRepositoryService.getCurrentBranch(workingDir)
        val remoteUrl = gitRepositoryService.getRemoteUrl(workingDir)

        return if (branchName != null) {
            val output = buildString {
                appendLine("=".repeat(60))
                appendLine("Git Branch Information")
                appendLine("=".repeat(60))
                appendLine()
                appendLine("Current Branch: $branchName")
                if (remoteUrl != null) {
                    appendLine("Remote URL: $remoteUrl")
                }
                appendLine()
                appendLine("=".repeat(60))
            }

            CommandResult.Success(output, sendToLlm = false)
        } else {
            CommandResult.Error("Failed to get branch information")
        }
    }
}
