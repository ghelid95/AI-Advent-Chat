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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueTicketsDialog(
    onDismiss: () -> Unit,
    onResolveTicket: ((IssueTicket) -> Unit)? = null,
    isResolving: Boolean = false,
    resolvingTicketId: String? = null
) {
    val ticketStorage = remember { IssueTicketStorage() }
    var tickets by remember { mutableStateOf(ticketStorage.loadTickets()) }
    var selectedTicket by remember { mutableStateOf<IssueTicket?>(null) }
    var filterStatus by remember { mutableStateOf<TicketStatus?>(null) }
    var filterType by remember { mutableStateOf<TicketType?>(null) }
    var showResolutionDialog by remember { mutableStateOf<IssueTicket?>(null) }

    // Refresh tickets when resolution changes
    LaunchedEffect(isResolving) {
        if (!isResolving) {
            tickets = ticketStorage.loadTickets()
        }
    }

    val filteredTickets = remember(tickets, filterStatus, filterType) {
        tickets.filter { ticket ->
            (filterStatus == null || ticket.status == filterStatus) &&
            (filterType == null || ticket.type == filterType)
        }.sortedByDescending { it.createdAt }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(800.dp)
                .height(600.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    "Issue Tickets",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Filters Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status Filter
                    var statusExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = !statusExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = filterStatus?.name ?: "All Status",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Status") },
                                onClick = {
                                    filterStatus = null
                                    statusExpanded = false
                                }
                            )
                            TicketStatus.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.name) },
                                    onClick = {
                                        filterStatus = status
                                        statusExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Type Filter
                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = filterType?.name ?: "All Types",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Types") },
                                onClick = {
                                    filterType = null
                                    typeExpanded = false
                                }
                            )
                            TicketType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        filterType = type
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ticket count
                Text(
                    "${filteredTickets.size} tickets found",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tickets List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTickets) { ticket ->
                        TicketCard(
                            ticket = ticket,
                            isSelected = selectedTicket?.id == ticket.id,
                            onClick = {
                                selectedTicket = if (selectedTicket?.id == ticket.id) null else ticket
                            },
                            onResolveClick = if (onResolveTicket != null) {
                                { onResolveTicket(ticket) }
                            } else null,
                            onViewResolutionClick = if (ticket.aiResolution != null) {
                                { showResolutionDialog = ticket }
                            } else null,
                            isResolving = isResolving && resolvingTicketId == ticket.id
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Resolution Dialog
    showResolutionDialog?.let { ticket ->
        AIResolutionDialog(
            ticket = ticket,
            onDismiss = { showResolutionDialog = null }
        )
    }
}

@Composable
fun TicketCard(
    ticket: IssueTicket,
    isSelected: Boolean,
    onClick: () -> Unit,
    onResolveClick: (() -> Unit)? = null,
    onViewResolutionClick: (() -> Unit)? = null,
    isResolving: Boolean = false
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2A3A4A) else Color(0xFF1E2830)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: ID, Title, Priority Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        ticket.id,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    PriorityBadge(ticket.priority)
                }
                StatusBadge(ticket.status)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                ticket.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Type and Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TypeBadge(ticket.type)
                Text(
                    dateFormat.format(Date(ticket.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // Expanded content when selected
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Description:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    ticket.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Reporter: ${ticket.reporter}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        "Updated: ${dateFormat.format(Date(ticket.updatedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                // AI Resolution buttons
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Show "AI Resolution" button if resolution exists
                    if (ticket.aiResolution != null && onViewResolutionClick != null) {
                        Button(
                            onClick = onViewResolutionClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Resolution")
                        }
                    }

                    // Show "Resolve with AI" button if no resolution and callback provided
                    if (ticket.aiResolution == null && onResolveClick != null) {
                        Button(
                            onClick = onResolveClick,
                            enabled = !isResolving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Resolving...")
                            } else {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resolve with AI")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AIResolutionDialog(
    ticket: IssueTicket,
    onDismiss: () -> Unit
) {
    val resolution = ticket.aiResolution ?: return
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(700.dp)
                .heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "AI Resolution",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "for ${ticket.id}: ${ticket.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Generated: ${dateFormat.format(Date(resolution.generatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Tools Used
                    if (resolution.toolsUsed.isNotEmpty()) {
                        Text(
                            "Tools Used:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            resolution.toolsUsed.forEach { tool ->
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF3A4A5A), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        tool,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Related Tickets
                    if (resolution.relatedTicketIds.isNotEmpty()) {
                        Text(
                            "Related Tickets:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            resolution.relatedTicketIds.filter { it != ticket.id }.forEach { relatedId ->
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF4A3A5A), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        relatedId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFAA88FF)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Solution
                    Text(
                        "Solution:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        resolution.solution,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun PriorityBadge(priority: TicketPriority) {
    val (color, text) = when (priority) {
        TicketPriority.CRITICAL -> Color(0xFFFF4444) to "CRITICAL"
        TicketPriority.HIGH -> Color(0xFFFF8800) to "HIGH"
        TicketPriority.MEDIUM -> Color(0xFFFFBB00) to "MEDIUM"
        TicketPriority.LOW -> Color(0xFF44BB44) to "LOW"
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatusBadge(status: TicketStatus) {
    val (color, text) = when (status) {
        TicketStatus.OPEN -> Color(0xFF4488FF) to "OPEN"
        TicketStatus.IN_PROGRESS -> Color(0xFFFFAA00) to "IN PROGRESS"
        TicketStatus.RESOLVED -> Color(0xFF44BB44) to "RESOLVED"
        TicketStatus.CLOSED -> Color(0xFF888888) to "CLOSED"
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TypeBadge(type: TicketType) {
    val (color, text) = when (type) {
        TicketType.BUG -> Color(0xFFFF6666) to "Bug"
        TicketType.LOGIC_ERROR -> Color(0xFFAA66FF) to "Logic Error"
        TicketType.DESIGN_ISSUE -> Color(0xFF66AAFF) to "Design"
        TicketType.PERFORMANCE -> Color(0xFFFF66AA) to "Performance"
        TicketType.FEATURE_REQUEST -> Color(0xFF66FFAA) to "Feature"
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
