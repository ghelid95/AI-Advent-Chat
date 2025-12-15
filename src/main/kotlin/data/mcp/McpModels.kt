package data.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// MCP Server Configuration (stored globally)
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)

// JSON-RPC 2.0 Messages
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String,
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

// MCP Protocol Messages
@Serializable
data class McpInitializeParams(
    val protocolVersion: String = "2024-11-05",
    val capabilities: McpClientCapabilities = McpClientCapabilities(),
    val clientInfo: McpClientInfo = McpClientInfo()
)

@Serializable
data class McpClientCapabilities(
    val tools: Map<String, Boolean> = mapOf("listChanged" to true)
)

@Serializable
data class McpClientInfo(
    val name: String = "ai-advent-chat",
    val version: String = "1.0.0"
)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
    val capabilities: McpServerCapabilities,
    val serverInfo: McpServerInfo
)

@Serializable
data class McpServerCapabilities(
    val tools: JsonObject? = null
)

@Serializable
data class McpServerInfo(
    val name: String,
    val version: String
)

// Tool Definitions
@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject
)

@Serializable
data class McpToolListResult(
    val tools: List<McpTool>
)

// Tool Execution
@Serializable
data class McpToolCallParams(
    val name: String,
    val arguments: JsonObject? = null
)

@Serializable
data class McpToolCallResult(
    val content: List<McpToolContent>,
    @SerialName("isError") val isError: Boolean = false
)

@Serializable
data class McpToolContent(
    val type: String, // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)
