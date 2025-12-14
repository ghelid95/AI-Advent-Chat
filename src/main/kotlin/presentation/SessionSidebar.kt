package presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.SessionSummary
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SessionSidebar(
    sessions: List<SessionSummary>,
    currentSessionId: String?,
    onSessionClick: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<SessionSummary?>(null) }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Header with New Session button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onNewSession) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Session",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Sessions List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
                        isActive = session.id == currentSessionId,
                        onClick = { onSessionClick(session.id) },
                        onDelete = { onDeleteSession(session.id) },
                        onRename = {
                            sessionToRename = session
                            showRenameDialog = true
                        }
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog && sessionToRename != null) {
        RenameSessionDialog(
            currentName = sessionToRename!!.name,
            onDismiss = {
                showRenameDialog = false
                sessionToRename = null
            },
            onConfirm = { newName ->
                onRenameSession(sessionToRename!!.id, newName)
                showRenameDialog = false
                sessionToRename = null
            }
        )
    }
}

@Composable
private fun SessionItem(
    session: SessionSummary,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Options",
                            tint = if (isActive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = dateFormat.format(Date(session.lastModified)),
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${session.messageCount} msgs",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Text(
                    text = "$${String.format("%.4f", session.totalCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun RenameSessionDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Session") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Session Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onConfirm(newName)
                    }
                }
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
