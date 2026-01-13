package data.commands

import data.CodeAssistantSettings
import data.git.GitRepositoryService
import data.git.PullRequestContext
import java.io.File

/**
 * Handler for /review-pr command
 *
 * Reviews the current branch as a pull request by comparing it against a base branch.
 * Uses pipeline with enriched context (project docs, code context) for comprehensive review.
 */
class ReviewPrCommandHandler(
    private val gitRepositoryService: GitRepositoryService
) : CommandHandler {

    override suspend fun execute(
        command: ChatCommand,
        settings: CodeAssistantSettings
    ): CommandResult {
        if (command !is ChatCommand.ReviewPr) {
            return CommandResult.Error("Invalid command type")
        }

        // Validate prerequisites
        val workingDir = settings.workingDirectory?.let { File(it) }
            ?: return CommandResult.Error("No working directory configured. Set it in Code Assistant settings.")

        if (!settings.gitEnabled) {
            return CommandResult.Error("Git integration not enabled. Enable it in Code Assistant settings.")
        }

        if (!gitRepositoryService.isGitRepository(workingDir)) {
            return CommandResult.Error("Working directory is not a git repository")
        }

        // Get current branch
        val currentBranch = gitRepositoryService.getCurrentBranch(workingDir)
            ?: return CommandResult.Error("Failed to determine current branch")

        // Detect or use specified base branch
        val baseBranch = command.baseBranch
            ?: gitRepositoryService.detectBaseBranch(workingDir)
            ?: return CommandResult.Error(
                "Could not auto-detect base branch. Specify manually: /review-pr <base-branch>"
            )

        // Validate branches are different
        if (currentBranch == baseBranch || currentBranch == baseBranch.removePrefix("origin/")) {
            return CommandResult.Error(
                "Current branch ($currentBranch) is same as base ($baseBranch). Switch to a feature branch first."
            )
        }

        try {
            // Build PR context
            val prContext = buildPullRequestContext(workingDir, baseBranch, currentBranch)

            if (prContext.filesChanged == 0) {
                return CommandResult.Success(
                    "No changes between $baseBranch and $currentBranch",
                    sendToLlm = false
                )
            }

            // Size validation
            if (prContext.totalChanges > 10000) {
                return CommandResult.Error(
                    "PR too large (${prContext.totalChanges} line changes). Maximum: 10,000 lines. " +
                    "Consider reviewing smaller changesets or specific files."
                )
            }

            // Build review prompt with instructions
            val reviewPrompt = buildReviewPrompt(prContext)

            // Return with sendToLlm=true to trigger pipeline
            return CommandResult.Success(reviewPrompt, sendToLlm = true)

        } catch (e: Exception) {
            return CommandResult.Error("PR review failed: ${e.message}")
        }
    }

    /**
     * Build comprehensive PR context from git data
     */
    private fun buildPullRequestContext(
        workingDir: File,
        baseBranch: String,
        currentBranch: String
    ): PullRequestContext {
        // Get commits between branches
        val commitsResult = gitRepositoryService.getCommitsBetweenBranches(
            workingDir, baseBranch, currentBranch, maxCount = 50
        )
        val commits = commitsResult.getOrElse { emptyList() }

        // Get diff between branches
        val diffsResult = gitRepositoryService.getBranchDiff(
            workingDir, baseBranch, currentBranch
        )
        val diffs = diffsResult.getOrElse { emptyList() }

        return PullRequestContext(
            currentBranch = currentBranch,
            baseBranch = baseBranch,
            commits = commits,
            diffs = diffs,
            filesChanged = diffs.size,
            linesAdded = diffs.sumOf { it.addedLines },
            linesRemoved = diffs.sumOf { it.removedLines }
        )
    }

    /**
     * Build enriched review prompt with structured instructions
     */
    private fun buildReviewPrompt(prContext: PullRequestContext): String {
        return buildString {
            appendLine(PullRequestContext.format(prContext))
            appendLine()
            appendLine("=== Review Instructions ===")
            appendLine()
            appendLine("Perform a comprehensive code review with the following structure:")
            appendLine()
            appendLine("## Summary")
            appendLine("- Provide 2-3 bullet points summarizing the changes")
            appendLine("- Identify the main purpose/goal of this PR")
            appendLine()
            appendLine("## File-by-File Analysis")
            appendLine("For each changed file:")
            appendLine("- Summarize the changes")
            appendLine("- Highlight key modifications")
            appendLine("- Note any concerns or questions")
            appendLine()
            appendLine("## Issues & Concerns")
            appendLine("Identify potential problems:")
            appendLine("- Bugs or logic errors")
            appendLine("- Performance issues")
            appendLine("- Security vulnerabilities")
            appendLine("- Breaking changes")
            appendLine("- Missing error handling")
            appendLine("- Code quality issues")
            appendLine()
            appendLine("## Suggestions & Improvements")
            appendLine("Provide actionable recommendations:")
            appendLine("- Code improvements")
            appendLine("- Better patterns or approaches")
            appendLine("- Missing tests or documentation")
            appendLine("- Refactoring opportunities")
            appendLine()
            appendLine("## Architecture & Design")
            appendLine("- Assess if changes align with project architecture")
            appendLine("- Note any architectural concerns")
            appendLine("- Suggest design improvements if needed")
            appendLine()
            appendLine("## Overall Assessment")
            appendLine("- Overall code quality rating")
            appendLine("- Readiness for merge (Approve / Request Changes / Comment)")
            appendLine("- Priority action items")
            appendLine()
            appendLine("TOOLS AVAILABLE:")
            appendLine("- git_show: View specific commit details")
            appendLine("- git_log: View additional commit history")
            appendLine("- git_status: Check current working directory state")
            appendLine()
            appendLine("Focus on being constructive, specific, and actionable in your feedback.")
        }
    }
}
