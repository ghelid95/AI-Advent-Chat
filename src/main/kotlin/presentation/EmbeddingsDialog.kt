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
import data.*
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun EmbeddingsDialog(
    onDismiss: () -> Unit
) {
    var selectedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var embeddingName by remember { mutableStateOf("") }
    var chunkSize by remember { mutableStateOf("500") }
    var overlap by remember { mutableStateOf("50") }
    var selectedStrategy by remember { mutableStateOf(FileChunking.ChunkStrategy.FIXED_SIZE) }
    var ollamaUrl by remember { mutableStateOf("http://localhost:11434") }
    var modelName by remember { mutableStateOf("nomic-embed-text") }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    Dialog(
        onCloseRequest = onDismiss,
        state = DialogState(width = 700.dp, height = 650.dp),
        title = "Generate Embeddings"
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
                    "Generate text embeddings using Ollama",
                    style = MaterialTheme.typography.headlineSmall
                )

                Divider()

                // File Selection
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("File Selection", style = MaterialTheme.typography.titleMedium)

                            if (selectedFiles.isNotEmpty()) {
                                Text(
                                    "${selectedFiles.size} file(s) selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val fileChooser = JFileChooser().apply {
                                        fileSelectionMode = JFileChooser.FILES_ONLY
                                        isMultiSelectionEnabled = true
                                        dialogTitle = "Select Text Files"
                                        fileFilter = FileNameExtensionFilter(
                                            "Text Files (*.txt, *.md, *.kt, *.java, *.py, *.js, *.json)",
                                            "txt", "md", "kt", "java", "py", "js", "json"
                                        )
                                    }
                                    if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        selectedFiles = fileChooser.selectedFiles.toList()
                                        errorMessage = ""
                                        successMessage = ""
                                        // Auto-generate embedding name if empty
                                        if (embeddingName.isEmpty() && selectedFiles.isNotEmpty()) {
                                            embeddingName = if (selectedFiles.size == 1) {
                                                selectedFiles[0].nameWithoutExtension
                                            } else {
                                                "combined_${selectedFiles.size}_files"
                                            }
                                        }
                                    }
                                },
                                enabled = !isProcessing
                            ) {
                                Text("Browse Files")
                            }

                            if (selectedFiles.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        selectedFiles = emptyList()
                                        errorMessage = ""
                                        successMessage = ""
                                    },
                                    enabled = !isProcessing
                                ) {
                                    Text("Clear")
                                }
                            }
                        }

                        if (selectedFiles.isNotEmpty()) {
                            OutlinedTextField(
                                value = embeddingName,
                                onValueChange = { embeddingName = it },
                                label = { Text("Embedding Name") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isProcessing,
                                singleLine = true,
                                placeholder = { Text("Enter a name for this embedding") }
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 120.dp)
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    selectedFiles.forEach { file ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${file.length() / 1024} KB",
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

                // Chunking Configuration
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Chunking Configuration", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = chunkSize,
                            onValueChange = { chunkSize = it },
                            label = { Text("Chunk Size (characters)") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing,
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = overlap,
                            onValueChange = { overlap = it },
                            label = { Text("Overlap (characters)") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing && selectedStrategy == FileChunking.ChunkStrategy.FIXED_SIZE,
                            singleLine = true
                        )

                        Text("Chunking Strategy:", style = MaterialTheme.typography.bodyMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FileChunking.ChunkStrategy.values().forEach { strategy ->
                                FilterChip(
                                    selected = selectedStrategy == strategy,
                                    onClick = { selectedStrategy = strategy },
                                    label = {
                                        Text(
                                            when (strategy) {
                                                FileChunking.ChunkStrategy.FIXED_SIZE -> "Fixed Size"
                                                FileChunking.ChunkStrategy.PARAGRAPH -> "Paragraph"
                                                FileChunking.ChunkStrategy.SENTENCE -> "Sentence"
                                            }
                                        )
                                    },
                                    enabled = !isProcessing
                                )
                            }
                        }
                    }
                }

                // Ollama Configuration
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Ollama Configuration", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = ollamaUrl,
                            onValueChange = { ollamaUrl = it },
                            label = { Text("Ollama URL") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing,
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("Model Name") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing,
                            singleLine = true
                        )
                    }
                }

                // Progress and Messages
                if (progress.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(progress, style = MaterialTheme.typography.bodyMedium)
                            if (isProcessing) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

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

                if (successMessage.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            successMessage,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
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
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isProcessing
                    ) {
                        Text("Close")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                if (selectedFiles.isEmpty()) {
                                    errorMessage = "Please select at least one file"
                                    return@launch
                                }

                                if (embeddingName.isBlank()) {
                                    errorMessage = "Please provide an embedding name"
                                    return@launch
                                }

                                val chunkSizeInt = chunkSize.toIntOrNull()
                                if (chunkSizeInt == null || chunkSizeInt <= 0) {
                                    errorMessage = "Chunk size must be a positive number"
                                    return@launch
                                }

                                val overlapInt = overlap.toIntOrNull() ?: 0
                                if (overlapInt < 0) {
                                    errorMessage = "Overlap must be non-negative"
                                    return@launch
                                }

                                isProcessing = true
                                errorMessage = ""
                                successMessage = ""

                                try {
                                    // Combine all file contents
                                    progress = "Reading and combining ${selectedFiles.size} file(s)..."
                                    val combinedContent = buildString {
                                        selectedFiles.forEachIndexed { index, file ->
                                            if (index > 0) {
                                                appendLine("\n\n")  // Add separator between files
                                            }
                                            appendLine("=== ${file.name} ===")
                                            appendLine(file.readText())
                                        }
                                    }

                                    progress = "Chunking combined content..."
                                    val chunks = FileChunking.chunkText(
                                        text = combinedContent,
                                        chunkSize = chunkSizeInt,
                                        overlap = overlapInt
                                    )

                                    progress = "Generated ${chunks.size} chunks. Creating embeddings..."

                                    val ollamaClient = OllamaClient(ollamaUrl)
                                    val embeddings = mutableListOf<List<Float>>()

                                    chunks.forEachIndexed { index, chunk ->
                                        progress = "Processing chunk ${index + 1}/${chunks.size}..."
                                        val embedding = ollamaClient.generateEmbedding(chunk.text, modelName)
                                        embeddings.add(embedding)
                                    }

                                    progress = "Saving embeddings..."
                                    val documentEmbeddings = EmbeddingStorage.createDocumentEmbeddings(
                                        fileName = embeddingName,
                                        filePath = selectedFiles.joinToString(", ") { it.name },
                                        model = modelName,
                                        chunkSize = chunkSizeInt,
                                        overlap = overlapInt,
                                        strategy = selectedStrategy,
                                        chunks = chunks,
                                        embeddings = embeddings
                                    )

                                    val outputFile = EmbeddingStorage.saveEmbeddings(documentEmbeddings, embeddingName)

                                    ollamaClient.close()

                                    progress = ""
                                    successMessage = buildString {
                                        appendLine("Successfully generated embeddings!")
                                        appendLine("Embedding name: $embeddingName")
                                        appendLine("Combined ${selectedFiles.size} file(s)")
                                        appendLine("Total chunks: ${chunks.size}")
                                        appendLine("Saved to: ${outputFile.absolutePath}")
                                        appendLine()
                                        appendLine("Source files:")
                                        selectedFiles.forEach { file ->
                                            appendLine("  - ${file.name}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    progress = ""
                                    errorMessage = "Error: ${e.message}"
                                    e.printStackTrace()
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = !isProcessing && selectedFiles.isNotEmpty()
                    ) {
                        Text("Generate Embeddings")
                    }
                }
            }
        }
    }
}