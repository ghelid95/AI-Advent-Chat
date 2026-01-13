package data.git

import data.CodeAssistantSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Service for enriching queries with git context
 */
class GitContextService(
    private val gitRepositoryService: GitRepositoryService
) {
    /**
     * Enrich a query with git repository context
     *
     * @param query The user's query
     * @param settings Code assistant settings including git configuration
     * @return The enriched query with git context prepended
     */
    suspend fun enrichQueryWithGitContext(
        query: String,
        settings: CodeAssistantSettings
    ): String {
        if (!settings.gitEnabled || settings.workingDirectory.isNullOrBlank()) {
            return query
        }

        val workingDir = File(settings.workingDirectory)
        if (!gitRepositoryService.isGitRepository(workingDir)) {
            return query
        }

        return withContext(Dispatchers.IO) {
            try {
                // Get git status
                val statusResult = gitRepositoryService.getRepositoryStatus(workingDir)
                val status = statusResult.getOrNull()

                // Get diffs if enabled
                val diffs = if (settings.gitIncludeDiffs) {
                    gitRepositoryService.getDiffContent(workingDir).getOrElse { emptyList() }
                } else {
                    emptyList()
                }

                // Get commit history if enabled
                val commits = if (settings.gitIncludeHistory) {
                    gitRepositoryService.getCommitHistory(workingDir, settings.gitMaxCommits)
                        .getOrElse { emptyList() }
                } else {
                    emptyList()
                }

                // Format context
                val gitContext = GitContext.format(
                    status = status,
                    diffs = diffs,
                    recentCommits = commits,
                    maxDiffSize = settings.gitMaxDiffLines * 80 // Approximate chars per line
                )

                // Prepend git context to query
                "${gitContext.formattedContext}\n\nOriginal Query: $query"
            } catch (e: Exception) {
                println("[Git Context] Error enriching query: ${e.message}")
                e.printStackTrace()
                query // Return original query on error
            }
        }
    }
}
