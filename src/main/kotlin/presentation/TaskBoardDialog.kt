package presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBoardDialog(
    onDismiss: () -> Unit
) {
    val taskStorage = remember { TaskBoardStorage() }
    var tasks by remember { mutableStateOf(taskStorage.loadTasks()) }
    var selectedTask by remember { mutableStateOf<BoardTask?>(null) }
    var filterCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var filterPriority by remember { mutableStateOf<TaskPriority?>(null) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showEditTaskDialog by remember { mutableStateOf<BoardTask?>(null) }
    var viewMode by remember { mutableStateOf(TaskViewMode.KANBAN) }

    fun refreshTasks() {
        tasks = taskStorage.loadTasks()
    }

    val filteredTasks = remember(tasks, filterCategory, filterPriority) {
        tasks.filter { task ->
            (filterCategory == null || task.category == filterCategory) &&
            (filterPriority == null || task.priority == filterPriority)
        }
    }

    val stats = remember(tasks) { taskStorage.getTaskStats() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(1100.dp)
                .height(700.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Task Board",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "${stats.totalTasks} tasks | ${stats.completedThisWeek} completed this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // View mode toggle
                        IconButton(
                            onClick = { viewMode = if (viewMode == TaskViewMode.KANBAN) TaskViewMode.LIST else TaskViewMode.KANBAN }
                        ) {
                            Icon(
                                if (viewMode == TaskViewMode.KANBAN) Icons.Default.ViewList else Icons.Default.ViewKanban,
                                contentDescription = "Toggle view",
                                tint = Color.White
                            )
                        }

                        // Add task button
                        Button(
                            onClick = { showAddTaskDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Task")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filters Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category Filter
                    var categoryExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = filterCategory?.name?.replace("_", " ") ?: "All Categories",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Categories") },
                                onClick = {
                                    filterCategory = null
                                    categoryExpanded = false
                                }
                            )
                            TaskCategory.entries.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name.replace("_", " ")) },
                                    onClick = {
                                        filterCategory = category
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Priority Filter
                    var priorityExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = priorityExpanded,
                        onExpandedChange = { priorityExpanded = !priorityExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = filterPriority?.name ?: "All Priorities",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = priorityExpanded,
                            onDismissRequest = { priorityExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Priorities") },
                                onClick = {
                                    filterPriority = null
                                    priorityExpanded = false
                                }
                            )
                            TaskPriority.entries.forEach { priority ->
                                DropdownMenuItem(
                                    text = { Text(priority.name) },
                                    onClick = {
                                        filterPriority = priority
                                        priorityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content based on view mode
                when (viewMode) {
                    TaskViewMode.KANBAN -> {
                        KanbanBoard(
                            tasks = filteredTasks,
                            selectedTask = selectedTask,
                            onTaskClick = { task ->
                                selectedTask = if (selectedTask?.id == task.id) null else task
                            },
                            onStatusChange = { task, newStatus ->
                                taskStorage.updateTaskStatus(task.id, newStatus)
                                refreshTasks()
                            },
                            onEditClick = { showEditTaskDialog = it },
                            onDeleteClick = { task ->
                                taskStorage.deleteTask(task.id)
                                refreshTasks()
                                if (selectedTask?.id == task.id) selectedTask = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TaskViewMode.LIST -> {
                        TaskListView(
                            tasks = filteredTasks,
                            selectedTask = selectedTask,
                            onTaskClick = { task ->
                                selectedTask = if (selectedTask?.id == task.id) null else task
                            },
                            onEditClick = { showEditTaskDialog = it },
                            onDeleteClick = { task ->
                                taskStorage.deleteTask(task.id)
                                refreshTasks()
                                if (selectedTask?.id == task.id) selectedTask = null
                            },
                            modifier = Modifier.weight(1f)
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

    // Add Task Dialog
    if (showAddTaskDialog) {
        AddEditTaskDialog(
            task = null,
            onDismiss = { showAddTaskDialog = false },
            onSave = { title, description, category, priority, status, tags, estimatedHours ->
                taskStorage.createTask(
                    title = title,
                    description = description,
                    category = category,
                    priority = priority,
                    status = status,
                    tags = tags,
                    estimatedHours = estimatedHours
                )
                refreshTasks()
                showAddTaskDialog = false
            }
        )
    }

    // Edit Task Dialog
    showEditTaskDialog?.let { task ->
        AddEditTaskDialog(
            task = task,
            onDismiss = { showEditTaskDialog = null },
            onSave = { title, description, category, priority, status, tags, estimatedHours ->
                taskStorage.updateTask(task.id) { existingTask ->
                    existingTask.copy(
                        title = title,
                        description = description,
                        category = category,
                        priority = priority,
                        status = status,
                        tags = tags,
                        estimatedHours = estimatedHours
                    )
                }
                refreshTasks()
                showEditTaskDialog = null
            }
        )
    }
}

enum class TaskViewMode {
    KANBAN, LIST
}

@Composable
fun KanbanBoard(
    tasks: List<BoardTask>,
    selectedTask: BoardTask?,
    onTaskClick: (BoardTask) -> Unit,
    onStatusChange: (BoardTask, TaskStatus) -> Unit,
    onEditClick: (BoardTask) -> Unit,
    onDeleteClick: (BoardTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TaskStatus.entries.forEach { status ->
            val statusTasks = tasks.filter { it.status == status }
            KanbanColumn(
                status = status,
                tasks = statusTasks,
                selectedTask = selectedTask,
                onTaskClick = onTaskClick,
                onStatusChange = onStatusChange,
                onEditClick = onEditClick,
                onDeleteClick = onDeleteClick,
                modifier = Modifier.width(200.dp)
            )
        }
    }
}

@Composable
fun KanbanColumn(
    status: TaskStatus,
    tasks: List<BoardTask>,
    selectedTask: BoardTask?,
    onTaskClick: (BoardTask) -> Unit,
    onStatusChange: (BoardTask, TaskStatus) -> Unit,
    onEditClick: (BoardTask) -> Unit,
    onDeleteClick: (BoardTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val columnColor = when (status) {
        TaskStatus.BACKLOG -> Color(0xFF444444)
        TaskStatus.TODO -> Color(0xFF4488FF)
        TaskStatus.IN_PROGRESS -> Color(0xFFFFAA00)
        TaskStatus.REVIEW -> Color(0xFFAA66FF)
        TaskStatus.DONE -> Color(0xFF44BB44)
    }

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Column Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(columnColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    status.name.replace("_", " "),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = columnColor
                )
                Box(
                    modifier = Modifier
                        .background(columnColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${tasks.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tasks
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks.sortedByDescending { it.priority.ordinal }) { task ->
                    KanbanTaskCard(
                        task = task,
                        isSelected = selectedTask?.id == task.id,
                        onClick = { onTaskClick(task) },
                        onStatusChange = { newStatus -> onStatusChange(task, newStatus) },
                        onEditClick = { onEditClick(task) },
                        onDeleteClick = { onDeleteClick(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun KanbanTaskCard(
    task: BoardTask,
    isSelected: Boolean,
    onClick: () -> Unit,
    onStatusChange: (TaskStatus) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2A3A4A) else Color(0xFF252525)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Priority Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TaskPriorityBadge(task.priority)
                Text(
                    task.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = if (isSelected) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Category Badge
            TaskCategoryBadge(task.category)

            // Expanded content when selected
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )

                if (task.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        task.tags.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF3A4A5A), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                task.estimatedHours?.let { hours ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Est: ${hours}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Move to previous status
                    val prevStatus = TaskStatus.entries.getOrNull(task.status.ordinal - 1)
                    if (prevStatus != null) {
                        IconButton(
                            onClick = { onStatusChange(prevStatus) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Move to ${prevStatus.name}",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Move to next status
                    val nextStatus = TaskStatus.entries.getOrNull(task.status.ordinal + 1)
                    if (nextStatus != null) {
                        IconButton(
                            onClick = { onStatusChange(nextStatus) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Move to ${nextStatus.name}",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskListView(
    tasks: List<BoardTask>,
    selectedTask: BoardTask?,
    onTaskClick: (BoardTask) -> Unit,
    onEditClick: (BoardTask) -> Unit,
    onDeleteClick: (BoardTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tasks.sortedByDescending { it.priority.ordinal * 1000 + (Long.MAX_VALUE - it.createdAt) }) { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTaskClick(task) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedTask?.id == task.id) Color(0xFF2A3A4A) else Color(0xFF1E2830)
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                task.id,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            TaskPriorityBadge(task.priority)
                        }
                        TaskStatusBadge(task.status)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TaskCategoryBadge(task.category)
                        Text(
                            dateFormat.format(Date(task.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }

                    if (selectedTask?.id == task.id) {
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
                            task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        if (task.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                task.tags.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF3A4A5A), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("#$tag", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                            }
                        }

                        task.estimatedHours?.let { hours ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Estimated: ${hours} hours",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { onEditClick(task) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeleteClick(task) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF4444))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskDialog(
    task: BoardTask?,
    onDismiss: () -> Unit,
    onSave: (String, String, TaskCategory, TaskPriority, TaskStatus, List<String>, Int?) -> Unit
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var category by remember { mutableStateOf(task?.category ?: TaskCategory.FEATURE) }
    var priority by remember { mutableStateOf(task?.priority ?: TaskPriority.MEDIUM) }
    var status by remember { mutableStateOf(task?.status ?: TaskStatus.BACKLOG) }
    var tagsText by remember { mutableStateOf(task?.tags?.joinToString(", ") ?: "") }
    var estimatedHours by remember { mutableStateOf(task?.estimatedHours?.toString() ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (task == null) "Add New Task" else "Edit Task",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category dropdown
                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category.name.replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        TaskCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name.replace("_", " ")) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Priority dropdown
                var priorityExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = priorityExpanded,
                    onExpandedChange = { priorityExpanded = !priorityExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = priority.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false }
                    ) {
                        TaskPriority.entries.forEach { prio ->
                            DropdownMenuItem(
                                text = { Text(prio.name) },
                                onClick = {
                                    priority = prio
                                    priorityExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status dropdown
                var statusExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = !statusExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = status.name.replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        TaskStatus.entries.forEach { stat ->
                            DropdownMenuItem(
                                text = { Text(stat.name.replace("_", " ")) },
                                onClick = {
                                    status = stat
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tags
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Estimated hours
                OutlinedTextField(
                    value = estimatedHours,
                    onValueChange = { estimatedHours = it.filter { c -> c.isDigit() } },
                    label = { Text("Estimated Hours (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                            if (title.isNotBlank()) {
                                val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val hours = estimatedHours.toIntOrNull()
                                onSave(title, description, category, priority, status, tags, hours)
                            }
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text(if (task == null) "Add Task" else "Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun TaskPriorityBadge(priority: TaskPriority) {
    val (color, text) = when (priority) {
        TaskPriority.CRITICAL -> Color(0xFFFF4444) to "CRITICAL"
        TaskPriority.HIGH -> Color(0xFFFF8800) to "HIGH"
        TaskPriority.MEDIUM -> Color(0xFFFFBB00) to "MEDIUM"
        TaskPriority.LOW -> Color(0xFF44BB44) to "LOW"
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
fun TaskStatusBadge(status: TaskStatus) {
    val (color, text) = when (status) {
        TaskStatus.BACKLOG -> Color(0xFF888888) to "BACKLOG"
        TaskStatus.TODO -> Color(0xFF4488FF) to "TODO"
        TaskStatus.IN_PROGRESS -> Color(0xFFFFAA00) to "IN PROGRESS"
        TaskStatus.REVIEW -> Color(0xFFAA66FF) to "REVIEW"
        TaskStatus.DONE -> Color(0xFF44BB44) to "DONE"
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
fun TaskCategoryBadge(category: TaskCategory) {
    val (color, text) = when (category) {
        TaskCategory.FEATURE -> Color(0xFF66FFAA) to "Feature"
        TaskCategory.IMPROVEMENT -> Color(0xFF66AAFF) to "Improvement"
        TaskCategory.BUG_FIX -> Color(0xFFFF6666) to "Bug Fix"
        TaskCategory.REFACTORING -> Color(0xFFAA66FF) to "Refactoring"
        TaskCategory.DOCUMENTATION -> Color(0xFFFFAA66) to "Docs"
        TaskCategory.TESTING -> Color(0xFF66FFFF) to "Testing"
        TaskCategory.INFRASTRUCTURE -> Color(0xFFFF66AA) to "Infra"
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
