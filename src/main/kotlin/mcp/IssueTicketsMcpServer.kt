package mcp

import data.mcp.JsonRpcRequest
import data.mcp.JsonRpcResponse
import data.mcp.JsonRpcError
import data.mcp.McpServerInfo
import data.mcp.McpServerCapabilities
import data.mcp.McpInitializeResult
import data.mcp.McpTool
import data.mcp.McpToolListResult
import data.mcp.McpToolContent
import data.mcp.McpToolCallResult
import data.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * MCP Server for Issue Tickets Management
 * Provides tools to query and manage issue tickets
 */
class IssueTicketsMcpServer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serverInfo = McpServerInfo(
        name = "issue-tickets-server",
        version = "1.0.0"
    )

    private val capabilities = McpServerCapabilities()
    private val ticketStorage = IssueTicketStorage()

    private fun log(message: String) {
        System.err.println("[IssueTicketsMcpServer] $message")
        System.err.flush()
    }

    private fun sendResponse(response: JsonRpcResponse) {
        val jsonStr = json.encodeToString(response)
        println(jsonStr)
        System.out.flush()
        log("Sent response: $jsonStr")
    }

    private fun sendError(requestId: Int?, code: Int, message: String) {
        val response = JsonRpcResponse(
            id = requestId,
            error = JsonRpcError(code, message)
        )
        sendResponse(response)
    }

    private fun handleInitialize(requestId: Int?, params: JsonObject?): JsonRpcResponse {
        log("Handling initialize with params: $params")
        val result = McpInitializeResult(
            protocolVersion = "2024-11-05",
            serverInfo = serverInfo,
            capabilities = capabilities
        )
        return JsonRpcResponse(
            id = requestId,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun handleToolsList(requestId: Int?): JsonRpcResponse {
        log("Handling tools/list")

        val tools = listOf(
            McpTool(
                name = "list_tickets",
                description = "List all issue tickets, optionally filtered by status, type, or priority",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("status", buildJsonObject {
                            put("type", "string")
                            put("description", "Filter by status: OPEN, IN_PROGRESS, RESOLVED, CLOSED")
                        })
                        put("type", buildJsonObject {
                            put("type", "string")
                            put("description", "Filter by type: BUG, LOGIC_ERROR, DESIGN_ISSUE, PERFORMANCE, FEATURE_REQUEST")
                        })
                        put("priority", buildJsonObject {
                            put("type", "string")
                            put("description", "Filter by priority: LOW, MEDIUM, HIGH, CRITICAL")
                        })
                    })
                }
            ),
            McpTool(
                name = "get_ticket",
                description = "Get details of a specific ticket by ID",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("ticketId", buildJsonObject {
                            put("type", "string")
                            put("description", "The ticket ID (e.g., TICKET-001)")
                        })
                    })
                    put("required", buildJsonArray { add("ticketId") })
                }
            ),
            McpTool(
                name = "get_ticket_stats",
                description = "Get statistics about all tickets (counts by status, type, priority)",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                }
            )
        )

        val result = McpToolListResult(tools)
        return JsonRpcResponse(
            id = requestId,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun formatTicket(ticket: IssueTicket): String {
        return buildString {
            appendLine("ID: ${ticket.id}")
            appendLine("Title: ${ticket.title}")
            appendLine("Type: ${ticket.type}")
            appendLine("Priority: ${ticket.priority}")
            appendLine("Status: ${ticket.status}")
            appendLine("Reporter: ${ticket.reporter}")
            appendLine("Description: ${ticket.description}")
            appendLine("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(ticket.createdAt))}")
            appendLine("Updated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(ticket.updatedAt))}")
        }
    }

    private fun listTicketsTool(arguments: JsonObject): McpToolCallResult {
        val statusFilter = arguments["status"]?.jsonPrimitive?.contentOrNull?.let {
            try { TicketStatus.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }
        val typeFilter = arguments["type"]?.jsonPrimitive?.contentOrNull?.let {
            try { TicketType.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }
        val priorityFilter = arguments["priority"]?.jsonPrimitive?.contentOrNull?.let {
            try { TicketPriority.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }

        var tickets = ticketStorage.loadTickets()

        if (statusFilter != null) {
            tickets = tickets.filter { it.status == statusFilter }
        }
        if (typeFilter != null) {
            tickets = tickets.filter { it.type == typeFilter }
        }
        if (priorityFilter != null) {
            tickets = tickets.filter { it.priority == priorityFilter }
        }

        val output = if (tickets.isEmpty()) {
            "No tickets found matching the criteria."
        } else {
            buildString {
                appendLine("Found ${tickets.size} ticket(s):")
                appendLine()
                tickets.forEach { ticket ->
                    appendLine("---")
                    append(formatTicket(ticket))
                }
            }
        }

        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = output)),
            isError = false
        )
    }

    private fun getTicketTool(arguments: JsonObject): McpToolCallResult {
        val ticketId = arguments["ticketId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Error: ticketId is required")),
                isError = true
            )

        val ticket = ticketStorage.getTicketById(ticketId)
            ?: return McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = "Ticket not found: $ticketId")),
                isError = true
            )

        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = formatTicket(ticket))),
            isError = false
        )
    }

    private fun getTicketStatsTool(arguments: JsonObject): McpToolCallResult {
        val tickets = ticketStorage.loadTickets()

        val output = buildString {
            appendLine("Ticket Statistics:")
            appendLine()
            appendLine("Total tickets: ${tickets.size}")
            appendLine()
            appendLine("By Status:")
            TicketStatus.entries.forEach { status ->
                val count = tickets.count { it.status == status }
                appendLine("  $status: $count")
            }
            appendLine()
            appendLine("By Type:")
            TicketType.entries.forEach { type ->
                val count = tickets.count { it.type == type }
                appendLine("  $type: $count")
            }
            appendLine()
            appendLine("By Priority:")
            TicketPriority.entries.forEach { priority ->
                val count = tickets.count { it.priority == priority }
                appendLine("  $priority: $count")
            }
        }

        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = output)),
            isError = false
        )
    }

    private fun handleToolsCall(requestId: Int?, params: JsonObject?): JsonRpcResponse {
        if (params == null) {
            return JsonRpcResponse(
                id = requestId,
                error = JsonRpcError(-32602, "Invalid params")
            )
        }

        val toolName = params["name"]?.jsonPrimitive?.contentOrNull
        val arguments = params["arguments"]?.jsonObject ?: buildJsonObject { }

        log("Handling tools/call for $toolName with args: $arguments")

        val result = when (toolName) {
            "list_tickets" -> listTicketsTool(arguments)
            "get_ticket" -> getTicketTool(arguments)
            "get_ticket_stats" -> getTicketStatsTool(arguments)
            else -> {
                return JsonRpcResponse(
                    id = requestId,
                    error = JsonRpcError(-32601, "Unknown tool: $toolName")
                )
            }
        }

        return JsonRpcResponse(
            id = requestId,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun handleRequest(request: JsonRpcRequest) {
        val requestId = request.id
        val method = request.method
        val params = request.params

        log("Received request: $method")

        try {
            val response = when (method) {
                "initialize" -> handleInitialize(requestId, params)
                "tools/list" -> handleToolsList(requestId)
                "tools/call" -> handleToolsCall(requestId, params)
                else -> {
                    sendError(requestId, -32601, "Method not found: $method")
                    return
                }
            }

            sendResponse(response)
        } catch (e: Exception) {
            log("Error handling request: ${e.message}")
            e.printStackTrace(System.err)
            sendError(requestId, -32603, "Internal error: ${e.message}")
        }
    }

    fun run() {
        log("MCP Issue Tickets Server starting...")
        log("Kotlin version: ${KotlinVersion.CURRENT}")
        log("Waiting for requests on stdin...")

        try {
            while (true) {
                val line = readlnOrNull() ?: break
                val trimmedLine = line.trim()

                if (trimmedLine.isEmpty()) {
                    continue
                }

                try {
                    val request = json.decodeFromString<JsonRpcRequest>(trimmedLine)
                    handleRequest(request)
                } catch (e: SerializationException) {
                    log("Invalid JSON received: ${e.message}")
                    sendError(null, -32700, "Parse error")
                }
            }
        } catch (e: Exception) {
            log("Fatal error: ${e.message}")
            e.printStackTrace(System.err)
            throw e
        }

        log("Server shutting down...")
    }
}

fun main() {
    val server = IssueTicketsMcpServer()
    server.run()
}
