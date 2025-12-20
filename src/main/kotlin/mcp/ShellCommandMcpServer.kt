package mcp

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MCP Server for Shell Command Execution
 * Provides command line access tools for LLM client
 */

// JSON-RPC Models
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)

// MCP Protocol Models
@Serializable
data class McpServerInfo(
    val name: String,
    val version: String
)

@Serializable
data class McpCapabilities(
    val tools: JsonObject = buildJsonObject { }
)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String = "2024-11-05",
    val serverInfo: McpServerInfo,
    val capabilities: McpCapabilities
)

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

@Serializable
data class McpToolContent(
    val type: String,
    val text: String? = null
)

@Serializable
data class McpToolCallResult(
    val content: List<McpToolContent>,
    @SerialName("isError") val isError: Boolean = false
)

class ShellCommandMcpServer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serverInfo = McpServerInfo(
        name = "shell-command-server",
        version = "1.0.0"
    )

    private val capabilities = McpCapabilities()

    private fun log(message: String) {
        System.err.println("[ShellCommandMcpServer] $message")
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
                name = "execute_command",
                description = "Execute a shell command and return its output. Use with caution as this runs commands directly on the system.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("command", buildJsonObject {
                            put("type", "string")
                            put("description", "The shell command to execute")
                        })
                        put("workingDir", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional working directory for the command")
                        })
                        put("timeout", buildJsonObject {
                            put("type", "number")
                            put("description", "Optional timeout in seconds (default: 30)")
                        })
                    })
                    put("required", buildJsonArray {
                        add("command")
                    })
                }
            ),
            McpTool(
                name = "read_file",
                description = "Read the contents of a file from the filesystem",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The file path to read")
                        })
                    })
                    put("required", buildJsonArray {
                        add("path")
                    })
                }
            ),
            McpTool(
                name = "write_file",
                description = "Write content to a file on the filesystem",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The file path to write to")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "The content to write to the file")
                        })
                    })
                    put("required", buildJsonArray {
                        add("path")
                        add("content")
                    })
                }
            ),
            McpTool(
                name = "list_directory",
                description = "List files and directories in a given path",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The directory path to list (default: current directory)")
                        })
                    })
                }
            )
        )

        val result = McpToolListResult(tools)
        return JsonRpcResponse(
            id = requestId,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun executeCommandTool(arguments: JsonObject): McpToolCallResult {
        val command = arguments["command"]?.jsonPrimitive?.contentOrNull
        val workingDir = arguments["workingDir"]?.jsonPrimitive?.contentOrNull
        val timeout = arguments["timeout"]?.jsonPrimitive?.longOrNull ?: 30L

        if (command == null) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: 'command' argument is required"
                )),
                isError = true
            )
        }

        return try {
            log("Executing command: $command")

            val processBuilder = ProcessBuilder()
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                processBuilder.command("cmd.exe", "/c", command)
            } else {
                processBuilder.command("sh", "-c", command)
            }

            workingDir?.let {
                processBuilder.directory(File(it))
            }

            processBuilder.redirectErrorStream(false)

            val process = processBuilder.start()
            val completed = process.waitFor(timeout, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return McpToolCallResult(
                    content = listOf(McpToolContent(
                        type = "text",
                        text = "Command timed out after $timeout seconds"
                    )),
                    isError = true
                )
            }

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            val output = buildString {
                append("Exit Code: $exitCode\n\n")
                if (stdout.isNotEmpty()) {
                    append("STDOUT:\n$stdout\n")
                }
                if (stderr.isNotEmpty()) {
                    append("\nSTDERR:\n$stderr")
                }
            }

            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = output
                )),
                isError = exitCode != 0
            )
        } catch (e: Exception) {
            log("Error executing command: ${e.message}")
            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error executing command: ${e.message}"
                )),
                isError = true
            )
        }
    }

    private fun readFileTool(arguments: JsonObject): McpToolCallResult {
        val path = arguments["path"]?.jsonPrimitive?.contentOrNull

        if (path == null) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: 'path' argument is required"
                )),
                isError = true
            )
        }

        return try {
            val file = File(path)
            val content = file.readText()

            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = content
                )),
                isError = false
            )
        } catch (e: Exception) {
            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error reading file: ${e.message}"
                )),
                isError = true
            )
        }
    }

    private fun writeFileTool(arguments: JsonObject): McpToolCallResult {
        val path = arguments["path"]?.jsonPrimitive?.contentOrNull
        val content = arguments["content"]?.jsonPrimitive?.contentOrNull

        if (path == null) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: 'path' argument is required"
                )),
                isError = true
            )
        }

        if (content == null) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: 'content' argument is required"
                )),
                isError = true
            )
        }

        return try {
            val file = File(path)
            file.writeText(content)

            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Successfully wrote ${content.length} characters to $path"
                )),
                isError = false
            )
        } catch (e: Exception) {
            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error writing file: ${e.message}"
                )),
                isError = true
            )
        }
    }

    private fun listDirectoryTool(arguments: JsonObject): McpToolCallResult {
        val path = arguments["path"]?.jsonPrimitive?.contentOrNull ?: "."

        return try {
            val dir = File(path)
            val items = dir.listFiles()?.sortedBy { it.name } ?: emptyList()

            val resultLines = items.map { file ->
                if (file.isDirectory) {
                    "[DIR]  ${file.name}"
                } else {
                    "[FILE] ${file.name} (${file.length()} bytes)"
                }
            }

            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = resultLines.joinToString("\n")
                )),
                isError = false
            )
        } catch (e: Exception) {
            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error listing directory: ${e.message}"
                )),
                isError = true
            )
        }
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
            "execute_command" -> executeCommandTool(arguments)
            "read_file" -> readFileTool(arguments)
            "write_file" -> writeFileTool(arguments)
            "list_directory" -> listDirectoryTool(arguments)
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
        log("MCP Shell Command Server starting...")
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
    val server = ShellCommandMcpServer()
    server.run()
}