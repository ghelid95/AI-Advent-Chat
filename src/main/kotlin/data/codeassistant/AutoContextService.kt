package data.codeassistant

import data.CodeAssistantSettings
import java.io.File

class AutoContextService(
    private val fileSearchService: FileSearchService,
    private val contentSearchService: ContentSearchService
) {

    /**
     * Enrich user query with relevant code context
     */
    fun enrichQueryWithCodeContext(
        query: String,
        settings: CodeAssistantSettings
    ): String {
        if (!settings.enabled || settings.workingDirectory.isNullOrBlank()) {
            return query
        }

        if (!settings.autoContextEnabled) {
            return query
        }

        val workingDir = File(settings.workingDirectory)
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return query
        }

        // Detect code references in the query
        val references = detectCodeReferences(query)
        if (references.isEmpty()) {
            return query
        }

        // Collect file matches based on detected references
        val fileMatches = mutableListOf<FileMatch>()

        for (reference in references.sortedByDescending { it.confidence }) {
            when (reference.type) {
                ReferenceType.FILE_NAME -> {
                    val matches = fileSearchService.searchByFileName(
                        fileName = reference.value,
                        workingDir = workingDir,
                        settings = settings,
                        fuzzy = false
                    )
                    fileMatches.addAll(matches)
                }

                ReferenceType.PATH -> {
                    val pathFile = File(workingDir, reference.value)
                    if (pathFile.exists() && pathFile.isFile) {
                        val relativePath = pathFile.relativeTo(workingDir).path.replace(File.separator, "/")
                        fileMatches.add(
                            FileMatch(
                                file = pathFile,
                                relativePath = relativePath,
                                size = pathFile.length(),
                                lastModified = pathFile.lastModified(),
                                matchReason = "Path reference"
                            )
                        )
                    }
                }

                ReferenceType.CLASS_NAME, ReferenceType.FUNCTION_NAME -> {
                    val matches = contentSearchService.searchForDefinition(
                        name = reference.value,
                        workingDir = workingDir,
                        settings = settings
                    )
                    // Convert ContentMatch to FileMatch for consistency
                    for (match in matches.take(3)) {
                        if (fileMatches.none { it.file.absolutePath == match.file.absolutePath }) {
                            fileMatches.add(
                                FileMatch(
                                    file = match.file,
                                    relativePath = match.relativePath,
                                    size = match.file.length(),
                                    lastModified = match.file.lastModified(),
                                    matchReason = "Contains ${reference.type.name.lowercase().replace('_', ' ')}: ${reference.value}"
                                )
                            )
                        }
                    }
                }

                ReferenceType.GENERIC -> {
                    // For generic queries, try to find recently modified files
                    val allFiles = fileSearchService.getAllFiles(workingDir, settings)
                    val recentFiles = allFiles
                        .sortedByDescending { it.lastModified }
                        .take(3)
                    fileMatches.addAll(recentFiles.map { it.copy(matchReason = "Recently modified") })
                }

                ReferenceType.ERROR_LINE -> {
                    // Don't add extra context for error line references
                    // The user will typically provide more specific info
                }
            }

            // Stop if we have enough files
            if (fileMatches.size >= settings.maxFilesInContext) {
                break
            }
        }

        // Remove duplicates and limit to maxFilesInContext
        val uniqueFiles = fileMatches
            .distinctBy { it.file.absolutePath }
            .take(settings.maxFilesInContext)

        if (uniqueFiles.isEmpty()) {
            return query
        }

        // Format context
        val codeContext = CodeContext.format(
            files = uniqueFiles,
            contentMatches = emptyList()
        )

        // Prepend context to query
        return "${codeContext.formattedContext}\n\nOriginal Query: $query"
    }

    /**
     * Detect code references in user query
     */
    fun detectCodeReferences(query: String): List<CodeReference> {
        val references = mutableListOf<CodeReference>()
        val lowercaseQuery = query.lowercase()

        // 1. File name patterns (e.g., "in MainActivity.kt", "look at App.kt", "ChatViewModel.kt")
        val fileNamePattern = Regex("""(?:in|at|from|about|show|view|read|check|edit)\s+(\w+\.\w+)|(\w+\.(kt|java|py|js|ts|go|rs|cpp|c|h|md|txt))""", RegexOption.IGNORE_CASE)
        fileNamePattern.findAll(query).forEach { match ->
            val fileName = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (fileName.isNotBlank()) {
                references.add(CodeReference(ReferenceType.FILE_NAME, fileName, 0.9f))
            }
        }

        // 2. Path references (e.g., "src/main/kotlin/...", "data/models/...")
        val pathPattern = Regex("""[/\\]?(\w+[/\\]\w+[/\\][\w./\\]+)""")
        pathPattern.findAll(query).forEach { match ->
            val path = match.groupValues[1]
            if (path.contains('/') || path.contains('\\')) {
                references.add(CodeReference(ReferenceType.PATH, path, 0.8f))
            }
        }

        // 3. Class name patterns (e.g., "UserRepository class", "MainActivity", capitalized words)
        val classPattern = Regex("""(?:class|interface|object|data class)\s+(\w+)|(?:\s|^)([A-Z]\w+(?:[A-Z]\w+)+)(?:\s|$)""")
        classPattern.findAll(query).forEach { match ->
            val className = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (className.isNotBlank() && className.length > 2) {
                references.add(CodeReference(ReferenceType.CLASS_NAME, className, 0.7f))
            }
        }

        // 4. Function name patterns (e.g., "fetchData function", "sendMessage()")
        val functionPattern = Regex("""(?:function|method|fun|def)\s+(\w+)|(\w+)\s*\(\s*\)""")
        functionPattern.findAll(query).forEach { match ->
            val functionName = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (functionName.isNotBlank() && functionName.length > 2) {
                references.add(CodeReference(ReferenceType.FUNCTION_NAME, functionName, 0.7f))
            }
        }

        // 5. Error line references (e.g., "error in line 42", "at line 123")
        val errorLinePattern = Regex("""(?:line|at line|on line)\s+(\d+)""", RegexOption.IGNORE_CASE)
        if (errorLinePattern.containsMatchIn(query)) {
            errorLinePattern.findAll(query).forEach { match ->
                val lineNumber = match.groupValues[1]
                references.add(CodeReference(ReferenceType.ERROR_LINE, lineNumber, 0.6f))
            }
        }

        // 6. Generic implementation queries
        val genericPatterns = listOf(
            "how is" to 0.5f,
            "how does" to 0.5f,
            "show me" to 0.6f,
            "what does" to 0.5f,
            "explain" to 0.4f,
            "where is" to 0.6f,
            "find" to 0.5f,
            "search for" to 0.5f,
            "look for" to 0.5f,
            "implemented" to 0.5f,
            "implementation" to 0.5f
        )

        for ((pattern, confidence) in genericPatterns) {
            if (lowercaseQuery.contains(pattern)) {
                references.add(CodeReference(ReferenceType.GENERIC, pattern, confidence))
                break // Only add one generic reference
            }
        }

        return references.distinctBy { "${it.type}:${it.value}" }
            .sortedByDescending { it.confidence }
    }

    /**
     * Detect if query contains git-related references
     * Used to determine if git context should be added to the query
     */
    fun detectGitReferences(query: String): Boolean {
        val lowercaseQuery = query.lowercase()

        val gitKeywords = listOf(
            "branch", "commit", "diff", "changes", "modified",
            "staged", "unstaged", "git status", "git log",
            "git diff", "repository", "repo", "version control",
            "merge", "pull", "push", "checkout", "what changed",
            "recent changes", "last commit", "working directory",
            "uncommitted", "pending changes", "current branch",
            "git branch", "commits", "commit history"
        )

        return gitKeywords.any { lowercaseQuery.contains(it) }
    }
}
