package data.codeassistant

import data.CodeAssistantSettings
import java.io.File

data class SearchOptions(
    val caseSensitive: Boolean = false,
    val contextLines: Int = 3, // Lines before and after match
    val maxMatches: Int = 100
)

class ContentSearchService {

    /**
     * Search for content within files in the working directory
     */
    fun searchContent(
        query: String,
        workingDir: File,
        settings: CodeAssistantSettings,
        options: SearchOptions = SearchOptions()
    ): List<ContentMatch> {
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return emptyList()
        }

        val pattern = try {
            if (options.caseSensitive) {
                Regex(query)
            } else {
                Regex(query, RegexOption.IGNORE_CASE)
            }
        } catch (e: Exception) {
            // If pattern is invalid regex, treat as literal string
            Regex.escape(query).let {
                if (options.caseSensitive) Regex(it) else Regex(it, RegexOption.IGNORE_CASE)
            }
        }

        val matches = mutableListOf<ContentMatch>()

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

                // Check if file matches include patterns
                if (!matchesIncludePattern(relativePath, settings.fileIncludePatterns)) {
                    return@forEach
                }

                // Search within file
                val fileMatches = searchInFile(file, relativePath, pattern, options)
                matches.addAll(fileMatches)

                if (matches.size >= options.maxMatches) {
                    return matches.take(options.maxMatches)
                }
            }

        return matches.take(options.maxMatches)
    }

    /**
     * Search within a specific file
     */
    fun searchInFile(
        file: File,
        relativePath: String,
        pattern: Regex,
        options: SearchOptions = SearchOptions()
    ): List<ContentMatch> {
        val matches = mutableListOf<ContentMatch>()

        try {
            val lines = file.readLines()

            lines.forEachIndexed { index, line ->
                if (pattern.containsMatchIn(line)) {
                    val lineNumber = index + 1
                    val contextBefore = mutableListOf<String>()
                    val contextAfter = mutableListOf<String>()

                    // Get context before
                    for (i in (index - options.contextLines).coerceAtLeast(0) until index) {
                        contextBefore.add(lines[i])
                    }

                    // Get context after
                    for (i in (index + 1)..(index + options.contextLines).coerceAtMost(lines.size - 1)) {
                        contextAfter.add(lines[i])
                    }

                    matches.add(
                        ContentMatch(
                            file = file,
                            relativePath = relativePath,
                            lineNumber = lineNumber,
                            matchedLine = line,
                            contextBefore = contextBefore,
                            contextAfter = contextAfter
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Skip files that can't be read
        }

        return matches
    }

    /**
     * Search for a specific function or class definition
     */
    fun searchForDefinition(
        name: String,
        workingDir: File,
        settings: CodeAssistantSettings
    ): List<ContentMatch> {
        // Common patterns for function/class definitions across multiple languages
        val patterns = listOf(
            "class\\s+$name",           // class Name
            "interface\\s+$name",       // interface Name
            "fun\\s+$name",             // fun name (Kotlin)
            "function\\s+$name",        // function name (JavaScript/TypeScript)
            "def\\s+$name",             // def name (Python)
            "fn\\s+$name",              // fn name (Rust)
            "func\\s+$name",            // func name (Go)
            "\\b$name\\s*\\(",          // name( (generic function call)
            "data\\s+class\\s+$name",   // data class Name (Kotlin)
            "object\\s+$name"           // object Name (Kotlin)
        )

        val allMatches = mutableListOf<ContentMatch>()

        for (pattern in patterns) {
            val matches = searchContent(
                query = pattern,
                workingDir = workingDir,
                settings = settings,
                options = SearchOptions(caseSensitive = false, contextLines = 5)
            )
            allMatches.addAll(matches)
        }

        // Remove duplicates based on file and line number
        return allMatches.distinctBy { "${it.relativePath}:${it.lineNumber}" }
    }

    private fun shouldExclude(path: String, excludePatterns: List<String>): Boolean {
        val normalizedPath = path.replace(File.separator, "/")
        return excludePatterns.any { pattern ->
            val normalizedPattern = pattern.replace(File.separator, "/")
            matchesPattern(normalizedPath, normalizedPattern)
        }
    }

    private fun matchesIncludePattern(path: String, includePatterns: List<String>): Boolean {
        if (includePatterns.isEmpty()) return true

        val normalizedPath = path.replace(File.separator, "/")
        return includePatterns.any { pattern ->
            val normalizedPattern = pattern.replace(File.separator, "/")
            matchesPattern(normalizedPath, normalizedPattern)
        }
    }

    private fun matchesPattern(path: String, pattern: String): Boolean {
        // Simple glob pattern matching
        val regexPattern = pattern
            .replace(".", "\\.")
            .replace("**/", ".*")
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .replace("?", ".")

        return Regex(regexPattern).matches(path)
    }
}
