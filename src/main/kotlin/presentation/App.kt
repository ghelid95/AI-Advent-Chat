package presentation

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import data.Vendor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App(viewModel: ChatViewModel, getApiKey: (Vendor) -> String?) {
    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showJokeDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showMcpSettingsDialog by remember { mutableStateOf(false) }
    var pendingVendor by remember { mutableStateOf<Vendor?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    LaunchedEffect(viewModel.joke.value) {
        viewModel.joke.value?.let {
            showJokeDialog = true
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onSurface = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("${viewModel.currentSessionName.value} - ${viewModel.currentVendor.value.displayName}") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                if (viewModel.messages.isNotEmpty()) {
                                    val chatHistory = viewModel.getChatHistory()
                                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                    clipboard.setContents(StringSelection(chatHistory), null)
                                }
                            },
                            enabled = viewModel.messages.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Chat",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { viewModel.performManualCompaction() },
                            enabled = viewModel.canCompact()
                        ) {
                            Icon(
                                Icons.Default.Compress,
                                contentDescription = "Compact History",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showMcpSettingsDialog = true }) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = "MCP Servers",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear Chat",
                                tint = Color.White
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Main content area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                // Vendor Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Vendor.entries.forEach { vendor ->
                        Button(
                            onClick = {
                                if (viewModel.currentVendor.value != vendor) {
                                    pendingVendor = vendor
                                    showApiKeyDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.currentVendor.value == vendor)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Gray,
                                contentColor = if (viewModel.currentVendor.value == vendor)
                                    Color.White
                                else
                                    Color.Black
                            )
                        ) {
                            Text(vendor.displayName)
                        }
                    }
                }

                // Usage Stats
                val stats = viewModel.sessionStats.value
                if (stats.totalTokens > 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Session Stats",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Tokens: ${stats.totalTokens} (↑${stats.totalInputTokens} ↓${stats.totalOutputTokens})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Chat total cost: $${String.format("%.4f", stats.totalCost)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold
                                )
                                if (stats.lastRequestTimeMs > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val previousTime = viewModel.previousResponseTime.value
                                    val currentTime = stats.lastRequestTimeMs
                                    val comparisonText = if (previousTime != null && previousTime > 0) {
                                        if (currentTime > previousTime) {
                                            "Last response time: ${currentTime}ms ↓"
                                        } else if (currentTime < previousTime) {
                                            "Last response time: ${currentTime}ms ↑"
                                        } else {
                                            "Last response time: ${currentTime}ms"
                                        }
                                    } else {
                                        "Last response time: ${currentTime}ms"
                                    }
                                    val textColor = if (previousTime != null && previousTime > 0) {
                                        when {
                                            currentTime > previousTime -> Color.Red
                                            currentTime < previousTime -> Color(0xFF4CAF50)
                                            else -> Color.Gray
                                        }
                                    } else {
                                        Color.Gray
                                    }
                                    Text(
                                        comparisonText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (viewModel.messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Start a conversation with AI",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    items(viewModel.messages) { message ->
                        MessageBubble(
                            message = message,
                            onExpand = if (message.content.startsWith(">") && message.content.contains("compacted")) {
                                { viewModel.toggleSummaryExpansion(message.timestamp) }
                            } else null
                        )
                    }

                    if (viewModel.isLoading.value) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

                // Error Message
                viewModel.errorMessage.value?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF5252)
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(12.dp),
                            color = Color.White
                        )
                    }
                }

                val captureMessage =  {
                    if (inputText.isNotBlank() && !viewModel.isLoading.value && !viewModel.isCompacting.value) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                }

                // Input Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (viewModel.isCompacting.value) "Compacting messages..."
                                else "Type your message..."
                            )
                        },
                        enabled = !viewModel.isLoading.value && !viewModel.isCompacting.value,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                captureMessage()
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            captureMessage()
                        },
                        enabled = inputText.isNotBlank() && !viewModel.isLoading.value && !viewModel.isCompacting.value,
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (inputText.isNotBlank() && !viewModel.isLoading.value && !viewModel.isCompacting.value)
                                    MaterialTheme.colorScheme.primary
                                else Color.Gray,
                                shape = RoundedCornerShape(28.dp)
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

                // Session Sidebar on the right
                SessionSidebar(
                    sessions = viewModel.sessions,
                    currentSessionId = viewModel.currentSessionId.value,
                    onSessionClick = { sessionId ->
                        viewModel.loadSession(sessionId)
                    },
                    onNewSession = {
                        viewModel.createNewSession()
                    },
                    onDeleteSession = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    },
                    onRenameSession = { sessionId, newName ->
                        viewModel.renameSession(sessionId, newName)
                    }
                )
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(viewModel = viewModel, onDismiss = { showSettingsDialog = false })
        }

        if (showMcpSettingsDialog) {
            McpSettingsDialog(
                servers = viewModel.appSettings.value.mcpServers,
                onSave = { servers ->
                    viewModel.updateMcpServers(servers)
                    showMcpSettingsDialog = false
                },
                onDismiss = { showMcpSettingsDialog = false }
            )
        }

        if (showJokeDialog && viewModel.joke.value != null) {
            JokeDialog(
                joke = viewModel.joke.value!!,
                onDismiss = {
                    showJokeDialog = false
                    viewModel.joke.value = null
                }
            )
        }

        if (showApiKeyDialog && pendingVendor != null) {
            VendorSwitchDialog(
                vendor = pendingVendor!!,
                getApiKey = getApiKey,
                onConfirm = { apiKey ->
                    viewModel.switchVendor(pendingVendor!!, apiKey)
                    showApiKeyDialog = false
                    pendingVendor = null
                },
                onDismiss = {
                    showApiKeyDialog = false
                    pendingVendor = null
                }
            )
        }

        if (viewModel.showTaskReminderDialog.value) {
            TaskReminderDialog(
                taskSummary = viewModel.taskReminderSummary.value,
                onDismiss = { viewModel.dismissTaskReminderDialog() }
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message, onExpand: (() -> Unit)? = null) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val time = dateFormat.format(Date(message.timestamp))
    val isCompactedPlaceholder = message.content.startsWith(">") && message.content.contains("compacted")
    val isToolUse = message.content.startsWith("[Using tools:")
    val isToolResult = message.content.startsWith("Tool '")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .let { if (isCompactedPlaceholder && onExpand != null) it.clickable { onExpand() } else it },
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (message.isUser) 12.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 12.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    message.isUser -> MaterialTheme.colorScheme.primary
                    isToolUse -> Color(0xFF1E3A5F) // Dark blue for tool use
                    isToolResult -> Color(0xFF2D4A2C) // Dark green for tool results
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isCompactedPlaceholder) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Show tool icon for tool-related messages
                    if (isToolUse || isToolResult) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = "Tool",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontFamily = if (isToolResult) androidx.compose.ui.text.font.FontFamily.Monospace else null
                            )
                        }
                    } else {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.isUser) Color.White else Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color.Gray,
                        fontWeight = FontWeight.Light
                    )
                    message.usage?.let { usage ->
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            val tokenCount = if (message.isUser) usage.inputTokens else usage.outputTokens
                            val tokenType = if (message.isUser) "in" else "out"
                            Text(
                                text = "$tokenCount tokens $tokenType",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.isUser) Color.White.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                text = "$${String.format("%.4f", if (message.isUser) usage.estimatedInputCost else usage.estimatedOutputCost)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.isUser) Color.White.copy(alpha = 0.6f) else Color(0xFF4CAF50).copy(alpha = 0.8f),
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    var editedPrompt by remember { mutableStateOf(viewModel.systemPrompt.value) }
    var editedTemperature by remember { mutableStateOf(viewModel.temperature.value) }
    var editedMaxTokens by remember { mutableStateOf(viewModel.maxTokens.value.toString()) }
    var editedModel by remember { mutableStateOf(viewModel.selectedModel.value) }
    var editedCompactionEnabled by remember { mutableStateOf(viewModel.compactionEnabled.value) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(650.dp)
            ) {
                Text(
                    "System Prompt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedPrompt,
                    onValueChange = { editedPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = { Text("Enter system prompt...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Temperature: ${String.format("%.2f", editedTemperature)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Controls randomness. Lower = focused, Higher = creative",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = editedTemperature,
                    onValueChange = { editedTemperature = it },
                    valueRange = 0f..1f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Max Tokens",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Maximum number of tokens in the response",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedMaxTokens,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                            editedMaxTokens = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., 4096") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (viewModel.isLoadingModels.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (viewModel.availableModels.isEmpty()) {
                    OutlinedTextField(
                        value = editedModel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        enabled = false,
                        label = { Text("Failed to load models") }
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        val selectedModelInfo = viewModel.availableModels.find { it.id == editedModel }
                        val displayValue = selectedModelInfo?.displayName ?: selectedModelInfo?.id ?: editedModel

                        OutlinedTextField(
                            value = displayValue,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            viewModel.availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.displayName ?: model.id) },
                                    onClick = {
                                        editedModel = model.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Compaction Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Auto-Compact Chat History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Automatically summarize every 10 messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = editedCompactionEnabled,
                        onCheckedChange = { editedCompactionEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Note: Changes apply immediately to your next message. You can optionally clear the chat for a fresh start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.systemPrompt.value = editedPrompt
                    viewModel.temperature.value = editedTemperature
                    viewModel.maxTokens.value = editedMaxTokens.toIntOrNull() ?: 4096
                    viewModel.selectedModel.value = editedModel
                    viewModel.compactionEnabled.value = editedCompactionEnabled
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun JokeDialog(joke: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Here's a joke for you!") },
        text = {
            Text(
                text = joke,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Thanks!")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun VendorSwitchDialog(
    vendor: Vendor,
    getApiKey: (Vendor) -> String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val apiKey = getApiKey(vendor)
    val envVarName = when (vendor) {
        Vendor.ANTHROPIC -> "CLAUDE_API_KEY"
        Vendor.PERPLEXITY -> "PERPLEXITY_API_KEY"
    }

    if (apiKey != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Switch to ${vendor.displayName}?") },
            text = {
                Text(
                    "Do you want to switch to ${vendor.displayName}?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(apiKey)
                    }
                ) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("API Key Not Found") },
            text = {
                Column {
                    Text(
                        "Cannot switch to ${vendor.displayName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Please set the following environment variable:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            "$envVarName=...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }
}
