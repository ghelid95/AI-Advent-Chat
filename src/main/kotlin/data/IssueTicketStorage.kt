package data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class IssueTicket(
    val id: String,
    val title: String,
    val description: String,
    val type: TicketType,
    val priority: TicketPriority,
    val status: TicketStatus,
    val reporter: String,
    val createdAt: Long,
    val updatedAt: Long,
    val aiResolution: AIResolution? = null
)

@Serializable
data class AIResolution(
    val solution: String,
    val relatedTicketIds: List<String> = emptyList(),
    val toolsUsed: List<String> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class TicketType {
    BUG, LOGIC_ERROR, DESIGN_ISSUE, PERFORMANCE, FEATURE_REQUEST
}

@Serializable
enum class TicketPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
enum class TicketStatus {
    OPEN, IN_PROGRESS, RESOLVED, CLOSED
}

@Serializable
data class IssueTicketsData(
    val tickets: List<IssueTicket>
)

class IssueTicketStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val ticketsFile: File = run {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".ai-advent-chat")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        File(appDir, "issue-tickets.json")
    }

    fun loadTickets(): List<IssueTicket> {
        return try {
            if (!ticketsFile.exists()) {
                println("[IssueTickets] No tickets file found, creating sample data")
                val sampleTickets = createSampleTickets()
                saveTickets(sampleTickets)
                sampleTickets
            } else {
                val data = json.decodeFromString<IssueTicketsData>(ticketsFile.readText(Charsets.UTF_8))
                println("[IssueTickets] Loaded ${data.tickets.size} tickets")
                data.tickets
            }
        } catch (e: Exception) {
            println("[IssueTickets] Error loading tickets: ${e.message}")
            e.printStackTrace()
            createSampleTickets()
        }
    }

    fun saveTickets(tickets: List<IssueTicket>) {
        try {
            val data = IssueTicketsData(tickets)
            val jsonString = json.encodeToString(data)
            ticketsFile.writeText(jsonString, Charsets.UTF_8)
            println("[IssueTickets] Saved ${tickets.size} tickets")
        } catch (e: Exception) {
            println("[IssueTickets] Error saving tickets: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getTicketById(id: String): IssueTicket? {
        return loadTickets().find { it.id == id }
    }

    fun getTicketsByStatus(status: TicketStatus): List<IssueTicket> {
        return loadTickets().filter { it.status == status }
    }

    fun getTicketsByType(type: TicketType): List<IssueTicket> {
        return loadTickets().filter { it.type == type }
    }

    fun getTicketsByPriority(priority: TicketPriority): List<IssueTicket> {
        return loadTickets().filter { it.priority == priority }
    }

    fun updateTicketResolution(ticketId: String, resolution: AIResolution): Boolean {
        val tickets = loadTickets().toMutableList()
        val index = tickets.indexOfFirst { it.id == ticketId }
        if (index == -1) return false

        tickets[index] = tickets[index].copy(
            aiResolution = resolution,
            updatedAt = System.currentTimeMillis()
        )
        saveTickets(tickets)
        return true
    }

    private fun createSampleTickets(): List<IssueTicket> {
        val now = System.currentTimeMillis()
        return listOf(
            IssueTicket(
                id = "TICKET-001",
                title = "Chat messages not scrolling to bottom automatically",
                description = "When new messages arrive in the chat, the view doesn't automatically scroll to show the latest message. Users have to manually scroll down to see new responses.",
                type = TicketType.BUG,
                priority = TicketPriority.HIGH,
                status = TicketStatus.OPEN,
                reporter = "john.doe@example.com",
                createdAt = now - 86400000 * 5,
                updatedAt = now - 86400000 * 2
            ),
            IssueTicket(
                id = "TICKET-002",
                title = "Temperature slider doesn't show decimal values correctly",
                description = "The temperature slider in settings shows values like 0.30000001 instead of 0.3. This is a floating point precision display issue.",
                type = TicketType.DESIGN_ISSUE,
                priority = TicketPriority.LOW,
                status = TicketStatus.IN_PROGRESS,
                reporter = "jane.smith@example.com",
                createdAt = now - 86400000 * 10,
                updatedAt = now - 86400000 * 1
            ),
            IssueTicket(
                id = "TICKET-003",
                title = "Session stats calculation incorrect after compaction",
                description = "After compacting messages, the total token count in session stats doesn't include tokens from compacted messages, leading to incorrect cost calculations.",
                type = TicketType.LOGIC_ERROR,
                priority = TicketPriority.CRITICAL,
                status = TicketStatus.OPEN,
                reporter = "mike.wilson@example.com",
                createdAt = now - 86400000 * 3,
                updatedAt = now - 86400000 * 3
            ),
            IssueTicket(
                id = "TICKET-004",
                title = "MCP server connection timeout too short",
                description = "When MCP servers take longer than expected to respond, the connection times out. This happens especially with complex shell commands.",
                type = TicketType.BUG,
                priority = TicketPriority.MEDIUM,
                status = TicketStatus.RESOLVED,
                reporter = "sarah.johnson@example.com",
                createdAt = now - 86400000 * 15,
                updatedAt = now - 86400000 * 7
            ),
            IssueTicket(
                id = "TICKET-005",
                title = "Dark mode colors too low contrast",
                description = "Some text in the dark theme is hard to read due to low contrast between text and background colors. Especially visible in the settings dialog.",
                type = TicketType.DESIGN_ISSUE,
                priority = TicketPriority.MEDIUM,
                status = TicketStatus.OPEN,
                reporter = "alex.brown@example.com",
                createdAt = now - 86400000 * 8,
                updatedAt = now - 86400000 * 8
            ),
            IssueTicket(
                id = "TICKET-006",
                title = "Memory leak when switching between sessions",
                description = "Application memory usage increases each time a user switches between different chat sessions. The old session data seems to remain in memory.",
                type = TicketType.PERFORMANCE,
                priority = TicketPriority.HIGH,
                status = TicketStatus.IN_PROGRESS,
                reporter = "chris.taylor@example.com",
                createdAt = now - 86400000 * 12,
                updatedAt = now - 86400000 * 4
            ),
            IssueTicket(
                id = "TICKET-007",
                title = "Copy chat history includes internal formatting",
                description = "When copying chat history to clipboard, the copied text includes markdown formatting characters and internal message markers that shouldn't be visible to users.",
                type = TicketType.BUG,
                priority = TicketPriority.LOW,
                status = TicketStatus.CLOSED,
                reporter = "emma.davis@example.com",
                createdAt = now - 86400000 * 20,
                updatedAt = now - 86400000 * 14
            ),
            IssueTicket(
                id = "TICKET-008",
                title = "Embedding search returns irrelevant results",
                description = "The RAG embedding search sometimes returns chunks that are not semantically related to the query. The similarity threshold doesn't seem to filter properly.",
                type = TicketType.LOGIC_ERROR,
                priority = TicketPriority.HIGH,
                status = TicketStatus.OPEN,
                reporter = "david.martinez@example.com",
                createdAt = now - 86400000 * 6,
                updatedAt = now - 86400000 * 5
            ),
            IssueTicket(
                id = "TICKET-009",
                title = "Add keyboard shortcut for sending messages",
                description = "Users want to be able to send messages using Ctrl+Enter or just Enter key, instead of clicking the send button every time.",
                type = TicketType.FEATURE_REQUEST,
                priority = TicketPriority.MEDIUM,
                status = TicketStatus.RESOLVED,
                reporter = "lisa.anderson@example.com",
                createdAt = now - 86400000 * 25,
                updatedAt = now - 86400000 * 18
            ),
            IssueTicket(
                id = "TICKET-010",
                title = "Git diff tool shows empty output for binary files",
                description = "When running git diff on repositories with binary file changes, the tool returns empty output instead of indicating that the file is binary.",
                type = TicketType.BUG,
                priority = TicketPriority.LOW,
                status = TicketStatus.OPEN,
                reporter = "robert.garcia@example.com",
                createdAt = now - 86400000 * 2,
                updatedAt = now - 86400000 * 2
            ),
            IssueTicket(
                id = "TICKET-011",
                title = "Session name truncation cuts off important text",
                description = "Long session names are truncated in the sidebar without showing the full name on hover. Users can't distinguish between similarly named sessions.",
                type = TicketType.DESIGN_ISSUE,
                priority = TicketPriority.LOW,
                status = TicketStatus.OPEN,
                reporter = "jennifer.lee@example.com",
                createdAt = now - 86400000 * 4,
                updatedAt = now - 86400000 * 4
            ),
            IssueTicket(
                id = "TICKET-012",
                title = "API response time calculation includes network latency",
                description = "The displayed response time includes both API processing time and network latency, making it difficult to assess actual model performance.",
                type = TicketType.LOGIC_ERROR,
                priority = TicketPriority.LOW,
                status = TicketStatus.CLOSED,
                reporter = "william.chen@example.com",
                createdAt = now - 86400000 * 30,
                updatedAt = now - 86400000 * 22
            )
        )
    }
}
