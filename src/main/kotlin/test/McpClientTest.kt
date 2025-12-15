package test

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.BufferedWriter

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

@Serializable
data class InitializeParams(
    val protocolVersion: String = "2024-11-05",
    val capabilities: JsonObject = buildJsonObject {},
    val clientInfo: ClientInfo = ClientInfo()
)

@Serializable
data class ClientInfo(
    val name: String = "ai-advent-chat-test",
    val version: String = "1.0.0"
)

fun main() {
    println("=== MCP Weather Server Test ===\n")

    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    // Launch Java process
    println("1. Launching weather server...")
    val processBuilder = ProcessBuilder(
        "java",
        "-jar",
        "C:\\Users\\gheli\\IdeaProjects\\MCP_simple\\build\\libs\\MCP_simple-1.0-SNAPSHOT.jar"
    ).apply {
        redirectErrorStream(false)
    }

    val process = processBuilder.start()
    val writer = process.outputStream.bufferedWriter()
    val reader = process.inputStream.bufferedReader()
    val errorReader = process.errorStream.bufferedReader()

    // Start error stream reader in background
    Thread {
        try {
            errorReader.forEachLine { line ->
                println("[STDERR] $line")
            }
        } catch (e: Exception) {
            println("Error reader exception: ${e.message}")
        }
    }.start()

    var requestId = 0

    fun sendRequest(method: String, params: JsonObject? = null): JsonRpcResponse? {
        try {
            requestId++
            val request = JsonRpcRequest(id = requestId, method = method, params = params)
            val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)

            println("\n→ Sending request:")
            println(requestJson)

            writer.write(requestJson)
            writer.newLine()
            writer.flush()

            // Read response
            val responseLine = reader.readLine()
            if (responseLine == null) {
                println("✗ No response received (server closed connection)")
                return null
            }

            println("\n← Received response:")
            println(responseLine)

            val response = json.decodeFromString<JsonRpcResponse>(responseLine)

            if (response.error != null) {
                println("✗ Error: ${response.error.message}")
            } else {
                println("✓ Success!")
            }

            return response
        } catch (e: Exception) {
            println("✗ Exception: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    try {
        // Give server time to start
        Thread.sleep(500)

        // 2. Initialize
        println("\n2. Sending initialize request...")
        val initParams = InitializeParams()
        val initResponse = sendRequest("initialize", json.encodeToJsonElement(initParams).jsonObject)

        if (initResponse?.result != null) {
            val result = initResponse.result
            println("\n   Server Info:")
            println("   ${json.encodeToString(JsonElement.serializer(), result)}")
        }

        // 3. List tools
        println("\n3. Listing available tools...")
        val toolsResponse = sendRequest("tools/list")

        if (toolsResponse?.result != null) {
            val toolsList = toolsResponse.result
            println("\n   Available Tools:")
            println("   ${json.encodeToString(JsonElement.serializer(), toolsList)}")

            // Extract tool names
            try {
                val toolsArray = toolsList.jsonObject["tools"]?.jsonArray
                if (toolsArray != null && toolsArray.isNotEmpty()) {
                    val firstTool = toolsArray[0].jsonObject
                    val toolName = firstTool["name"]?.jsonPrimitive?.content

                    // 4. Call first tool
                    if (toolName != null) {
                        println("\n4. Calling tool: $toolName")

                        val toolCallParams = buildJsonObject {
                            put("name", toolName)
                            put("arguments", buildJsonObject {})
                        }

                        val toolResponse = sendRequest("tools/call", toolCallParams)

                        if (toolResponse?.result != null) {
                            println("\n   Tool Result:")
                            println("   ${json.encodeToString(JsonElement.serializer(), toolResponse.result)}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("✗ Error parsing tools: ${e.message}")
            }
        }

        println("\n\n=== Test Complete ===")

    } catch (e: Exception) {
        println("✗ Test failed: ${e.message}")
        e.printStackTrace()
    } finally {
        // Cleanup
        println("\nCleaning up...")
        writer.close()
        reader.close()
        errorReader.close()
        process.destroy()
        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        if (process.isAlive) {
            println("Force killing process...")
            process.destroyForcibly()
        }
        println("Done!")
    }
}
