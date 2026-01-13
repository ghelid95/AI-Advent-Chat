package data.codeassistant

import kotlinx.serialization.Serializable
import java.io.File

/**
 * Represents a file found by pattern search
 */
data class FileMatch(
    val file: File,
    val relativePath: String,
    val size: Long,
    val lastModified: Long,
    val matchReason: String // e.g., "Exact filename match", "Pattern match", "Related file"
)

/**
 * Represents a code snippet found by content search
 */
data class ContentMatch(
    val file: File,
    val relativePath: String,
    val lineNumber: Int,
    val matchedLine: String,
    val contextBefore: List<String> = emptyList(),
    val contextAfter: List<String> = emptyList()
) {
    fun getFullContext(): String {
        val allLines = mutableListOf<String>()
        val startLine = lineNumber - contextBefore.size

        contextBefore.forEachIndexed { index, line ->
            allLines.add("${startLine + index}: $line")
        }
        allLines.add("$lineNumber: $matchedLine")
        contextAfter.forEachIndexed { index, line ->
            allLines.add("${lineNumber + index + 1}: $line")
        }

        return allLines.joinToString("\n")
    }
}

/**
 * Types of projects that can be detected
 */
enum class ProjectType {
    GRADLE_KOTLIN,
    GRADLE_GROOVY,
    MAVEN,
    NPM,
    PYTHON,
    RUST,
    GO,
    UNKNOWN
}

/**
 * Project metadata and structure information
 */
data class ProjectInfo(
    val projectType: ProjectType,
    val rootDirectory: File,
    val readmeContent: String? = null,
    val metadata: Map<String, String> = emptyMap(), // e.g., project name, version, dependencies
    val fileStructure: Map<String, Int> = emptyMap(), // file extension -> count
    val totalFiles: Int = 0,
    val totalSize: Long = 0
) {
    fun getDescription(): String {
        val lines = mutableListOf<String>()
        lines.add("Project Type: $projectType")
        lines.add("Root: ${rootDirectory.absolutePath}")
        lines.add("Total Files: $totalFiles")

        if (metadata.isNotEmpty()) {
            lines.add("\nMetadata:")
            metadata.forEach { (key, value) ->
                lines.add("  $key: $value")
            }
        }

        if (fileStructure.isNotEmpty()) {
            lines.add("\nFile Structure:")
            fileStructure.entries.sortedByDescending { it.value }.take(10).forEach { (ext, count) ->
                lines.add("  $ext: $count files")
            }
        }

        if (!readmeContent.isNullOrBlank()) {
            lines.add("\nREADME:")
            lines.add(readmeContent.take(500)) // Limit README length
            if (readmeContent.length > 500) {
                lines.add("... (truncated)")
            }
        }

        return lines.joinToString("\n")
    }
}

/**
 * Reference to code detected in user query
 */
data class CodeReference(
    val type: ReferenceType,
    val value: String,
    val confidence: Float // 0.0 to 1.0
)

enum class ReferenceType {
    FILE_NAME,      // "MainActivity.kt"
    PATH,           // "src/main/kotlin/..."
    CLASS_NAME,     // "UserRepository"
    FUNCTION_NAME,  // "fetchData"
    ERROR_LINE,     // "error in line 42"
    GENERIC         // "show me", "how is implemented"
}

/**
 * Formatted code context to be added to LLM query
 */
data class CodeContext(
    val files: List<FileMatch>,
    val contentMatches: List<ContentMatch>,
    val projectInfo: ProjectInfo? = null,
    val formattedContext: String
) {
    companion object {
        fun format(
            files: List<FileMatch>,
            contentMatches: List<ContentMatch> = emptyList(),
            projectInfo: ProjectInfo? = null,
            maxTotalSize: Int = 10_000
        ): CodeContext {
            val lines = mutableListOf<String>()
            lines.add("=== Code Context ===")
            lines.add("")

            var currentSize = 0

            // Add file matches
            for (fileMatch in files) {
                if (currentSize >= maxTotalSize) break

                try {
                    val content = fileMatch.file.readText()
                    val truncatedContent = if (content.length + currentSize > maxTotalSize) {
                        content.take(maxTotalSize - currentSize)
                    } else {
                        content
                    }

                    lines.add("File: ${fileMatch.relativePath}")
                    lines.add("Relevance: ${fileMatch.matchReason}")
                    lines.add("")
                    lines.add(truncatedContent)
                    lines.add("")
                    lines.add("---")
                    lines.add("")

                    currentSize += truncatedContent.length
                } catch (e: Exception) {
                    // Skip files that can't be read
                    continue
                }
            }

            // Add content matches
            for (contentMatch in contentMatches) {
                if (currentSize >= maxTotalSize) break

                val contextStr = contentMatch.getFullContext()
                lines.add("File: ${contentMatch.relativePath}")
                lines.add("Match at line ${contentMatch.lineNumber}:")
                lines.add("")
                lines.add(contextStr)
                lines.add("")
                lines.add("---")
                lines.add("")

                currentSize += contextStr.length
            }

            lines.add("=== End Code Context ===")
            lines.add("")
            lines.add("IMPORTANT: Use the code context above to answer the user's question. The context includes relevant code files from the working directory. Reference specific files and line numbers in your response.")
            lines.add("")

            val formattedContext = lines.joinToString("\n")
            return CodeContext(files, contentMatches, projectInfo, formattedContext)
        }
    }
}
