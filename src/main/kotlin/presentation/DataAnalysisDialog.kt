package presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import data.*
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

enum class DataAnalysisTab {
    LOAD,
    EXPLORE,
    ANALYZE,
    HISTORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAnalysisDialog(
    onDismiss: () -> Unit,
    currentModel: String = "llama2"
) {
    val ollamaClient = remember { OllamaLlmClient() }
    val analysisService = remember { DataAnalysisService(ollamaClient) }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(DataAnalysisTab.LOAD) }
    var currentDataset by remember { mutableStateOf<Dataset?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Analysis state
    var question by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResults by remember { mutableStateOf<List<AnalysisResult>>(emptyList()) }
    var suggestedQuestions by remember { mutableStateOf<List<String>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📊 Local Data Analysis Lab")
                Icon(
                    Icons.Default.QueryStats,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tabs
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tab(
                        selected = selectedTab == DataAnalysisTab.LOAD,
                        onClick = { selectedTab = DataAnalysisTab.LOAD },
                        text = { Text("Load Data") }
                    )
                    Tab(
                        selected = selectedTab == DataAnalysisTab.EXPLORE,
                        onClick = { selectedTab = DataAnalysisTab.EXPLORE },
                        text = { Text("Explore") },
                        enabled = currentDataset != null
                    )
                    Tab(
                        selected = selectedTab == DataAnalysisTab.ANALYZE,
                        onClick = { selectedTab = DataAnalysisTab.ANALYZE },
                        text = { Text("Analyze") },
                        enabled = currentDataset != null
                    )
                    Tab(
                        selected = selectedTab == DataAnalysisTab.HISTORY,
                        onClick = { selectedTab = DataAnalysisTab.HISTORY },
                        text = { Text("History") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error message
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(error, color = Color.White, modifier = Modifier.weight(1f))
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Tab content
                when (selectedTab) {
                    DataAnalysisTab.LOAD -> {
                        LoadDataTab(
                            isLoading = isLoading,
                            currentDataset = currentDataset,
                            onLoadFile = {
                                // Call JFileChooser synchronously first
                                val fileChooser = JFileChooser()
                                fileChooser.fileFilter = FileNameExtensionFilter(
                                    "Data files (*.csv, *.json, *.log, *.txt)",
                                    "csv", "json", "log", "txt"
                                )

                                if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    val file = fileChooser.selectedFile
                                    // Now process the file in a coroutine
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            analysisService.loadDataset(file).fold(
                                                onSuccess = { dataset ->
                                                    currentDataset = dataset
                                                    suggestedQuestions = analysisService.getSuggestedQuestions()
                                                    selectedTab = DataAnalysisTab.EXPLORE
                                                },
                                                onFailure = { error ->
                                                    errorMessage = "Failed to load file: ${error.message}"
                                                }
                                            )
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                    DataAnalysisTab.EXPLORE -> {
                        currentDataset?.let { dataset ->
                            ExploreDataTab(dataset = dataset)
                        }
                    }
                    DataAnalysisTab.ANALYZE -> {
                        AnalyzeDataTab(
                            question = question,
                            onQuestionChanged = { question = it },
                            suggestedQuestions = suggestedQuestions,
                            onSuggestedQuestionClick = { question = it },
                            isAnalyzing = isAnalyzing,
                            analysisResults = analysisResults,
                            onAnalyze = {
                                scope.launch {
                                    isAnalyzing = true
                                    errorMessage = null
                                    try {
                                        analysisService.analyzeQuestion(question, currentModel).fold(
                                            onSuccess = { result ->
                                                analysisResults = listOf(result) + analysisResults
                                                question = ""
                                            },
                                            onFailure = { error ->
                                                errorMessage = "Analysis failed: ${error.message}"
                                            }
                                        )
                                    } finally {
                                        isAnalyzing = false
                                    }
                                }
                            },
                            onGetSummary = {
                                scope.launch {
                                    isAnalyzing = true
                                    errorMessage = null
                                    try {
                                        analysisService.getSummary(currentModel).fold(
                                            onSuccess = { result ->
                                                analysisResults = listOf(result) + analysisResults
                                            },
                                            onFailure = { error ->
                                                errorMessage = "Summary failed: ${error.message}"
                                            }
                                        )
                                    } finally {
                                        isAnalyzing = false
                                    }
                                }
                            },
                            onAnalyzeErrors = {
                                scope.launch {
                                    isAnalyzing = true
                                    errorMessage = null
                                    try {
                                        analysisService.analyzeErrors(currentModel).fold(
                                            onSuccess = { result ->
                                                analysisResults = listOf(result) + analysisResults
                                            },
                                            onFailure = { error ->
                                                errorMessage = "Error analysis failed: ${error.message}"
                                            }
                                        )
                                    } finally {
                                        isAnalyzing = false
                                    }
                                }
                            },
                            datasetFormat = currentDataset?.format
                        )
                    }
                    DataAnalysisTab.HISTORY -> {
                        HistoryTab(
                            history = analysisService.getHistory(),
                            onClear = {
                                analysisService.clearHistory()
                                analysisResults = emptyList()
                            },
                            onExport = {
                                try {
                                    // Call JFileChooser synchronously
                                    val fileChooser = JFileChooser()
                                    fileChooser.selectedFile = File("analysis_report.md")
                                    if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        scope.launch {
                                            try {
                                                analysisService.exportResults(fileChooser.selectedFile)
                                            } catch (e: Exception) {
                                                errorMessage = "Export failed: ${e.message}"
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Export failed: ${e.message}"
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun LoadDataTab(
    isLoading: Boolean,
    currentDataset: Dataset?,
    onLoadFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.UploadFile,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Local Data Analysis",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Analyze CSV, JSON, or LOG files completely offline",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLoadFile,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(0.5f).height(48.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading...")
            } else {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select File")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Supported Formats:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                FormatInfoRow("📊 CSV", "Comma-separated values")
                FormatInfoRow("📋 JSON", "Array of objects or single object")
                FormatInfoRow("📝 LOG", "Application logs with timestamps")
            }
        }

        if (currentDataset != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Dataset Loaded: ${currentDataset.name}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${currentDataset.rowCount} rows, ${currentDataset.columns.size} columns",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FormatInfoRow(title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(100.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun ExploreDataTab(dataset: Dataset) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Dataset info
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dataset: ${dataset.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Format", dataset.format.name)
                    InfoRow("Rows", dataset.rowCount.toString())
                    InfoRow("Columns", dataset.columns.size.toString())
                    InfoRow("Loaded", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(dataset.loadedAt))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Summary
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Summary Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        dataset.summary.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color.LightGray
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Sample data
        item {
            Text("Sample Data (first 10 rows):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(dataset.rows.take(10)) { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    row.values.forEach { (key, value) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "$key:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(120.dp)
                            )
                            Text(
                                value.take(100) + if (value.length > 100) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AnalyzeDataTab(
    question: String,
    onQuestionChanged: (String) -> Unit,
    suggestedQuestions: List<String>,
    onSuggestedQuestionClick: (String) -> Unit,
    isAnalyzing: Boolean,
    analysisResults: List<AnalysisResult>,
    onAnalyze: () -> Unit,
    onGetSummary: () -> Unit,
    onAnalyzeErrors: () -> Unit,
    datasetFormat: DataFormat?
) {
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        // Quick actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onGetSummary,
                enabled = !isAnalyzing,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Summary", style = MaterialTheme.typography.labelSmall)
            }

            if (datasetFormat == DataFormat.LOG) {
                Button(
                    onClick = onAnalyzeErrors,
                    enabled = !isAnalyzing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Errors", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Question input
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChanged,
            label = { Text("Ask a question about your data") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            trailingIcon = {
                IconButton(
                    onClick = onAnalyze,
                    enabled = question.isNotBlank() && !isAnalyzing
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Analyze",
                        tint = if (question.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        )

        // Suggested questions
        if (suggestedQuestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Suggested questions:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            suggestedQuestions.forEach { suggested ->
                SuggestionChip(
                    onClick = { onSuggestedQuestionClick(suggested) },
                    label = { Text(suggested, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Results
        if (isAnalyzing) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Analyzing data...", color = Color.Gray)
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(analysisResults) { result ->
                AnalysisResultCard(result)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun AnalysisResultCard(result: AnalysisResult) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    result.query.question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Time: ${result.processingTimeMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        result.answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    history: List<AnalysisResult>,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onExport,
                enabled = history.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export Report")
            }
            Button(
                onClick = onClear,
                enabled = history.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear History")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No analysis history yet", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(history) { result ->
                    AnalysisResultCard(result)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
