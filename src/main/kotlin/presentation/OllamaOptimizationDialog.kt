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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import data.*
import kotlinx.coroutines.launch

enum class OllamaTab {
    PRESETS,
    CUSTOM,
    TEMPLATES,
    COMPARISON
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OllamaOptimizationDialog(
    onDismiss: () -> Unit,
    currentModel: String = "llama2"
) {
    val optimizer = remember { OllamaOptimizer() }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(OllamaTab.PRESETS) }
    var selectedPreset by remember { mutableStateOf(OllamaPresets.BALANCED) }
    var customConfig by remember { mutableStateOf(OllamaPresets.BALANCED) }
    var settings by remember { mutableStateOf(optimizer.loadSettings()) }

    // Comparison state
    var isRunningComparison by remember { mutableStateOf(false) }
    var comparisonResults by remember { mutableStateOf<List<ComparisonResult>>(emptyList()) }
    var comparisonPrompt by remember { mutableStateOf("Explain the concept of recursion in programming.") }
    var selectedConfigsForComparison by remember { mutableStateOf(setOf<String>()) }
    var comparisonProgress by remember { mutableStateOf(0 to 0) }

    // Template state
    var selectedTemplate by remember { mutableStateOf<PromptTemplate?>(null) }
    var templateParameters by remember { mutableStateOf(mapOf<String, String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ollama Optimization Lab")
                Row {
                    Icon(
                        Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tabs
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tab(
                        selected = selectedTab == OllamaTab.PRESETS,
                        onClick = { selectedTab = OllamaTab.PRESETS },
                        text = { Text("Presets") }
                    )
                    Tab(
                        selected = selectedTab == OllamaTab.CUSTOM,
                        onClick = { selectedTab = OllamaTab.CUSTOM },
                        text = { Text("Custom") }
                    )
                    Tab(
                        selected = selectedTab == OllamaTab.TEMPLATES,
                        onClick = { selectedTab = OllamaTab.TEMPLATES },
                        text = { Text("Templates") }
                    )
                    Tab(
                        selected = selectedTab == OllamaTab.COMPARISON,
                        onClick = { selectedTab = OllamaTab.COMPARISON },
                        text = { Text("Comparison") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Content
                when (selectedTab) {
                    OllamaTab.PRESETS -> {
                        PresetsTab(
                            presets = OllamaPresets.getAllPresets(),
                            selectedPreset = selectedPreset,
                            onPresetSelected = { selectedPreset = it },
                            onSavePreset = {
                                settings = settings.copy(selectedConfig = selectedPreset)
                                optimizer.saveSettings(settings)
                            }
                        )
                    }
                    OllamaTab.CUSTOM -> {
                        CustomConfigTab(
                            config = customConfig,
                            onConfigChanged = { customConfig = it },
                            onSave = {
                                val updatedSettings = settings.copy(
                                    selectedConfig = customConfig,
                                    customConfigs = settings.customConfigs + customConfig
                                )
                                optimizer.saveSettings(updatedSettings)
                                settings = updatedSettings
                            }
                        )
                    }
                    OllamaTab.TEMPLATES -> {
                        TemplatesTab(
                            templates = PromptTemplates.getAllTemplates(),
                            selectedTemplate = selectedTemplate,
                            onTemplateSelected = { selectedTemplate = it },
                            templateParameters = templateParameters,
                            onParametersChanged = { templateParameters = it },
                            onUseTemplate = { template, params ->
                                val filled = PromptTemplates.fillTemplate(template, params)
                                comparisonPrompt = filled
                                selectedTab = OllamaTab.COMPARISON
                            }
                        )
                    }
                    OllamaTab.COMPARISON -> {
                        ComparisonTab(
                            prompt = comparisonPrompt,
                            onPromptChanged = { comparisonPrompt = it },
                            selectedConfigs = selectedConfigsForComparison,
                            onConfigSelectionChanged = { selectedConfigsForComparison = it },
                            isRunning = isRunningComparison,
                            progress = comparisonProgress,
                            results = comparisonResults,
                            onRunComparison = {
                                scope.launch {
                                    isRunningComparison = true
                                    comparisonResults = emptyList()

                                    val configsToTest = mutableListOf<OllamaOptimizationConfig>()
                                    selectedConfigsForComparison.forEach { configName ->
                                        val preset = OllamaPresets.getAllPresets().find { it.configName == configName }
                                        if (preset != null) configsToTest.add(preset)
                                    }

                                    val results = optimizer.runComparison(
                                        configs = configsToTest,
                                        prompt = comparisonPrompt,
                                        model = currentModel,
                                        onProgress = { current, total ->
                                            comparisonProgress = current to total
                                        }
                                    )

                                    comparisonResults = results
                                    optimizer.saveComparisonResults(results)
                                    isRunningComparison = false
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
fun PresetsTab(
    presets: List<OllamaOptimizationConfig>,
    selectedPreset: OllamaOptimizationConfig,
    onPresetSelected: (OllamaOptimizationConfig) -> Unit,
    onSavePreset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Choose an optimization preset:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        presets.forEach { preset ->
            PresetCard(
                preset = preset,
                isSelected = preset.configName == selectedPreset.configName,
                onClick = { onPresetSelected(preset) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSavePreset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Selected Preset")
        }
    }
}

@Composable
fun PresetCard(
    preset: OllamaOptimizationConfig,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    preset.configName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                )
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ParameterChip("Ctx: ${preset.numCtx}")
                ParameterChip("Threads: ${preset.numThread}")
                ParameterChip("Temp: ${String.format("%.1f", preset.temperature)}")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ParameterChip("Top-K: ${preset.topK}")
                ParameterChip("Top-P: ${String.format("%.2f", preset.topP)}")
                ParameterChip("Penalty: ${String.format("%.2f", preset.repeatPenalty)}")
            }
        }
    }
}

@Composable
fun ParameterChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
fun CustomConfigTab(
    config: OllamaOptimizationConfig,
    onConfigChanged: (OllamaOptimizationConfig) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Configure custom parameters:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Config Name
        OutlinedTextField(
            value = config.configName,
            onValueChange = { onConfigChanged(config.copy(configName = it)) },
            label = { Text("Configuration Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Context Window
        SliderParameter(
            label = "Context Window (numCtx)",
            value = config.numCtx.toFloat(),
            valueRange = 512f..8192f,
            steps = 15,
            onValueChange = { onConfigChanged(config.copy(numCtx = it.toInt())) },
            displayValue = config.numCtx.toString()
        )

        // Threads
        SliderParameter(
            label = "Threads (numThread)",
            value = config.numThread.toFloat(),
            valueRange = 1f..32f,
            steps = 30,
            onValueChange = { onConfigChanged(config.copy(numThread = it.toInt())) },
            displayValue = config.numThread.toString()
        )

        // Temperature
        SliderParameter(
            label = "Temperature",
            value = config.temperature,
            valueRange = 0f..2f,
            steps = 19,
            onValueChange = { onConfigChanged(config.copy(temperature = it)) },
            displayValue = String.format("%.2f", config.temperature)
        )

        // Top-K
        SliderParameter(
            label = "Top-K Sampling",
            value = config.topK.toFloat(),
            valueRange = 1f..100f,
            steps = 98,
            onValueChange = { onConfigChanged(config.copy(topK = it.toInt())) },
            displayValue = config.topK.toString()
        )

        // Top-P
        SliderParameter(
            label = "Top-P (Nucleus)",
            value = config.topP,
            valueRange = 0f..1f,
            steps = 19,
            onValueChange = { onConfigChanged(config.copy(topP = it)) },
            displayValue = String.format("%.2f", config.topP)
        )

        // Repeat Penalty
        SliderParameter(
            label = "Repeat Penalty",
            value = config.repeatPenalty,
            valueRange = 0.5f..2f,
            steps = 29,
            onValueChange = { onConfigChanged(config.copy(repeatPenalty = it)) },
            displayValue = String.format("%.2f", config.repeatPenalty)
        )

        // Max Tokens
        SliderParameter(
            label = "Max Tokens",
            value = config.maxTokens.toFloat(),
            valueRange = 128f..4096f,
            steps = 31,
            onValueChange = { onConfigChanged(config.copy(maxTokens = it.toInt())) },
            displayValue = config.maxTokens.toString()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Custom Configuration")
        }
    }
}

@Composable
fun SliderParameter(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    displayValue: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                displayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun TemplatesTab(
    templates: List<PromptTemplate>,
    selectedTemplate: PromptTemplate?,
    onTemplateSelected: (PromptTemplate) -> Unit,
    templateParameters: Map<String, String>,
    onParametersChanged: (Map<String, String>) -> Unit,
    onUseTemplate: (PromptTemplate, Map<String, String>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Text(
            "Select a prompt template:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Group templates by category
        val groupedTemplates = templates.groupBy { it.category }

        LazyColumn(modifier = Modifier.weight(1f)) {
            groupedTemplates.forEach { (category, categoryTemplates) ->
                item {
                    Text(
                        category.name.replace("_", " "),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(categoryTemplates) { template ->
                    TemplateCard(
                        template = template,
                        isSelected = template == selectedTemplate,
                        onClick = { onTemplateSelected(template) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        if (selectedTemplate != null) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Button(
                onClick = { onUseTemplate(selectedTemplate, templateParameters) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Use Template for Comparison")
            }
        }
    }
}

@Composable
fun TemplateCard(
    template: PromptTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    template.recommendedConfig,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                template.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ComparisonTab(
    prompt: String,
    onPromptChanged: (String) -> Unit,
    selectedConfigs: Set<String>,
    onConfigSelectionChanged: (Set<String>) -> Unit,
    isRunning: Boolean,
    progress: Pair<Int, Int>,
    results: List<ComparisonResult>,
    onRunComparison: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Text(
            "A/B Comparison Test:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Prompt input
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChanged,
            label = { Text("Test Prompt") },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Config selection
        Text("Select configurations to compare:", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OllamaPresets.getAllPresets().take(3).forEach { preset ->
                FilterChip(
                    selected = selectedConfigs.contains(preset.configName),
                    onClick = {
                        val updated = selectedConfigs.toMutableSet()
                        if (updated.contains(preset.configName)) {
                            updated.remove(preset.configName)
                        } else {
                            updated.add(preset.configName)
                        }
                        onConfigSelectionChanged(updated)
                    },
                    label = { Text(preset.configName, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OllamaPresets.getAllPresets().drop(3).forEach { preset ->
                FilterChip(
                    selected = selectedConfigs.contains(preset.configName),
                    onClick = {
                        val updated = selectedConfigs.toMutableSet()
                        if (updated.contains(preset.configName)) {
                            updated.remove(preset.configName)
                        } else {
                            updated.add(preset.configName)
                        }
                        onConfigSelectionChanged(updated)
                    },
                    label = { Text(preset.configName, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Run button
        Button(
            onClick = onRunComparison,
            enabled = !isRunning && selectedConfigs.isNotEmpty() && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Running ${progress.first}/${progress.second}...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Comparison")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Results
        if (results.isNotEmpty()) {
            Text(
                "Results:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(results) { result ->
                    ComparisonResultCard(result)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ComparisonResultCard(result: ComparisonResult) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    result.configName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${String.format("%.1f", result.tokensPerSecond)} tok/s",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Time: ${result.totalTimeMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    "Tokens: ${result.inputTokens} → ${result.outputTokens}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Response text with dynamic height
            if (expanded) {
                // Full response with scrollable area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        result.response,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            } else {
                // Preview (first 150 chars)
                Text(
                    result.response.take(150) + if (result.response.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    maxLines = 3
                )
            }

            if (!expanded && result.response.length > 150) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Click to expand full response",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}
