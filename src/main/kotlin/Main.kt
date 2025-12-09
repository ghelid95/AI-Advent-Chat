import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App(viewModel: ChatViewModel) {
    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showJokeDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
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
                    title = { Text("AI Chat - ${viewModel.currentVendor.value.displayName}") },
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
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
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
                        MessageBubble(message)
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
                    if (inputText.isNotBlank() && !viewModel.isLoading.value) {
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
                        placeholder = { Text("Type your message...") },
                        enabled = !viewModel.isLoading.value,
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
                        enabled = inputText.isNotBlank() && !viewModel.isLoading.value,
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (inputText.isNotBlank() && !viewModel.isLoading.value)
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

        if (showSettingsDialog) {
            SettingsDialog(viewModel = viewModel, onDismiss = { showSettingsDialog = false })
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
    }
}

@Composable
fun MessageBubble(message: Message) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val time = dateFormat.format(Date(message.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 400.dp),
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
                    MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color.Gray,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
fun ApiKeyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About API Key") },
        text = {
            Column {
                Text("Your OpenAI API key is configured at startup.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "To change it, restart the application and provide a new key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    var editedPrompt by remember { mutableStateOf(viewModel.systemPrompt.value) }
    var editedTemperature by remember { mutableStateOf(viewModel.temperature.value) }
    var editedModel by remember { mutableStateOf(viewModel.selectedModel.value) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(600.dp)
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
                    viewModel.selectedModel.value = editedModel
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
fun VendorSwitchDialog(vendor: Vendor, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter ${vendor.displayName} API Key") },
        text = {
            Column {
                Text(
                    "Please enter your API key for ${vendor.displayName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        error = null
                    },
                    label = { Text("API Key") },
                    placeholder = {
                        Text(when (vendor) {
                            Vendor.ANTHROPIC -> "sk-ant-..."
                            Vendor.PERPLEXITY -> "pplx-..."
                        })
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    isError = error != null
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (apiKey.isBlank()) {
                        error = "API key cannot be empty"
                    } else {
                        onConfirm(apiKey)
                    }
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
}

fun main() = application {
    var apiKey by remember { mutableStateOf("") }
    var showApiKeyInput by remember { mutableStateOf(true) }
    var viewModel: ChatViewModel? by remember { mutableStateOf(null) }

    if (showApiKeyInput) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Enter API Key",
            state = rememberWindowState(width = 400.dp, height = 400.dp)
        ) {
            MaterialTheme {
                var inputKey by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Claude API Key",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = inputKey,
                            onValueChange = {
                                inputKey = it
                                error = null
                            },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-ant-...") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        error?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (inputKey.isBlank()) {
                                    error = "API key cannot be empty"
                                } else {
                                    apiKey = inputKey
                                    viewModel = ChatViewModel(apiKey)
                                    showApiKeyInput = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Chat")
                        }
                    }
                }
            }
        }
    } else {
        Window(
            onCloseRequest = {
                viewModel?.cleanup()
                exitApplication()
            },
            title = "AI Chat",
            state = rememberWindowState(width = 900.dp, height = 700.dp)
        ) {
            viewModel?.let { App(it) }
        }
    }
}