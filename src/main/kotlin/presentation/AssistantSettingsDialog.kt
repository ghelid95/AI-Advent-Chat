package presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogState
import data.CodeAssistantSettings
import data.codeassistant.ProjectAnalysisService
import data.codeassistant.FileSearchService
import java.io.File
import javax.swing.JFileChooser

@Composable
fun AssistantSettingsDialog(
    settings: CodeAssistantSettings,
    onDismiss: () -> Unit,
    onSave: (CodeAssistantSettings) -> Unit
) {
    var enabled by remember { mutableStateOf(settings.enabled) }
    var workingDirectory by remember { mutableStateOf(settings.workingDirectory ?: "") }
    var autoContextEnabled by remember { mutableStateOf(settings.autoContextEnabled) }
    var maxFilesInContext by remember { mutableStateOf(settings.maxFilesInContext) }
    var includePatterns by remember { mutableStateOf(settings.fileIncludePatterns.joinToString(", ")) }
    var excludePatterns by remember { mutableStateOf(settings.fileExcludePatterns.joinToString(", ")) }
    var maxFileSize by remember { mutableStateOf(settings.maxFileSize.toString()) }

    // Git integration settings
    var gitEnabled by remember { mutableStateOf(settings.gitEnabled) }
    var gitAutoDetectEnabled by remember { mutableStateOf(settings.gitAutoDetectEnabled) }
    var gitIncludeDiffs by remember { mutableStateOf(settings.gitIncludeDiffs) }
    var gitIncludeHistory by remember { mutableStateOf(settings.gitIncludeHistory) }
    var gitMaxDiffLines by remember { mutableStateOf(settings.gitMaxDiffLines.toString()) }
    var gitMaxCommits by remember { mutableStateOf(settings.gitMaxCommits.toString()) }

    // Project documentation settings
    var projectDocsEnabled by remember { mutableStateOf(settings.projectDocsEnabled) }

    var errorMessage by remember { mutableStateOf("") }
    var projectInfo by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    Dialog(
        onCloseRequest = onDismiss,
        state = DialogState(width = 700.dp, height = 900.dp),
        title = "Code Assistant Settings"
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Configure code assistant for project navigation",
                    style = MaterialTheme.typography.headlineSmall
                )

                Divider()

                // Enable/Disable
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Enable Code Assistant",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Automatically include relevant code context in chat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it }
                        )
                    }
                }

                // Working Directory Selection
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Working Directory",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = workingDirectory,
                                onValueChange = {
                                    workingDirectory = it
                                    errorMessage = ""
                                },
                                label = { Text("Directory Path") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("Select a directory...") }
                            )

                            Button(
                                onClick = {
                                    val fileChooser = JFileChooser().apply {
                                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                        dialogTitle = "Select Working Directory"
                                        if (workingDirectory.isNotBlank()) {
                                            currentDirectory = File(workingDirectory)
                                        }
                                    }
                                    if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        workingDirectory = fileChooser.selectedFile.absolutePath
                                        errorMessage = ""
                                        projectInfo = null
                                    }
                                }
                            ) {
                                Text("Browse")
                            }
                        }

                        if (workingDirectory.isNotBlank()) {
                            Button(
                                onClick = {
                                    val dir = File(workingDirectory)
                                    if (!dir.exists() || !dir.isDirectory) {
                                        errorMessage = "Directory does not exist or is not a directory"
                                        projectInfo = null
                                    } else {
                                        isAnalyzing = true
                                        errorMessage = ""
                                        try {
                                            val fileSearchService = FileSearchService()
                                            val analysisService = ProjectAnalysisService(fileSearchService)
                                            val info = analysisService.analyzeProject(dir)
                                            projectInfo = info?.getDescription() ?: "Unable to analyze project"
                                        } catch (e: Exception) {
                                            errorMessage = "Error analyzing project: ${e.message}"
                                            projectInfo = null
                                        } finally {
                                            isAnalyzing = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isAnalyzing
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyzing...")
                                } else {
                                    Text("Analyze Project")
                                }
                            }

                            if (projectInfo != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        projectInfo!!,
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .heightIn(max = 200.dp)
                                            .verticalScroll(rememberScrollState()),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                // Auto-Context Settings
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Auto-Context",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Automatically detect and include relevant files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoContextEnabled,
                                onCheckedChange = { autoContextEnabled = it },
                                enabled = enabled
                            )
                        }

                        if (autoContextEnabled && enabled) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Max Files in Context: $maxFilesInContext",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = maxFilesInContext.toFloat(),
                                    onValueChange = { maxFilesInContext = it.toInt() },
                                    valueRange = 1f..10f,
                                    steps = 8,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // File Patterns
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "File Patterns",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = includePatterns,
                            onValueChange = { includePatterns = it },
                            label = { Text("Include Patterns (comma-separated)") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("*.kt, *.java, *.py, *.js") },
                            supportingText = { Text("Glob patterns to include") }
                        )

                        OutlinedTextField(
                            value = excludePatterns,
                            onValueChange = { excludePatterns = it },
                            label = { Text("Exclude Patterns (comma-separated)") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("**/build/**, **/node_modules/**") },
                            supportingText = { Text("Glob patterns to exclude") }
                        )

                        OutlinedTextField(
                            value = maxFileSize,
                            onValueChange = { maxFileSize = it },
                            label = { Text("Max File Size (characters)") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("100000") },
                            supportingText = { Text("Skip files larger than this") }
                        )
                    }
                }

                // Git Integration Settings
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Git Integration",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Enable repository awareness and automatic git context",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = gitEnabled,
                                onCheckedChange = { gitEnabled = it },
                                enabled = enabled
                            )
                        }

                        if (gitEnabled && enabled) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Auto-detect Keywords
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "Auto-detect Keywords",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "Automatically add git context when keywords detected",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = gitAutoDetectEnabled,
                                        onCheckedChange = { gitAutoDetectEnabled = it }
                                    )
                                }

                                Divider()

                                // Include Diffs
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "Include Diffs",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "Include uncommitted changes in context",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = gitIncludeDiffs,
                                        onCheckedChange = { gitIncludeDiffs = it }
                                    )
                                }

                                if (gitIncludeDiffs) {
                                    OutlinedTextField(
                                        value = gitMaxDiffLines,
                                        onValueChange = { gitMaxDiffLines = it },
                                        label = { Text("Max Diff Lines") },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("500") },
                                        supportingText = { Text("Maximum lines of diff to include") }
                                    )
                                }

                                Divider()

                                // Include Commit History
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "Include Commit History",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "Include recent commit history in context",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = gitIncludeHistory,
                                        onCheckedChange = { gitIncludeHistory = it }
                                    )
                                }

                                if (gitIncludeHistory) {
                                    OutlinedTextField(
                                        value = gitMaxCommits,
                                        onValueChange = { gitMaxCommits = it },
                                        label = { Text("Max Commits") },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("5") },
                                        supportingText = { Text("Maximum commits to include in history") }
                                    )
                                }

                                // Git status indicator
                                if (workingDirectory.isNotBlank()) {
                                    Divider()

                                    val gitRepoService = remember { data.git.GitRepositoryService() }
                                    val workingDir = remember(workingDirectory) { File(workingDirectory) }
                                    val isGitRepo = remember(workingDirectory) {
                                        gitRepoService.isGitRepository(workingDir)
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isGitRepo) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isGitRepo) {
                                                val branch = remember(workingDirectory) {
                                                    gitRepoService.getCurrentBranch(workingDir)
                                                }
                                                Text(
                                                    "Git repository detected - Branch: ${branch ?: "unknown"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            } else {
                                                Text(
                                                    "Not a git repository",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Project Documentation Settings
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Project Documentation (Auto RAG)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Automatically enrich queries with project docs (README, CONTRIBUTING, docs/)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = projectDocsEnabled,
                                onCheckedChange = { projectDocsEnabled = it },
                                enabled = enabled
                            )
                        }

                        // Show metadata if initialized
                        if (projectDocsEnabled && enabled && settings.projectDocsLastInitialized != null) {
                            Divider()

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val lastInit = remember(settings.projectDocsLastInitialized) {
                                    val instant = java.time.Instant.ofEpochMilli(settings.projectDocsLastInitialized!!)
                                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .withZone(java.time.ZoneId.systemDefault())
                                    formatter.format(instant)
                                }

                                Text(
                                    "Last initialized: $lastInit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (!settings.projectDocsSourceFiles.isNullOrEmpty()) {
                                    val docNames = remember(settings.projectDocsSourceFiles) {
                                        settings.projectDocsSourceFiles!!.joinToString(", ") { path ->
                                            File(path).name
                                        }
                                    }
                                    Text(
                                        "Docs: $docNames",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    "Embeddings will automatically update when documentation files change",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Error Message
                if (errorMessage.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            errorMessage,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            // Validation
                            if (enabled && workingDirectory.isBlank()) {
                                errorMessage = "Please select a working directory"
                                return@Button
                            }

                            if (enabled && workingDirectory.isNotBlank()) {
                                val dir = File(workingDirectory)
                                if (!dir.exists() || !dir.isDirectory) {
                                    errorMessage = "Selected directory does not exist or is not a directory"
                                    return@Button
                                }
                            }

                            val maxFileSizeInt = maxFileSize.toIntOrNull()
                            if (maxFileSizeInt == null || maxFileSizeInt <= 0) {
                                errorMessage = "Max file size must be a positive number"
                                return@Button
                            }

                            // Parse patterns
                            val includePatternslist = includePatterns.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .ifEmpty { listOf("*.kt", "*.java", "*.py", "*.js", "*.ts", "*.md") }

                            val excludePatternsList = excludePatterns.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .ifEmpty { listOf("**/build/**", "**/node_modules/**", "**/.git/**", "**/.idea/**") }

                            // Validate git settings
                            val gitMaxDiffLinesInt = gitMaxDiffLines.toIntOrNull()
                            if (gitEnabled && (gitMaxDiffLinesInt == null || gitMaxDiffLinesInt <= 0)) {
                                errorMessage = "Max diff lines must be a positive number"
                                return@Button
                            }

                            val gitMaxCommitsInt = gitMaxCommits.toIntOrNull()
                            if (gitEnabled && (gitMaxCommitsInt == null || gitMaxCommitsInt <= 0)) {
                                errorMessage = "Max commits must be a positive number"
                                return@Button
                            }

                            // Create updated settings
                            val updatedSettings = CodeAssistantSettings(
                                enabled = enabled,
                                workingDirectory = if (enabled) workingDirectory.ifBlank { null } else null,
                                autoContextEnabled = autoContextEnabled,
                                maxFilesInContext = maxFilesInContext,
                                fileIncludePatterns = includePatternslist,
                                fileExcludePatterns = excludePatternsList,
                                maxFileSize = maxFileSizeInt,
                                gitEnabled = gitEnabled,
                                gitAutoDetectEnabled = gitAutoDetectEnabled,
                                gitIncludeDiffs = gitIncludeDiffs,
                                gitIncludeHistory = gitIncludeHistory,
                                gitMaxDiffLines = gitMaxDiffLinesInt ?: 500,
                                gitMaxCommits = gitMaxCommitsInt ?: 5,
                                projectDocsEnabled = projectDocsEnabled,
                                projectDocsLastInitialized = settings.projectDocsLastInitialized,
                                projectDocsSourceFiles = settings.projectDocsSourceFiles
                            )

                            onSave(updatedSettings)
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
