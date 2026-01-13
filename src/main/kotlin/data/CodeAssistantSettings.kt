package data

import kotlinx.serialization.Serializable

@Serializable
data class CodeAssistantSettings(
    val enabled: Boolean = false,
    val workingDirectory: String? = null,
    val autoContextEnabled: Boolean = true,
    val maxFilesInContext: Int = 5,
    val fileIncludePatterns: List<String> = listOf("*.kt", "*.java", "*.py", "*.js", "*.ts", "*.md"),
    val fileExcludePatterns: List<String> = listOf("**/build/**", "**/node_modules/**", "**/.git/**", "**/.idea/**"),
    val maxFileSize: Int = 100_000, // Maximum file size in characters

    // Git integration settings
    val gitEnabled: Boolean = false,
    val gitAutoDetectEnabled: Boolean = true, // Auto-detect git keywords
    val gitIncludeDiffs: Boolean = true,
    val gitIncludeHistory: Boolean = true,
    val gitMaxDiffLines: Int = 500,
    val gitMaxCommits: Int = 5,

    // Project documentation RAG settings
    val projectDocsEnabled: Boolean = true, // Default enabled
    val projectDocsLastInitialized: Long? = null,
    val projectDocsSourceFiles: List<String>? = null // Track source files for staleness detection
)
