package data.codeassistant

import data.CodeAssistantSettings
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

class FileSearchService {
    private val fileListCache = mutableMapOf<String, Pair<Long, List<FileMatch>>>()
    private val cacheTTL = 5 * 60 * 1000L // 5 minutes

    /**
     * Search for files matching the given pattern in the working directory
     */
    fun searchByPattern(
        pattern: String,
        workingDir: File,
        settings: CodeAssistantSettings
    ): List<FileMatch> {
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return emptyList()
        }

        val matcher = createGlobMatcher(pattern)
        val files = mutableListOf<FileMatch>()

        workingDir.walkTopDown()
            .onEnter { dir ->
                // Check if directory should be excluded
                val relativePath = dir.relativeTo(workingDir).path
                !shouldExclude(relativePath, settings.fileExcludePatterns)
            }
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(workingDir).path.replace(File.separator, "/")

                // Check exclude patterns
                if (shouldExclude(relativePath, settings.fileExcludePatterns)) {
                    return@forEach
                }

                // Check if file matches pattern
                if (matcher.matches(Paths.get(relativePath))) {
                    // Check file size
                    val content = try {
                        file.readText()
                    } catch (e: Exception) {
                        return@forEach
                    }

                    if (content.length <= settings.maxFileSize) {
                        files.add(
                            FileMatch(
                                file = file,
                                relativePath = relativePath,
                                size = content.length.toLong(),
                                lastModified = file.lastModified(),
                                matchReason = "Pattern match: $pattern"
                            )
                        )
                    }
                }
            }

        return files.sortedByDescending { it.lastModified }
    }

    /**
     * Search for files by name (exact or fuzzy match)
     */
    fun searchByFileName(
        fileName: String,
        workingDir: File,
        settings: CodeAssistantSettings,
        fuzzy: Boolean = true
    ): List<FileMatch> {
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return emptyList()
        }

        val files = mutableListOf<FileMatch>()
        val normalizedSearchName = fileName.lowercase()

        workingDir.walkTopDown()
            .onEnter { dir ->
                val relativePath = dir.relativeTo(workingDir).path
                !shouldExclude(relativePath, settings.fileExcludePatterns)
            }
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(workingDir).path.replace(File.separator, "/")

                if (shouldExclude(relativePath, settings.fileExcludePatterns)) {
                    return@forEach
                }

                val normalizedFileName = file.name.lowercase()
                val isExactMatch = normalizedFileName == normalizedSearchName
                val isFuzzyMatch = fuzzy && normalizedFileName.contains(normalizedSearchName)

                if (isExactMatch || isFuzzyMatch) {
                    // Check file size
                    val content = try {
                        file.readText()
                    } catch (e: Exception) {
                        return@forEach
                    }

                    if (content.length <= settings.maxFileSize) {
                        files.add(
                            FileMatch(
                                file = file,
                                relativePath = relativePath,
                                size = content.length.toLong(),
                                lastModified = file.lastModified(),
                                matchReason = if (isExactMatch) "Exact filename match" else "Fuzzy filename match"
                            )
                        )
                    }
                }
            }

        // Sort exact matches first, then by last modified
        return files.sortedWith(compareBy(
            { !it.matchReason.contains("Exact") },
            { -it.lastModified }
        ))
    }

    /**
     * Get project file tree information
     */
    fun getFileTree(workingDir: File, settings: CodeAssistantSettings): ProjectInfo {
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return ProjectInfo(
                projectType = ProjectType.UNKNOWN,
                rootDirectory = workingDir,
                totalFiles = 0,
                totalSize = 0
            )
        }

        val fileStructure = mutableMapOf<String, Int>()
        var totalFiles = 0
        var totalSize = 0L

        workingDir.walkTopDown()
            .onEnter { dir ->
                val relativePath = dir.relativeTo(workingDir).path
                !shouldExclude(relativePath, settings.fileExcludePatterns)
            }
            .filter { it.isFile }
            .forEach { file ->
                totalFiles++
                totalSize += file.length()

                val extension = file.extension.ifEmpty { "(no extension)" }
                fileStructure[extension] = fileStructure.getOrDefault(extension, 0) + 1
            }

        return ProjectInfo(
            projectType = ProjectType.UNKNOWN, // Will be set by ProjectAnalysisService
            rootDirectory = workingDir,
            fileStructure = fileStructure,
            totalFiles = totalFiles,
            totalSize = totalSize
        )
    }

    /**
     * Get all files matching include patterns
     */
    fun getAllFiles(workingDir: File, settings: CodeAssistantSettings): List<FileMatch> {
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return emptyList()
        }

        // Check cache
        val cacheKey = "${workingDir.absolutePath}:${settings.hashCode()}"
        val cached = fileListCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.first < cacheTTL) {
            return cached.second
        }

        val files = mutableListOf<FileMatch>()
        val includeMatchers = settings.fileIncludePatterns.map { createGlobMatcher(it) }

        workingDir.walkTopDown()
            .onEnter { dir ->
                val relativePath = dir.relativeTo(workingDir).path
                !shouldExclude(relativePath, settings.fileExcludePatterns)
            }
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(workingDir).path.replace(File.separator, "/")

                if (shouldExclude(relativePath, settings.fileExcludePatterns)) {
                    return@forEach
                }

                // Check if file matches any include pattern
                val path = Paths.get(relativePath)
                if (includeMatchers.any { it.matches(path) }) {
                    // Check file size
                    val size = try {
                        val content = file.readText()
                        content.length.toLong()
                    } catch (e: Exception) {
                        return@forEach
                    }

                    if (size <= settings.maxFileSize) {
                        files.add(
                            FileMatch(
                                file = file,
                                relativePath = relativePath,
                                size = size,
                                lastModified = file.lastModified(),
                                matchReason = "Included by pattern"
                            )
                        )
                    }
                }
            }

        val result = files.sortedByDescending { it.lastModified }

        // Update cache
        fileListCache[cacheKey] = Pair(System.currentTimeMillis(), result)

        return result
    }

    /**
     * Clear the file list cache
     */
    fun clearCache() {
        fileListCache.clear()
    }

    private fun createGlobMatcher(pattern: String): PathMatcher {
        val normalizedPattern = pattern.replace(File.separator, "/")
        return FileSystems.getDefault().getPathMatcher("glob:$normalizedPattern")
    }

    private fun shouldExclude(path: String, excludePatterns: List<String>): Boolean {
        val normalizedPath = path.replace(File.separator, "/")
        return excludePatterns.any { pattern ->
            val normalizedPattern = pattern.replace(File.separator, "/")
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$normalizedPattern")
            matcher.matches(Paths.get(normalizedPath))
        }
    }

    /**
     * Check if a file appears to be binary (not text)
     */
    private fun isBinaryFile(file: File): Boolean {
        return try {
            val bytes = file.readBytes().take(512)
            // Simple heuristic: if more than 30% of first 512 bytes are non-printable, consider it binary
            val nonPrintable = bytes.count { it < 0x20 && it != 0x09.toByte() && it != 0x0A.toByte() && it != 0x0D.toByte() }
            nonPrintable > bytes.size * 0.3
        } catch (e: Exception) {
            true // If we can't read it, assume binary
        }
    }
}
