package data.mcp

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.atomic.AtomicInteger

// Manages a single MCP server process
class McpServer(
    val config: McpServerConfig,
    private val scope: CoroutineScope
) {
    private var process: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private var stdoutReader: BufferedReader? = null
    private var stderrReader: BufferedReader? = null
    private val requestIdCounter = AtomicInteger(0)
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<JsonRpcResponse>>()
    private val mutex = Mutex()

    private var readJob: Job? = null
    private var errorJob: Job? = null
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    var serverInfo: McpServerInfo? = null
        private set
    var capabilities: McpServerCapabilities? = null
        private set
    var tools: List<McpTool> = emptyList()
        private set

    enum class State { STOPPED, STARTING, READY, ERROR }
    var state: State = State.STOPPED
        private set
    var errorMessage: String? = null
        private set

    // Launch server process
    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            state = State.STARTING
            errorMessage = null

            println("[MCP] Starting server: ${config.name}")
            println("[MCP] Command: ${config.command} ${config.args.joinToString(" ")}")

            // Build process
            val processBuilder = ProcessBuilder(
                listOf(config.command) + config.args
            ).apply {
                // Set environment variables
                environment().putAll(config.env)
                redirectErrorStream(false)
            }

            process = processBuilder.start()
            stdinWriter = process!!.outputStream.bufferedWriter()
            stdoutReader = process!!.inputStream.bufferedReader()
            stderrReader = process!!.errorStream.bufferedReader()

            // Start reading stdout in background
            readJob = scope.launch { readStdoutLoop() }

            // Start reading stderr in background
            errorJob = scope.launch { readStderrLoop() }

            // Initialize handshake
            val initResult = initialize()
            initResult.onSuccess { result ->
                serverInfo = result.serverInfo
                capabilities = result.capabilities
                state = State.READY
                println("[MCP] Server ready: ${result.serverInfo.name} v${result.serverInfo.version}")

                // List tools
                listTools().onSuccess { toolList ->
                    tools = toolList.tools
                    println("[MCP] Loaded ${tools.size} tools from ${config.name}")
                    tools.forEach { tool ->
                        println("[MCP]   - ${tool.name}: ${tool.description ?: "No description"}")
                    }
                }
            }.onFailure { error ->
                state = State.ERROR
                errorMessage = error.message
                println("[MCP] Failed to initialize ${config.name}: ${error.message}")
                stop()
            }

            initResult.map { }
        } catch (e: Exception) {
            state = State.ERROR
            errorMessage = e.message
            println("[MCP] Exception starting ${config.name}: ${e.message}")
            e.printStackTrace()
            stop()
            Result.failure(e)
        }
    }

    // Initialize handshake
    private suspend fun initialize(): Result<McpInitializeResult> {
        val params = McpInitializeParams()
        val response = sendRequest("initialize", json.encodeToJsonElement(params).jsonObject)

        return response.mapCatching { resp ->
            resp.error?.let { throw Exception("MCP initialize error: ${it.message}") }
            json.decodeFromJsonElement<McpInitializeResult>(resp.result!!)
        }
    }

    // List available tools
    private suspend fun listTools(): Result<McpToolListResult> {
        val response = sendRequest("tools/list", null)
        return response.mapCatching { resp ->
            resp.error?.let { throw Exception("MCP tools/list error: ${it.message}") }
            json.decodeFromJsonElement<McpToolListResult>(resp.result!!)
        }
    }

    // Call a tool
    suspend fun callTool(name: String, arguments: JsonObject?): Result<McpToolCallResult> {
        if (state != State.READY) {
            return Result.failure(Exception("MCP server not ready (state: $state)"))
        }

        val params = McpToolCallParams(name, arguments)
        val response = sendRequest("tools/call", json.encodeToJsonElement(params).jsonObject)

        return response.mapCatching { resp ->
            resp.error?.let { throw Exception("MCP tool call error: ${it.message}") }
            json.decodeFromJsonElement<McpToolCallResult>(resp.result!!)
        }
    }

    // Send JSON-RPC request
    private suspend fun sendRequest(method: String, params: JsonObject?): Result<JsonRpcResponse> = mutex.withLock {
        try {
            val id = requestIdCounter.incrementAndGet()
            val request = JsonRpcRequest(id = id, method = method, params = params)
            val deferred = CompletableDeferred<JsonRpcResponse>()

            pendingRequests[id] = deferred

            // Send request
            val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)
            stdinWriter?.write(requestJson)
            stdinWriter?.newLine()
            stdinWriter?.flush()

            // Wait for response with timeout
            withTimeout(30000) {
                val response = deferred.await()
                Result.success(response)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Request timeout after 30s"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Read stdout loop
    private suspend fun readStdoutLoop() = withContext(Dispatchers.IO) {
        try {
            while (isActive) {
                val line = stdoutReader?.readLine() ?: break
                if (line.isBlank()) continue

                try {
                    val response = json.decodeFromString<JsonRpcResponse>(line)
                    response.id?.let { id ->
                        pendingRequests.remove(id)?.complete(response)
                    }
                } catch (e: Exception) {
                    println("[MCP] Failed to parse response: $line")
                    println("[MCP] Error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            if (state == State.READY || state == State.STARTING) {
                println("[MCP] Stdout reader error for ${config.name}: ${e.message}")
                state = State.ERROR
                errorMessage = e.message
            }
        }
    }

    // Read stderr loop
    private suspend fun readStderrLoop() = withContext(Dispatchers.IO) {
        try {
            while (isActive) {
                val line = stderrReader?.readLine() ?: break
                if (line.isNotBlank()) {
                    println("[MCP STDERR ${config.name}] $line")
                }
            }
        } catch (e: Exception) {
            // Stderr errors are not critical
        }
    }

    // Stop server
    suspend fun stop() = withContext(Dispatchers.IO) {
        println("[MCP] Stopping server: ${config.name}")
        readJob?.cancel()
        errorJob?.cancel()
        try {
            stdinWriter?.close()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            stdoutReader?.close()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            stderrReader?.close()
        } catch (e: Exception) {
            // Ignore
        }
        process?.destroy()
        process?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        if (process?.isAlive == true) {
            println("[MCP] Force killing process for ${config.name}")
            process?.destroyForcibly()
        }
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        state = State.STOPPED
    }
}

// Manages all MCP servers
class McpServerManager(private val scope: CoroutineScope) {
    private val servers = mutableMapOf<String, McpServer>()
    private val mutex = Mutex()

    // Start all enabled servers
    suspend fun startServers(configs: List<McpServerConfig>) = mutex.withLock {
        // Stop existing servers
        servers.values.forEach { it.stop() }
        servers.clear()

        // Start new servers and wait for all to complete initialization
        val jobs = configs.filter { it.enabled }.map { config ->
            val server = McpServer(config, scope)
            servers[config.id] = server
            scope.async {
                server.start()
            }
        }

        // Wait for all servers to finish initialization
        jobs.forEach { job ->
            try {
                job.await()
            } catch (e: Exception) {
                println("[MCP] Server initialization failed: ${e.message}")
            }
        }

        println("[MCP] All servers initialized. Ready servers: ${getReadyServers().size}")
    }

    // Get all ready servers
    fun getReadyServers(): List<McpServer> {
        return servers.values.filter { it.state == McpServer.State.READY }
    }

    // Get all servers with their states
    fun getAllServers(): List<McpServer> {
        return servers.values.toList()
    }

    // Get all tools from all servers
    fun getAllTools(): List<Pair<String, McpTool>> {
        return getReadyServers().flatMap { server ->
            server.tools.map { server.config.id to it }
        }
    }

    // Call a tool on a specific server
    suspend fun callTool(serverId: String, toolName: String, arguments: JsonObject?): Result<McpToolCallResult> {
        val server = servers[serverId] ?: return Result.failure(Exception("Server not found: $serverId"))
        return server.callTool(toolName, arguments)
    }

    // Stop all servers
    suspend fun stopAll() = mutex.withLock {
        println("[MCP] Stopping all servers...")
        servers.values.forEach { it.stop() }
        servers.clear()
    }
}
