package presentation

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
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
import data.EmbeddingStorage
import data.Vendor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
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
    var showEmbeddingsDialog by remember { mutableStateOf(false) }
    var showAssistantSettingsDialog by remember { mutableStateOf(false) }
    var showIssueTicketsDialog by remember { mutableStateOf(false) }
    var showTaskBoardDialog by remember { mutableStateOf(false) }
    var showProjectAssistantDialog by remember { mutableStateOf(false) }
    var pendingVendor by remember { mutableStateOf<Vendor?>(null) }

    // Create Project Assistant ViewModel (lazy initialization)
    val projectAssistantViewModel = remember {
        ProjectAssistantViewModel(
            apiClient = viewModel.getApiClient(),
            mcpServerManager = viewModel.getMcpServerManager()
        )
    }
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    LaunchedEffect(viewModel.uiState.value.joke) {
        viewModel.uiState.value.joke?.let {
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
                    title = { Text("${viewModel.uiState.value.currentSessionName} - ${viewModel.uiState.value.currentVendor.displayName}") },
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
                        IconButton(onClick = { showEmbeddingsDialog = true }) {
                            Icon(
                                Icons.Default.DataObject,
                                contentDescription = "Embeddings",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showAssistantSettingsDialog = true }) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = "Code Assistant",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showIssueTicketsDialog = true }) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = "Issue Tickets",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showTaskBoardDialog = true }) {
                            Icon(
                                Icons.Default.Assignment,
                                contentDescription = "Task Board",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showProjectAssistantDialog = true }) {
                            Icon(
                                Icons.Default.Assistant,
                                contentDescription = "Project Assistant",
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
                                if (viewModel.uiState.value.currentVendor != vendor) {
                                    pendingVendor = vendor
                                    showApiKeyDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.uiState.value.currentVendor == vendor)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Gray,
                                contentColor = if (viewModel.uiState.value.currentVendor == vendor)
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
                val stats = viewModel.uiState.value.sessionStats
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
                                    val previousTime = viewModel.uiState.value.previousResponseTime
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

                    if (viewModel.uiState.value.isLoading) {
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
                viewModel.uiState.value.errorMessage?.let { error ->
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

                // Command Status Warning
                if (!viewModel.uiState.value.codeAssistantEnabled || viewModel.uiState.value.codeAssistantWorkingDir.isNullOrBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF9800)
                        )
                    ) {
                        Text(
                            text = "Code Assistant not configured. Commands require a working directory.",
                            modifier = Modifier.padding(12.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                val captureMessage =  {
                    if (inputText.isNotBlank() && !viewModel.uiState.value.isLoading && !viewModel.uiState.value.isCompacting) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                }

                // Input Field with Command Dropdown
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column {
                        // Command Autocomplete Dropdown
                        CommandAutocompleteDropdown(
                            inputText = inputText,
                            onCommandSelect = { command ->
                                inputText = command
                            }
                        )

                        // Input Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .onKeyEvent { event ->
                                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                            if (!event.isShiftPressed) {
                                                captureMessage()
                                                true
                                            } else {
                                                false // Allow Shift+Enter for newline if needed
                                            }
                                        } else {
                                            false
                                        }
                                    },
                                placeholder = {
                                    Text(
                                        if (viewModel.uiState.value.isCompacting) "Compacting messages..."
                                        else "Type message or command (/)..."
                                    )
                                },
                                enabled = !viewModel.uiState.value.isLoading && !viewModel.uiState.value.isCompacting,
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
                                enabled = inputText.isNotBlank() && !viewModel.uiState.value.isLoading && !viewModel.uiState.value.isCompacting,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        if (inputText.isNotBlank() && !viewModel.uiState.value.isLoading && !viewModel.uiState.value.isCompacting)
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
                }
                }

                // Session Sidebar on the right
                SessionSidebar(
                    sessions = viewModel.sessions,
                    currentSessionId = viewModel.uiState.value.currentSessionId,
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
                servers = viewModel.uiState.value.appSettings.mcpServers,
                onSave = { servers ->
                    viewModel.updateMcpServers(servers)
                    showMcpSettingsDialog = false
                },
                onDismiss = { showMcpSettingsDialog = false }
            )
        }

        if (showJokeDialog && viewModel.uiState.value.joke != null) {
            JokeDialog(
                joke = viewModel.uiState.value.joke!!,
                onDismiss = {
                    showJokeDialog = false
                    viewModel.clearJoke()
                }
            )
        }

        if (showEmbeddingsDialog) {
            EmbeddingsDialog(
                onDismiss = { showEmbeddingsDialog = false }
            )
        }

        if (showAssistantSettingsDialog) {
            AssistantSettingsDialog(
                settings = viewModel.uiState.value.codeAssistantSettings,
                onDismiss = { showAssistantSettingsDialog = false },
                onSave = { settings ->
                    viewModel.updateCodeAssistantSettings(settings)
                    showAssistantSettingsDialog = false
                }
            )
        }

        if (showIssueTicketsDialog) {
            IssueTicketsDialog(
                onDismiss = { showIssueTicketsDialog = false },
                onResolveTicket = { ticket ->
                    viewModel.resolveTicketWithAI(ticket)
                },
                isResolving = viewModel.uiState.value.isResolvingTicket,
                resolvingTicketId = viewModel.uiState.value.resolvingTicketId
            )
        }

        if (showTaskBoardDialog) {
            TaskBoardDialog(
                onDismiss = { showTaskBoardDialog = false }
            )
        }

        if (showProjectAssistantDialog) {
            ProjectAssistantDialog(
                viewModel = projectAssistantViewModel,
                onDismiss = { showProjectAssistantDialog = false }
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

        if (viewModel.uiState.value.showTaskReminderDialog) {
            TaskReminderDialog(
                taskSummary = viewModel.uiState.value.taskReminderSummary,
                onDismiss = { viewModel.dismissTaskReminderDialog() }
            )
        }
    }
}

data class CommandInfo(
    val command: String,
    val description: String,
    val usage: String
)

@Composable
fun CommandAutocompleteDropdown(
    inputText: String,
    onCommandSelect: (String) -> Unit
) {
    val showDropdown = inputText.startsWith("/") && inputText.length >= 1

    val commands = remember {
        listOf(
            CommandInfo("/help", "Show detailed project information and available commands", "/help"),
            CommandInfo("/search", "Search for files or code in the project", "/search <query>"),
            CommandInfo("/analyze", "Analyze a specific file", "/analyze <file>"),
            CommandInfo("/context", "Toggle auto-context enrichment", "/context on|off"),
            CommandInfo("/git status", "Show repository status", "/git status"),
            CommandInfo("/git diff", "Show uncommitted changes", "/git diff"),
            CommandInfo("/git log", "Show commit history", "/git log"),
            CommandInfo("/git branch", "Show current branch info", "/git branch"),
            CommandInfo("/review-pr", "Review current branch as pull request", "/review-pr")
        )
    }

    if (showDropdown) {
        val searchTerm = inputText.lowercase()
        val filteredCommands = commands.filter {
            it.command.lowercase().startsWith(searchTerm) ||
            it.description.lowercase().contains(searchTerm.drop(1))
        }

        if (filteredCommands.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredCommands) { commandInfo ->
                        CommandItem(
                            commandInfo = commandInfo,
                            onClick = { onCommandSelect(commandInfo.usage) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommandItem(
    commandInfo: CommandInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = commandInfo.command,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = commandInfo.description,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        if (commandInfo.usage != commandInfo.command) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Usage: ${commandInfo.usage}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
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
    var editedPrompt by remember { mutableStateOf(viewModel.uiState.value.systemPrompt) }
    var editedTemperature by remember { mutableStateOf(viewModel.uiState.value.temperature) }
    var editedMaxTokens by remember { mutableStateOf(viewModel.uiState.value.maxTokens.toString()) }
    var editedModel by remember { mutableStateOf(viewModel.uiState.value.selectedModel) }
    var editedCompactionEnabled by remember { mutableStateOf(viewModel.uiState.value.compactionEnabled) }
    var editedPipelineEnabled by remember { mutableStateOf(viewModel.uiState.value.pipelineEnabled) }
    var editedPipelineMaxIterations by remember { mutableStateOf(viewModel.uiState.value.pipelineMaxIterations.toString()) }
    var editedEmbeddingsEnabled by remember { mutableStateOf(viewModel.uiState.value.embeddingsEnabled) }
    var editedSelectedEmbedding by remember { mutableStateOf(viewModel.uiState.value.selectedEmbeddingFile) }
    var editedEmbeddingTopK by remember { mutableStateOf(viewModel.uiState.value.embeddingTopK.toString()) }
    var editedEmbeddingThreshold by remember { mutableStateOf(viewModel.uiState.value.embeddingThreshold.toString()) }
    var expanded by remember { mutableStateOf(false) }
    var embeddingExpanded by remember { mutableStateOf(false) }
    val availableEmbeddings = remember { EmbeddingStorage.listEmbeddingFiles() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(750.dp)
                    .verticalScroll(rememberScrollState())
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

                if (viewModel.uiState.value.isLoadingModels) {
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

                Spacer(modifier = Modifier.height(24.dp))

                // Pipeline Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Enable MCP Pipeline Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Allow multi-step tool execution chains",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = editedPipelineEnabled,
                        onCheckedChange = { editedPipelineEnabled = it }
                    )
                }

                // Pipeline Max Iterations (only show when pipeline is enabled)
                if (editedPipelineEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Max Pipeline Iterations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Maximum number of tool execution rounds (1-10)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedPipelineMaxIterations,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                                val value = it.toIntOrNull() ?: 0
                                if (value in 0..10 || it.isEmpty()) {
                                    editedPipelineMaxIterations = it
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., 5") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Embeddings Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Enable Embeddings (RAG)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Use embeddings to provide relevant context",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = editedEmbeddingsEnabled,
                        onCheckedChange = { editedEmbeddingsEnabled = it }
                    )
                }

                // Embedding Settings (only show when embeddings are enabled)
                if (editedEmbeddingsEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Select Embedding",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableEmbeddings.isEmpty()) {
                        Text(
                            "No embeddings found. Create one using the Embeddings dialog.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = embeddingExpanded,
                            onExpandedChange = { embeddingExpanded = !embeddingExpanded }
                        ) {
                            val selectedEmbeddingName = editedSelectedEmbedding?.let { File(it).nameWithoutExtension } ?: "None"

                            OutlinedTextField(
                                value = selectedEmbeddingName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = embeddingExpanded)
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
                                expanded = embeddingExpanded,
                                onDismissRequest = { embeddingExpanded = false }
                            ) {
                                availableEmbeddings.forEach { file ->
                                    DropdownMenuItem(
                                        text = { Text(file.nameWithoutExtension) },
                                        onClick = {
                                            editedSelectedEmbedding = file.absolutePath
                                            embeddingExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Top K Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Number of relevant chunks to retrieve (1-10)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedEmbeddingTopK,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                                val value = it.toIntOrNull() ?: 0
                                if (value in 0..10 || it.isEmpty()) {
                                    editedEmbeddingTopK = it
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., 3") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Similarity Threshold",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Minimum similarity score (0.0-1.0)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedEmbeddingThreshold,
                        onValueChange = {
                            if (it.isEmpty() || it.matches(Regex("^0?(\\.\\d{0,2})?$|^1(\\.0{0,2})?$"))) {
                                editedEmbeddingThreshold = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., 0.5") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
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
                    viewModel.updateSettingsFields(
                        systemPrompt = editedPrompt,
                        temperature = editedTemperature,
                        maxTokens = editedMaxTokens.toIntOrNull() ?: 4096,
                        selectedModel = editedModel,
                        compactionEnabled = editedCompactionEnabled
                    )

                    // Save pipeline settings
                    val maxIterations = editedPipelineMaxIterations.toIntOrNull()?.coerceIn(1, 10) ?: 5
                    viewModel.updatePipelineSettings(editedPipelineEnabled, maxIterations)

                    // Save embedding settings
                    val topK = editedEmbeddingTopK.toIntOrNull()?.coerceIn(1, 10) ?: 3
                    val threshold = editedEmbeddingThreshold.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f
                    viewModel.updateEmbeddingSettings(editedEmbeddingsEnabled, editedSelectedEmbedding, topK, threshold)

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
        Vendor.OLLAMA -> null  // Ollama doesn't need an API key
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
