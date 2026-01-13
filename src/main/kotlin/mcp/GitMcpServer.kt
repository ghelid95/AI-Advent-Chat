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
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MCP Server for Git Repository Operations
 * Provides git tools for LLM to interact with git repositories
 */
class GitMcpServer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serverInfo = McpServerInfo(
        name = "git-server",
        version = "1.0.0"
    )

    private val capabilities = McpServerCapabilities()

    private fun log(message: String) {
        System.err.println("[GitMcpServer] $message")
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
                name = "git_status",
                description = "Get the status of a git repository showing modified, staged, and untracked files",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("workingDir", buildJsonObject {
                            put("type", "string")
                            put("description", "Path to the git repository (default: current directory)")
                        })
                    })
                }
            ),
            McpTool(
                name = "git_diff",
                description = "Show uncommitted changes (diff between working directory and HEAD)",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("workingDir", buildJsonObject {
                            put("type", "string")
                            put("description", "Path to the git repository (default: current directory)")
                        })
                        put("file", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional: specific file to show diff for")
                        })
                    })
                }
            ),
            McpTool(
                name = "git_log",
                description = "Show commit history with author, date, and message",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("workingDir", buildJsonObject {
                            put("type", "string")
                            put("description", "Path to the git repository (default: current directory)")
                        })
                        put("maxCount", buildJsonObject {
                            put("type", "number")
                            put("description", "Maximum number of commits to show (default: 10)")
                        })
                    })
                }
            ),
            McpTool(
                name = "git_branch",
                description = "Show current branch name and remote URL",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("workingDir", buildJsonObject {
                            put("type", "string")
                            put("description", "Path to the git repository (default: current directory)")
                        })
                    })
                }
            ),
            McpTool(
                name = "git_show",
                description = "Show details of a specific commit including changes",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("workingDir", buildJsonObject {
                            put("type", "string")
                            put("description", "Path to the git repository (default: current directory)")
                        })
                        put("commitHash", buildJsonObject {
                            put("type", "string")
                            put("description", "Commit hash to show (default: HEAD)")
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

    /**
     * Execute a git command and return result
     */
    private fun executeGitCommand(workingDir: File, args: List<String>, timeout: Long = 10): McpToolCallResult {
        return try {
            val command = mutableListOf("git")
            command.addAll(args)

            log("Executing: ${command.joinToString(" ")} in ${workingDir.absolutePath}")

            val process = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(timeout, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return McpToolCallResult(
                    content = listOf(McpToolContent(
                        type = "text",
                        text = "Git command timed out after $timeout seconds"
                    )),
                    isError = true
                )
            }

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode != 0) {
                return McpToolCallResult(
                    content = listOf(McpToolContent(
                        type = "text",
                        text = "Git command failed:\n$output"
                    )),
                    isError = true
                )
            }

            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = output
                )),
                isError = false
            )
        } catch (e: Exception) {
            log("Error executing git command: ${e.message}")
            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: ${e.message}"
                )),
                isError = true
            )
        }
    }

    private fun gitStatusTool(arguments: JsonObject): McpToolCallResult {
        val workingDirPath = arguments["workingDir"]?.jsonPrimitive?.contentOrNull ?: "."
        val workingDir = File(workingDirPath)

        if (!workingDir.exists() || !workingDir.isDirectory) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: Directory does not exist: $workingDirPath"
                )),
                isError = true
            )
        }

        // Check if it's a git repository
        val isRepoResult = executeGitCommand(workingDir, listOf("rev-parse", "--is-inside-work-tree"))
        if (isRepoResult.isError) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: Not a git repository"
                )),
                isError = true
            )
        }

        // Get status
        val statusResult = executeGitCommand(workingDir, listOf("status", "--porcelain", "--branch"))
        if (statusResult.isError) {
            return statusResult
        }

        // Get current branch
        val branchResult = executeGitCommand(workingDir, listOf("rev-parse", "--abbrev-ref", "HEAD"))
        val branch = if (!branchResult.isError) {
            branchResult.content.getOrNull(0)?.text?.trim() ?: "unknown"
        } else {
            "unknown"
        }

        // Format output
        val statusOutput = statusResult.content.getOrNull(0)?.text ?: ""
        val output = buildString {
            appendLine("Repository: ${workingDir.absolutePath}")
            appendLine("Branch: $branch")
            appendLine()
            if (statusOutput.trim().isEmpty() || statusOutput.trim() == "## $branch") {
                appendLine("Working directory is clean")
            } else {
                appendLine("Status:")
                appendLine(statusOutput)
            }
        }

        return McpToolCallResult(
            content = listOf(McpToolContent(
                type = "text",
                text = output
            )),
            isError = false
        )
    }

    private fun gitDiffTool(arguments: JsonObject): McpToolCallResult {
        val workingDirPath = arguments["workingDir"]?.jsonPrimitive?.contentOrNull ?: "."
        val file = arguments["file"]?.jsonPrimitive?.contentOrNull
        val workingDir = File(workingDirPath)

        if (!workingDir.exists() || !workingDir.isDirectory) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: Directory does not exist: $workingDirPath"
                )),
                isError = true
            )
        }

        val args = mutableListOf("diff", "HEAD")
        if (file != null) {
            args.add("--")
            args.add(file)
        }

        val result = executeGitCommand(workingDir, args, timeout = 30)
        if (result.isError) {
            return result
        }

        val diffOutput = result.content.getOrNull(0)?.text ?: ""
        val output = if (diffOutput.trim().isEmpty()) {
            "No changes to show"
        } else {
            diffOutput
        }

        return McpToolCallResult(
            content = listOf(McpToolContent(
                type = "text",
                text = output
            )),
            isError = false
        )
    }

    private fun gitLogTool(arguments: JsonObject): McpToolCallResult {
        val workingDirPath = arguments["workingDir"]?.jsonPrimitive?.contentOrNull ?: "."
        val maxCount = arguments["maxCount"]?.jsonPrimitive?.intOrNull ?: 10
        val workingDir = File(workingDirPath)

        if (!workingDir.exists() || !workingDir.isDirectory) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: Directory does not exist: $workingDirPath"
                )),
                isError = true
            )
        }

        val args = listOf(
            "log",
            "--oneline",
            "-n", maxCount.toString(),
            "--format=%h - %s (%an, %ar)"
        )

        return executeGitCommand(workingDir, args, timeout = 15)
    }

    private fun gitBranchTool(arguments: JsonObject): McpToolCallResult {
        val workingDirPath = arguments["workingDir"]?.jsonPrimitive?.contentOrNull ?: "."
        val workingDir = File(workingDirPath)

        if (!workingDir.exists() || !workingDir.isDirectory) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: Directory does not exist: $workingDirPath"
                )),
                isError = true
            )
        }

        // Get current branch
        val branchResult = executeGitCommand(workingDir, listOf("rev-parse", "--abbrev-ref", "HEAD"))
        if (branchResult.isError) {
            return branchResult
        }

        // Get remote URL
        val remoteResult = executeGitCommand(workingDir, listOf("remote", "get-url", "origin"))
        val remoteUrl = if (!remoteResult.isError) {
            remoteResult.content.getOrNull(0)?.text?.trim() ?: "No remote configured"
        } else {
            "No remote configured"
        }

        val output = buildString {
            appendLine("Current Branch: ${branchResult.content.getOrNull(0)?.text?.trim() ?: "unknown"}")
            appendLine("Remote URL: $remoteUrl")
        }

        return McpToolCallResult(
            content = listOf(McpToolContent(
                type = "text",
                text = output
            )),
            isError = false
        )
    }

    private fun gitShowTool(arguments: JsonObject): McpToolCallResult {
        val workingDirPath = arguments["workingDir"]?.jsonPrimitive?.contentOrNull ?: "."
        val commitHash = arguments["commitHash"]?.jsonPrimitive?.contentOrNull ?: "HEAD"
        val workingDir = File(workingDirPath)

        if (!workingDir.exists() || !workingDir.isDirectory) {
            return McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Error: Directory does not exist: $workingDirPath"
                )),
                isError = true
            )
        }

        val args = listOf("show", commitHash, "--stat")
        return executeGitCommand(workingDir, args, timeout = 15)
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
            "git_status" -> gitStatusTool(arguments)
            "git_diff" -> gitDiffTool(arguments)
            "git_log" -> gitLogTool(arguments)
            "git_branch" -> gitBranchTool(arguments)
            "git_show" -> gitShowTool(arguments)
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
        log("MCP Git Server starting...")
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
    val server = GitMcpServer()
    server.run()
}
