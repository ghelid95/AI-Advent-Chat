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
import data.EmbeddingSearch
import data.OllamaClient
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * MCP Server for Project Documentation RAG
 * Provides tools for LLM to search project documentation
 */
class ProjectDocsMcpServer(
    private val workingDir: File
) {
    private val ollamaClient = OllamaClient()
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serverInfo = McpServerInfo(
        name = "project-docs-server",
        version = "1.0.0"
    )

    private val capabilities = McpServerCapabilities()

    private fun log(message: String) {
        System.err.println("[ProjectDocsMcpServer] $message")
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
                name = "search_project_docs",
                description = "Search project documentation (README, CONTRIBUTING, docs/) using semantic search. " +
                        "Use this to find information about project architecture, setup, guidelines, features, and other documentation. " +
                        "Returns the most relevant documentation chunks based on the query.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", "string")
                            put("description", "Search query to find relevant documentation (e.g., 'how to setup the project', 'architecture patterns', 'contributing guidelines')")
                        })
                        put("topK", buildJsonObject {
                            put("type", "integer")
                            put("description", "Number of results to return (default: 3, max: 10)")
                        })
                    })
                    put("required", buildJsonArray {
                        add("query")
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

    private fun searchProjectDocsTool(arguments: JsonObject): McpToolCallResult {
        return try {
            val query = arguments["query"]?.jsonPrimitive?.contentOrNull
                ?: return McpToolCallResult(
                    content = listOf(McpToolContent(
                        type = "text",
                        text = "Error: 'query' parameter is required"
                    )),
                    isError = true
                )

            val topK = arguments["topK"]?.jsonPrimitive?.intOrNull ?: 3
            val limitedTopK = minOf(topK, 10) // Cap at 10

            log("Searching project docs: query='$query', topK=$limitedTopK")

            // Get the project docs embedding file
            val embeddingFile = getProjectDocsEmbeddingFile()
            if (embeddingFile == null || !embeddingFile.exists()) {
                return McpToolCallResult(
                    content = listOf(McpToolContent(
                        type = "text",
                        text = "Project documentation not initialized. Enable 'Project Documentation' in settings to initialize."
                    )),
                    isError = true
                )
            }

            // Search using EmbeddingSearch
            val results = runBlocking {
                EmbeddingSearch.searchSimilarChunks(
                    query = query,
                    embeddingFile = embeddingFile,
                    ollamaClient = ollamaClient,
                    topK = limitedTopK,
                    threshold = 0.5f,
                    useMmr = true,
                    mmrLambda = 0.5f
                )
            }

            if (results.isEmpty()) {
                return McpToolCallResult(
                    content = listOf(McpToolContent(
                        type = "text",
                        text = "No relevant documentation found for query: '$query'"
                    )),
                    isError = false
                )
            }

            // Format results
            val formattedResults = buildString {
                appendLine("=== Project Documentation Search Results ===")
                appendLine()
                appendLine("Query: $query")
                appendLine("Found ${results.size} relevant section(s)")
                appendLine()

                results.forEachIndexed { index, result ->
                    appendLine("--- Result ${index + 1} (Similarity: ${"%.3f".format(result.similarity)}) ---")
                    appendLine(result.chunk.text)
                    appendLine()
                }

                appendLine("=== End of Search Results ===")
            }

            log("Found ${results.size} results")

            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = formattedResults
                )),
                isError = false
            )
        } catch (e: Exception) {
            log("Error searching project docs: ${e.message}")
            e.printStackTrace()
            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error searching project docs: ${e.message}"
                )),
                isError = true
            )
        }
    }

    private fun getProjectDocsEmbeddingFile(): File? {
        return try {
            // Hash working directory to get consistent filename
            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(workingDir.absolutePath.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(8)

            val homeDir = System.getProperty("user.home")
            val embeddingsDir = File(homeDir, ".ai-advent-chat/embeddings")
            File(embeddingsDir, "_project_docs_$hash.json")
        } catch (e: Exception) {
            log("Error getting project docs embedding file: ${e.message}")
            null
        }
    }

    private fun handleToolCall(requestId: Int?, params: JsonObject): JsonRpcResponse {
        log("Handling tools/call with params: $params")

        val toolName = params["name"]?.jsonPrimitive?.contentOrNull
        val arguments = params["arguments"]?.jsonObject ?: buildJsonObject { }

        val result = when (toolName) {
            "search_project_docs" -> searchProjectDocsTool(arguments)
            else -> McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Unknown tool: $toolName"
                )),
                isError = true
            )
        }

        return JsonRpcResponse(
            id = requestId,
            result = json.encodeToJsonElement(result)
        )
    }

    fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        log("Received request: ${request.method}")
        return when (request.method) {
            "initialize" -> handleInitialize(request.id, request.params as? JsonObject)
            "tools/list" -> handleToolsList(request.id)
            "tools/call" -> handleToolCall(request.id, request.params as JsonObject)
            else -> {
                log("Unknown method: ${request.method}")
                JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(-32601, "Method not found: ${request.method}")
                )
            }
        }
    }

    fun run() {
        log("MCP Project Documentation Server starting...")
        log("Working directory: ${workingDir.absolutePath}")
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
                    val response = handleRequest(request)
                    sendResponse(response)
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

fun main(args: Array<String>) {
    // Get working directory from arguments or environment variable or use current directory
    val workingDirPath = when {
        args.isNotEmpty() -> args[0]
        System.getenv("WORKING_DIR") != null -> System.getenv("WORKING_DIR")
        else -> System.getProperty("user.dir")
    }

    val workingDir = File(workingDirPath)
    if (!workingDir.exists() || !workingDir.isDirectory) {
        System.err.println("[ProjectDocsMcpServer] Error: Working directory does not exist or is not a directory: $workingDirPath")
        System.exit(1)
    }

    val server = ProjectDocsMcpServer(workingDir)
    server.run()
}
