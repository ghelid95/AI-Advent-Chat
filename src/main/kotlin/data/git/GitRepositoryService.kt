package data.git

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with git repositories via git CLI
 */
class GitRepositoryService {
    // Cache with TTL
    private val statusCache = mutableMapOf<String, Pair<Long, GitStatus>>()
    private val diffCache = mutableMapOf<String, Pair<Long, List<GitDiff>>>()
    private val historyCache = mutableMapOf<String, Pair<Long, List<GitCommit>>>()

    private val statusCacheTTL = 30 * 1000L // 30 seconds
    private val diffCacheTTL = 60 * 1000L // 1 minute
    private val historyCacheTTL = 5 * 60 * 1000L // 5 minutes

    /**
     * Check if a directory is a git repository
     */
    fun isGitRepository(workingDir: File): Boolean {
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return false
        }

        val result = executeGitCommand(workingDir, listOf("rev-parse", "--is-inside-work-tree"))
        return result.isSuccess && result.getOrNull()?.trim() == "true"
    }

    /**
     * Get the current branch name
     */
    fun getCurrentBranch(workingDir: File): String? {
        val result = executeGitCommand(workingDir, listOf("rev-parse", "--abbrev-ref", "HEAD"))
        return result.getOrNull()?.trim()
    }

    /**
     * Get repository status (cached)
     */
    fun getRepositoryStatus(workingDir: File): Result<GitStatus> {
        val cacheKey = workingDir.absolutePath
        val now = System.currentTimeMillis()

        // Check cache
        statusCache[cacheKey]?.let { (timestamp, cached) ->
            if (now - timestamp < statusCacheTTL) {
                return Result.success(cached)
            }
        }

        return try {
            // Get status
            val statusResult = executeGitCommand(workingDir, listOf("status", "--porcelain"))
            if (statusResult.isFailure) {
                return Result.failure(statusResult.exceptionOrNull() ?: Exception("Failed to get git status"))
            }

            // Get branch
            val branch = getCurrentBranch(workingDir) ?: "unknown"

            // Get remote URL
            val remoteUrl = getRemoteUrl(workingDir)

            // Get last commit
            val lastCommit = getLastCommit(workingDir)

            // Parse status
            val status = parseGitStatus(statusResult.getOrNull() ?: "", branch, remoteUrl, lastCommit)

            // Cache result
            statusCache[cacheKey] = Pair(now, status)

            Result.success(status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get diff content for all changes or specific file (cached)
     */
    fun getDiffContent(workingDir: File, filePath: String? = null): Result<List<GitDiff>> {
        val cacheKey = "${workingDir.absolutePath}:${filePath ?: "all"}"
        val now = System.currentTimeMillis()

        // Check cache
        diffCache[cacheKey]?.let { (timestamp, cached) ->
            if (now - timestamp < diffCacheTTL) {
                return Result.success(cached)
            }
        }

        return try {
            val args = if (filePath != null) {
                listOf("diff", "HEAD", "--", filePath)
            } else {
                listOf("diff", "HEAD")
            }

            val diffResult = executeGitCommand(workingDir, args, timeout = 30000)
            if (diffResult.isFailure) {
                return Result.failure(diffResult.exceptionOrNull() ?: Exception("Failed to get git diff"))
            }

            val diffs = parseGitDiff(diffResult.getOrNull() ?: "")

            // Cache result
            diffCache[cacheKey] = Pair(now, diffs)

            Result.success(diffs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get commit history (cached)
     */
    fun getCommitHistory(workingDir: File, maxCount: Int = 10): Result<List<GitCommit>> {
        val cacheKey = "${workingDir.absolutePath}:$maxCount"
        val now = System.currentTimeMillis()

        // Check cache
        historyCache[cacheKey]?.let { (timestamp, cached) ->
            if (now - timestamp < historyCacheTTL) {
                return Result.success(cached)
            }
        }

        return try {
            val args = listOf(
                "log",
                "--oneline",
                "-n", maxCount.toString(),
                "--format=%H|%h|%s|%an|%ad",
                "--date=short"
            )

            val logResult = executeGitCommand(workingDir, args, timeout = 15000)
            if (logResult.isFailure) {
                return Result.failure(logResult.exceptionOrNull() ?: Exception("Failed to get git log"))
            }

            val commits = parseGitLog(logResult.getOrNull() ?: "")

            // Cache result
            historyCache[cacheKey] = Pair(now, commits)

            Result.success(commits)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get remote URL (origin)
     */
    fun getRemoteUrl(workingDir: File): String? {
        val result = executeGitCommand(workingDir, listOf("remote", "get-url", "origin"))
        return result.getOrNull()?.trim()
    }

    /**
     * Get last commit
     */
    private fun getLastCommit(workingDir: File): GitCommit? {
        val result = executeGitCommand(workingDir, listOf(
            "log", "-1", "--format=%H|%h|%s|%an|%ad", "--date=short"
        ))
        val output = result.getOrNull() ?: return null
        val commits = parseGitLog(output)
        return commits.firstOrNull()
    }

    /**
     * Clear all caches
     */
    fun clearCache() {
        statusCache.clear()
        diffCache.clear()
        historyCache.clear()
    }

    /**
     * Execute a git command via ProcessBuilder
     */
    private fun executeGitCommand(
        workingDir: File,
        args: List<String>,
        timeout: Long = 10000
    ): Result<String> {
        return try {
            val command = mutableListOf("git")
            command.addAll(args)

            val process = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(timeout, TimeUnit.MILLISECONDS)

            if (!completed) {
                process.destroy()
                return Result.failure(Exception("Git command timed out"))
            }

            if (process.exitValue() != 0) {
                return Result.failure(Exception("Git command failed: $output"))
            }

            Result.success(output)
        } catch (e: IOException) {
            Result.failure(Exception("Git not installed or not accessible: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse git status --porcelain output
     */
    private fun parseGitStatus(
        output: String,
        branch: String,
        remoteUrl: String?,
        lastCommit: GitCommit?
    ): GitStatus {
        val modified = mutableListOf<String>()
        val untracked = mutableListOf<String>()
        val staged = mutableListOf<String>()

        output.lines().forEach { line ->
            if (line.isBlank()) return@forEach

            val status = line.substring(0, 2)
            val file = line.substring(3).trim()

            when {
                status[0] == '?' && status[1] == '?' -> untracked.add(file)
                status[0] != ' ' -> staged.add(file)
                status[1] != ' ' -> modified.add(file)
            }
        }

        return GitStatus(
            branch = branch,
            remoteUrl = remoteUrl,
            modifiedFiles = modified,
            untrackedFiles = untracked,
            stagedFiles = staged,
            lastCommit = lastCommit,
            isClean = modified.isEmpty() && untracked.isEmpty() && staged.isEmpty()
        )
    }

    /**
     * Parse git diff output
     */
    private fun parseGitDiff(output: String): List<GitDiff> {
        if (output.isBlank()) return emptyList()

        val diffs = mutableListOf<GitDiff>()
        val lines = output.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // Look for diff --git header
            if (line.startsWith("diff --git")) {
                val parts = line.split(" ")
                val file = if (parts.size >= 4) {
                    parts[3].removePrefix("b/")
                } else {
                    "unknown"
                }

                // Determine change type
                var changeType = ChangeType.MODIFIED
                var addedLines = 0
                var removedLines = 0
                val diffLines = mutableListOf<String>()

                // Read until next diff or end
                i++
                while (i < lines.size && !lines[i].startsWith("diff --git")) {
                    val diffLine = lines[i]
                    diffLines.add(diffLine)

                    when {
                        diffLine.startsWith("new file") -> changeType = ChangeType.ADDED
                        diffLine.startsWith("deleted file") -> changeType = ChangeType.DELETED
                        diffLine.startsWith("rename") -> changeType = ChangeType.RENAMED
                        diffLine.startsWith("+") && !diffLine.startsWith("+++") -> addedLines++
                        diffLine.startsWith("-") && !diffLine.startsWith("---") -> removedLines++
                    }
                    i++
                }

                diffs.add(GitDiff(
                    file = file,
                    changeType = changeType,
                    diffText = diffLines.joinToString("\n"),
                    addedLines = addedLines,
                    removedLines = removedLines
                ))

                continue
            }

            i++
        }

        return diffs
    }

    /**
     * Parse git log output
     */
    private fun parseGitLog(output: String): List<GitCommit> {
        if (output.isBlank()) return emptyList()

        return output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 5) {
                    GitCommit(
                        hash = parts[0].trim(),
                        shortHash = parts[1].trim(),
                        message = parts[2].trim(),
                        author = parts[3].trim(),
                        date = parts[4].trim()
                    )
                } else {
                    null
                }
            }
    }

    /**
     * Detect base branch by trying common patterns
     * Order: main, master, develop, trunk (local then remote)
     */
    fun detectBaseBranch(workingDir: File): String? {
        val candidates = listOf("main", "master", "develop", "trunk")

        for (candidate in candidates) {
            // Try local branch
            val localCheck = executeGitCommand(
                workingDir,
                listOf("rev-parse", "--verify", candidate)
            )
            if (localCheck.isSuccess) return candidate

            // Try remote branch
            val remoteCheck = executeGitCommand(
                workingDir,
                listOf("rev-parse", "--verify", "origin/$candidate")
            )
            if (remoteCheck.isSuccess) return "origin/$candidate"
        }

        return null
    }

    /**
     * Get diff between two branches using three-dot syntax
     * Uses git diff base...current for merge-base comparison
     */
    fun getBranchDiff(
        workingDir: File,
        baseBranch: String,
        currentBranch: String = "HEAD"
    ): Result<List<GitDiff>> {
        // Cache key includes both branches
        val cacheKey = "${workingDir.absolutePath}:diff:$baseBranch...$currentBranch"
        val now = System.currentTimeMillis()

        // Check cache (30 second TTL)
        diffCache[cacheKey]?.let { (timestamp, cached) ->
            if (now - timestamp < 30000L) {
                return Result.success(cached)
            }
        }

        return try {
            val args = listOf("diff", "$baseBranch...$currentBranch")
            val diffResult = executeGitCommand(workingDir, args, timeout = 60000)

            if (diffResult.isFailure) {
                return Result.failure(
                    diffResult.exceptionOrNull() ?: Exception("Failed to get branch diff")
                )
            }

            val diffs = parseGitDiff(diffResult.getOrNull() ?: "")
            diffCache[cacheKey] = Pair(now, diffs)

            Result.success(diffs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get commits between branches (base..current)
     */
    fun getCommitsBetweenBranches(
        workingDir: File,
        baseBranch: String,
        currentBranch: String = "HEAD",
        maxCount: Int = 20
    ): Result<List<GitCommit>> {
        return try {
            val args = listOf(
                "log",
                "$baseBranch..$currentBranch",
                "-n", maxCount.toString(),
                "--format=%H|%h|%s|%an|%ad",
                "--date=short"
            )

            val logResult = executeGitCommand(workingDir, args, timeout = 15000)
            if (logResult.isFailure) {
                return Result.failure(
                    logResult.exceptionOrNull() ?: Exception("Failed to get commits")
                )
            }

            val commits = parseGitLog(logResult.getOrNull() ?: "")
            Result.success(commits)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
