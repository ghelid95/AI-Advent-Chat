package data.git

/**
 * Git repository status information
 */
data class GitStatus(
    val branch: String,
    val remoteUrl: String?,
    val modifiedFiles: List<String>,
    val untrackedFiles: List<String>,
    val stagedFiles: List<String>,
    val lastCommit: GitCommit?,
    val isClean: Boolean
)

/**
 * Git diff information for a single file
 */
data class GitDiff(
    val file: String,
    val changeType: ChangeType,
    val diffText: String,
    val addedLines: Int,
    val removedLines: Int
)

/**
 * Type of change in a git diff
 */
enum class ChangeType {
    MODIFIED, ADDED, DELETED, RENAMED, COPIED
}

/**
 * Git commit information
 */
data class GitCommit(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val date: String,
    val filesChanged: Int = 0
)

/**
 * Formatted git context for LLM
 */
data class GitContext(
    val status: GitStatus?,
    val diffs: List<GitDiff>,
    val recentCommits: List<GitCommit>,
    val formattedContext: String
) {
    companion object {
        /**
         * Format git information into LLM-readable context
         *
         * @param status Repository status information
         * @param diffs List of file diffs
         * @param recentCommits Recent commit history
         * @param maxDiffSize Maximum size of diff content in characters
         * @return Formatted GitContext
         */
        fun format(
            status: GitStatus?,
            diffs: List<GitDiff>,
            recentCommits: List<GitCommit>,
            maxDiffSize: Int = 5000
        ): GitContext {
            val lines = mutableListOf<String>()
            lines.add("=== Git Repository Context ===")
            lines.add("")

            // Status section
            if (status != null) {
                lines.add("Branch: ${status.branch}")
                if (status.remoteUrl != null) {
                    lines.add("Remote: ${status.remoteUrl}")
                }
                lines.add("Status: ${if (status.isClean) "Clean" else "Has uncommitted changes"}")

                if (status.modifiedFiles.isNotEmpty()) {
                    lines.add("")
                    lines.add("Modified Files (${status.modifiedFiles.size}):")
                    status.modifiedFiles.take(10).forEach { lines.add("  M  $it") }
                    if (status.modifiedFiles.size > 10) {
                        lines.add("  ... and ${status.modifiedFiles.size - 10} more")
                    }
                }

                if (status.untrackedFiles.isNotEmpty()) {
                    lines.add("")
                    lines.add("Untracked Files (${status.untrackedFiles.size}):")
                    status.untrackedFiles.take(10).forEach { lines.add("  ?? $it") }
                    if (status.untrackedFiles.size > 10) {
                        lines.add("  ... and ${status.untrackedFiles.size - 10} more")
                    }
                }

                if (status.stagedFiles.isNotEmpty()) {
                    lines.add("")
                    lines.add("Staged Files (${status.stagedFiles.size}):")
                    status.stagedFiles.take(10).forEach { lines.add("  A  $it") }
                    if (status.stagedFiles.size > 10) {
                        lines.add("  ... and ${status.stagedFiles.size - 10} more")
                    }
                }
            }

            // Diffs section (truncated to maxDiffSize)
            if (diffs.isNotEmpty()) {
                lines.add("")
                lines.add("=== Uncommitted Changes ===")
                var currentSize = 0
                for (diff in diffs) {
                    if (currentSize >= maxDiffSize) {
                        lines.add("... (diff truncated, use /git diff for full output)")
                        break
                    }
                    lines.add("")
                    lines.add("File: ${diff.file} (${diff.changeType})")
                    lines.add("+${diff.addedLines} -${diff.removedLines}")
                    lines.add("")
                    val truncated = if (diff.diffText.length + currentSize > maxDiffSize) {
                        diff.diffText.take(maxDiffSize - currentSize) + "\n... (truncated)"
                    } else {
                        diff.diffText
                    }
                    lines.add(truncated)
                    currentSize += truncated.length
                }
            }

            // Commit history section
            if (recentCommits.isNotEmpty()) {
                lines.add("")
                lines.add("=== Recent Commits ===")
                recentCommits.forEach { commit ->
                    lines.add("${commit.shortHash} - ${commit.message}")
                    lines.add("  ${commit.author} - ${commit.date}")
                }
            }

            lines.add("")
            lines.add("=== End Git Context ===")
            lines.add("")
            lines.add("IMPORTANT: Use the git context above to answer questions about repository state, recent changes, and commit history.")

            val formattedContext = lines.joinToString("\n")
            return GitContext(status, diffs, recentCommits, formattedContext)
        }
    }
}
