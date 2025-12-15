package presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import data.mcp.McpServerConfig
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSettingsDialog(
    servers: List<McpServerConfig>,
    onSave: (List<McpServerConfig>) -> Unit,
    onDismiss: () -> Unit
) {
    var editedServers by remember { mutableStateOf(servers.toMutableList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MCP Server Configuration") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                Text(
                    "Configure Model Context Protocol servers to enable tool usage",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(editedServers) { server ->
                        McpServerCard(
                            server = server,
                            onDelete = { editedServers.remove(server) },
                            onToggle = { enabled ->
                                val index = editedServers.indexOf(server)
                                editedServers[index] = server.copy(enabled = enabled)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Server")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add MCP Server")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(editedServers) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showAddDialog) {
        AddMcpServerDialog(
            onAdd = { newServer ->
                editedServers.add(newServer)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun McpServerCard(
    server: McpServerConfig,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${server.command} ${server.args.joinToString(" ")}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                if (server.env.isNotEmpty()) {
                    Text(
                        "Env: ${server.env.keys.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = server.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMcpServerDialog(
    onAdd: (McpServerConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }
    var envVars by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MCP Server") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command") },
                    placeholder = { Text("java, python, npx, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = { Text("Arguments (space-separated)") },
                    placeholder = { Text("-jar C:\\path\\to\\server.jar") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = envVars,
                    onValueChange = { envVars = it },
                    label = { Text("Environment Variables (optional)") },
                    placeholder = { Text("KEY1=value1 KEY2=value2") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Example: java -jar C:\\tools\\mcp-server.jar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && command.isNotBlank()) {
                        // Parse arguments
                        val argsList = if (args.isBlank()) emptyList() else {
                            args.split(" ").filter { it.isNotBlank() }
                        }

                        // Parse environment variables
                        val envMap = if (envVars.isBlank()) emptyMap() else {
                            envVars.split(" ").filter { it.contains("=") }.associate {
                                val (key, value) = it.split("=", limit = 2)
                                key.trim() to value.trim()
                            }
                        }

                        onAdd(
                            McpServerConfig(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                command = command,
                                args = argsList,
                                env = envMap,
                                enabled = true
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && command.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
