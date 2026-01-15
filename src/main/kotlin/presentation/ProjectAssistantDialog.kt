package presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import data.UsageInfo
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectAssistantDialog(
    viewModel: ProjectAssistantViewModel,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val state = viewModel.uiState.value

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .width(900.dp)
                .height(700.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                ProjectAssistantHeader(
                    state = state,
                    onSettingsClick = { showSettings = true },
                    onClearClick = { viewModel.clearConversation() },
                    onRefreshClick = { viewModel.refreshContext() },
                    onDismiss = onDismiss
                )

                // Stats bar
                ProjectAssistantStatsBar(state = state)

                // Messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    if (viewModel.messages.isEmpty()) {
                        item {
                            EmptyStateMessage()
                        }
                    }

                    items(viewModel.messages) { message ->
                        ProjectAssistantMessageBubble(message = message)
                    }

                    if (state.isLoading) {
                        item {
                            LoadingIndicator(gatheringContext = state.gatheringContext)
                        }
                    }
                }

                // Error message
                state.errorMessage?.let { error ->
                    ErrorBanner(error = error)
                }

                // Input area
                ProjectAssistantInput(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() && !state.isLoading) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    isLoading = state.isLoading,
                    toolsLoaded = state.toolsLoaded
                )
            }
        }

        // Settings dialog
        if (showSettings) {
            ProjectAssistantSettingsDialog(
                currentModel = state.model,
                currentMaxIterations = state.maxIterations,
                onSave = { model, maxIterations ->
                    viewModel.updateSettings(model, maxIterations)
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
private fun ProjectAssistantHeader(
    state: ProjectAssistantUiState,
    onSettingsClick: () -> Unit,
    onClearClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = Color(0xFF2A2A2A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        "Project Assistant",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (state.toolsLoaded) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                        Text(
                            if (state.toolsLoaded) "Tools ready" else "Loading tools...",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.toolsLoaded) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRefreshClick) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Context",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onClearClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Clear Chat",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectAssistantStatsBar(state: ProjectAssistantUiState) {
    if (state.totalTokens > 0) {
        Surface(
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatItem(
                        icon = Icons.Default.Token,
                        label = "Tokens",
                        value = "${state.totalTokens} (↑${state.totalInputTokens} ↓${state.totalOutputTokens})"
                    )
                    StatItem(
                        icon = Icons.Default.Chat,
                        label = "Messages",
                        value = "${state.messageCount}"
                    )
                }
                Text(
                    "Cost: $${String.format("%.4f", state.totalCost)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(16.dp)
        )
        Text(
            "$label: $value",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun EmptyStateMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Text(
                "Project Assistant",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Text(
                "Ask me anything about your project!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
            Column(
                modifier = Modifier
                    .background(Color(0xFF252525), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "I can help you with:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                SuggestionItem("Git status, branches, and recent commits")
                SuggestionItem("Issue tickets and bug tracking")
                SuggestionItem("Task board and project progress")
                SuggestionItem("Reading and exploring code files")
                SuggestionItem("Project documentation and architecture")
            }

            Text(
                "Try: \"What's the current git status?\" or \"Show me open tasks\"",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SuggestionItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun ProjectAssistantMessageBubble(message: ProjectAssistantMessage) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val time = dateFormat.format(Date(message.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 700.dp),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (message.isUser) 12.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 12.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser)
                    MaterialTheme.colorScheme.primary
                else
                    Color(0xFF2A2A2A)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Tools used indicator
                if (!message.isUser && message.toolsUsed.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(14.dp)
                        )
                        message.toolsUsed.take(3).forEach { tool ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF3A3A3A), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    tool,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                        if (message.toolsUsed.size > 3) {
                            Text(
                                "+${message.toolsUsed.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Message content
                SelectionContainer {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer with time and usage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color.Gray
                    )

                    message.usage?.let { usage ->
                        Text(
                            text = "${usage.totalTokens} tokens • $${String.format("%.4f", usage.estimatedCost)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator(gatheringContext: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (gatheringContext) "Gathering project context..." else "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5252))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = Color.White
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ProjectAssistantInput(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    toolsLoaded: Boolean
) {
    Surface(
        color = Color(0xFF2A2A2A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            if (!event.isShiftPressed) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    },
                placeholder = {
                    Text(
                        if (!toolsLoaded) "Waiting for tools to load..."
                        else if (isLoading) "Processing..."
                        else "Ask about your project..."
                    )
                },
                enabled = !isLoading && toolsLoaded,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 3
            )

            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading && toolsLoaded,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (inputText.isNotBlank() && !isLoading && toolsLoaded)
                            MaterialTheme.colorScheme.primary
                        else Color.Gray,
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectAssistantSettingsDialog(
    currentModel: String,
    currentMaxIterations: Int,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedModel by remember { mutableStateOf(currentModel) }
    var maxIterations by remember { mutableStateOf(currentMaxIterations.toString()) }
    var modelExpanded by remember { mutableStateOf(false) }

    val models = listOf(
        "claude-3-haiku-20240307" to "Claude 3 Haiku (Fast & Cheap)",
        "claude-sonnet-4-20250514" to "Claude Sonnet 4",
        "claude-3-5-sonnet-20241022" to "Claude 3.5 Sonnet"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(400.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Project Assistant Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Model selection
                Text("Model", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = !modelExpanded }
                ) {
                    OutlinedTextField(
                        value = models.find { it.first == selectedModel }?.second ?: selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        models.forEach { (modelId, displayName) ->
                            DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = {
                                    selectedModel = modelId
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }

                // Max iterations
                Text("Max Tool Iterations", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                OutlinedTextField(
                    value = maxIterations,
                    onValueChange = {
                        if (it.all { c -> c.isDigit() } || it.isEmpty()) {
                            val value = it.toIntOrNull() ?: 0
                            if (value in 0..15 || it.isEmpty()) {
                                maxIterations = it
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("1-15") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )
                Text(
                    "Higher values allow more tool calls but increase cost",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val iterations = maxIterations.toIntOrNull()?.coerceIn(1, 15) ?: 8
                            onSave(selectedModel, iterations)
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}
