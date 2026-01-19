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
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * MCP Server for GitHub Operations
 * Provides GitHub tools for LLM to interact with GitHub repositories,
 * especially for managing releases and uploading assets.
 *
 * Requires GITHUB_TOKEN environment variable for authentication.
 */
class GitHubMcpServer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serverInfo = McpServerInfo(
        name = "github-server",
        version = "1.0.0"
    )

    private val capabilities = McpServerCapabilities()

    // GitHub API base URL
    private val apiBaseUrl = "https://api.github.com"

    // Get token from environment
    private val githubToken: String? = System.getenv("GITHUB_TOKEN")

    // Safe JSON accessors to handle JsonNull
    private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
        return when (this) {
            is JsonObject -> this
            else -> null
        }
    }

    private fun JsonElement?.asStringOrNull(): String? {
        return when (this) {
            is JsonPrimitive -> this.contentOrNull
            else -> null
        }
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        return when (this) {
            is JsonPrimitive -> this.intOrNull
            else -> null
        }
    }

    private fun JsonElement?.asLongOrNull(): Long? {
        return when (this) {
            is JsonPrimitive -> this.longOrNull
            else -> null
        }
    }

    private fun JsonElement?.asBooleanOrNull(): Boolean? {
        return when (this) {
            is JsonPrimitive -> this.booleanOrNull
            else -> null
        }
    }

    private fun JsonElement?.asJsonArrayOrNull(): JsonArray? {
        return when (this) {
            is JsonArray -> this
            else -> null
        }
    }

    private fun log(message: String) {
        System.err.println("[GitHubMcpServer] $message")
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

        if (githubToken.isNullOrEmpty()) {
            log("WARNING: GITHUB_TOKEN environment variable is not set!")
        } else {
            log("GitHub token found (length: ${githubToken.length})")
        }

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
                name = "github_get_repo_info",
                description = "Get information about a GitHub repository including description, stars, forks, and default branch",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (username or organization)")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name")
                        })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("owner"), JsonPrimitive("repo"))))
                }
            ),
            McpTool(
                name = "github_list_releases",
                description = "List all releases for a GitHub repository",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (username or organization)")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name")
                        })
                        put("per_page", buildJsonObject {
                            put("type", "number")
                            put("description", "Number of releases per page (default: 10, max: 100)")
                        })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("owner"), JsonPrimitive("repo"))))
                }
            ),
            McpTool(
                name = "github_get_release",
                description = "Get details of a specific release by tag name or release ID",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (username or organization)")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name")
                        })
                        put("tag", buildJsonObject {
                            put("type", "string")
                            put("description", "Tag name of the release (e.g., 'v1.0.0')")
                        })
                        put("release_id", buildJsonObject {
                            put("type", "number")
                            put("description", "Release ID (alternative to tag)")
                        })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("owner"), JsonPrimitive("repo"))))
                }
            ),
            McpTool(
                name = "github_create_release",
                description = "Create a new release on GitHub. This creates a release with optional release notes.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (username or organization)")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name")
                        })
                        put("tag_name", buildJsonObject {
                            put("type", "string")
                            put("description", "The name of the tag for the release (e.g., 'v1.0.0')")
                        })
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "The name/title of the release")
                        })
                        put("body", buildJsonObject {
                            put("type", "string")
                            put("description", "Release notes/description in markdown format")
                        })
                        put("draft", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Create as draft release (default: false)")
                        })
                        put("prerelease", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Mark as pre-release (default: false)")
                        })
                        put("target_commitish", buildJsonObject {
                            put("type", "string")
                            put("description", "Branch or commit SHA to tag (default: default branch)")
                        })
                        put("generate_release_notes", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Auto-generate release notes from commits (default: false)")
                        })
                    })
                    put("required", JsonArray(listOf(
                        JsonPrimitive("owner"),
                        JsonPrimitive("repo"),
                        JsonPrimitive("tag_name")
                    )))
                }
            ),
            McpTool(
                name = "github_upload_release_asset",
                description = "Upload a file as an asset to an existing GitHub release. The file must exist on the local filesystem.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (username or organization)")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name")
                        })
                        put("release_id", buildJsonObject {
                            put("type", "number")
                            put("description", "Release ID to upload asset to")
                        })
                        put("tag", buildJsonObject {
                            put("type", "string")
                            put("description", "Tag name of the release (alternative to release_id)")
                        })
                        put("file_path", buildJsonObject {
                            put("type", "string")
                            put("description", "Local path to the file to upload")
                        })
                        put("asset_name", buildJsonObject {
                            put("type", "string")
                            put("description", "Name for the asset (default: original filename)")
                        })
                        put("content_type", buildJsonObject {
                            put("type", "string")
                            put("description", "MIME type of the file (default: auto-detect)")
                        })
                    })
                    put("required", JsonArray(listOf(
                        JsonPrimitive("owner"),
                        JsonPrimitive("repo"),
                        JsonPrimitive("file_path")
                    )))
                }
            ),
            McpTool(
                name = "github_delete_release",
                description = "Delete a release from GitHub repository",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (username or organization)")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name")
                        })
                        put("release_id", buildJsonObject {
                            put("type", "number")
                            put("description", "Release ID to delete")
                        })
                        put("tag", buildJsonObject {
                            put("type", "string")
                            put("description", "Tag name of the release (alternative to release_id)")
                        })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("owner"), JsonPrimitive("repo"))))
                }
            ),
            McpTool(
                name = "github_list_release_assets",
                description = "List all assets attached to a release",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (username or organization)")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name")
                        })
                        put("release_id", buildJsonObject {
                            put("type", "number")
                            put("description", "Release ID")
                        })
                        put("tag", buildJsonObject {
                            put("type", "string")
                            put("description", "Tag name of the release (alternative to release_id)")
                        })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("owner"), JsonPrimitive("repo"))))
                }
            ),
            McpTool(
                name = "github_delete_release_asset",
                description = "Delete an asset from a release",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (username or organization)")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name")
                        })
                        put("asset_id", buildJsonObject {
                            put("type", "number")
                            put("description", "Asset ID to delete")
                        })
                    })
                    put("required", JsonArray(listOf(
                        JsonPrimitive("owner"),
                        JsonPrimitive("repo"),
                        JsonPrimitive("asset_id")
                    )))
                }
            ),
            McpTool(
                name = "github_list_commits",
                description = "List commit history for a GitHub repository. Can filter by branch, path, author, and date range.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("owner", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository owner (username or organization)")
                        })
                        put("repo", buildJsonObject {
                            put("type", "string")
                            put("description", "Repository name")
                        })
                        put("branch", buildJsonObject {
                            put("type", "string")
                            put("description", "Branch name or commit SHA (default: default branch)")
                        })
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Only commits containing this file path")
                        })
                        put("author", buildJsonObject {
                            put("type", "string")
                            put("description", "GitHub username or email to filter commits by author")
                        })
                        put("since", buildJsonObject {
                            put("type", "string")
                            put("description", "Only commits after this date (ISO 8601 format: YYYY-MM-DDTHH:MM:SSZ)")
                        })
                        put("until", buildJsonObject {
                            put("type", "string")
                            put("description", "Only commits before this date (ISO 8601 format: YYYY-MM-DDTHH:MM:SSZ)")
                        })
                        put("per_page", buildJsonObject {
                            put("type", "number")
                            put("description", "Number of commits to return (default: 20, max: 100)")
                        })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("owner"), JsonPrimitive("repo"))))
                }
            ),
            // Build tools
            McpTool(
                name = "build_project",
                description = "Build the project using Gradle. Only allows safe predefined tasks like build, assemble, clean, test, package. Returns build output and lists generated artifacts.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("task", buildJsonObject {
                            put("type", "string")
                            put("description", "Gradle task to run. Allowed tasks: build, assemble, clean, test, jar, package, packageDmg, packageMsi, packageDeb, packageExe, createDistributable, runDistributable")
                        })
                        put("project_dir", buildJsonObject {
                            put("type", "string")
                            put("description", "Project directory containing build.gradle.kts (default: current directory)")
                        })
                        put("additional_args", buildJsonObject {
                            put("type", "string")
                            put("description", "Additional safe gradle arguments like --info, --stacktrace, -x test")
                        })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("task"))))
                }
            ),
            McpTool(
                name = "list_build_artifacts",
                description = "List build artifacts in the project's build output directories (build/libs, build/distributions, build/compose/binaries)",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("project_dir", buildJsonObject {
                            put("type", "string")
                            put("description", "Project directory (default: current directory)")
                        })
                        put("include_all", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Include all files in build directory, not just common artifact locations (default: false)")
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
     * Make an HTTP request to GitHub API
     */
    private fun makeGitHubRequest(
        method: String,
        endpoint: String,
        body: String? = null,
        contentType: String = "application/json",
        uploadUrl: String? = null
    ): Result<String> {
        if (githubToken.isNullOrEmpty()) {
            return Result.failure(Exception("GITHUB_TOKEN environment variable is not set. Please set it with a valid GitHub personal access token."))
        }

        val url = uploadUrl ?: "$apiBaseUrl$endpoint"
        log("Making $method request to: $url")

        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $githubToken")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { os ->
                    os.write(body.toByteArray())
                }
            }

            val responseCode = connection.responseCode
            log("Response code: $responseCode")

            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                return Result.failure(Exception("GitHub API error ($responseCode): $errorBody"))
            }

            Result.success(responseBody)
        } catch (e: Exception) {
            log("Error making request: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Upload a file to GitHub release
     */
    private fun uploadFileToRelease(
        uploadUrl: String,
        file: File,
        assetName: String,
        contentType: String
    ): Result<String> {
        if (githubToken.isNullOrEmpty()) {
            return Result.failure(Exception("GITHUB_TOKEN environment variable is not set"))
        }

        // GitHub upload URL has {?name,label} template, we need to replace it
        // Also URL-encode the asset name to handle spaces and special characters
        val encodedAssetName = java.net.URLEncoder.encode(assetName, "UTF-8").replace("+", "%20")
        val baseUrl = uploadUrl.replace("{?name,label}", "").trimEnd('/')
        val finalUrl = "$baseUrl?name=$encodedAssetName"

        log("Uploading file to: $finalUrl")
        log("Asset name: $assetName (encoded: $encodedAssetName)")
        log("File size: ${file.length()} bytes (${formatSize(file.length())})")
        log("Content type: $contentType")

        return try {
            val uri = java.net.URI(finalUrl)
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $githubToken")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Content-Type", contentType)
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.connectTimeout = 60000 // 1 minute to connect
            connection.readTimeout = 600000 // 10 minutes for large files

            // Set content length and use fixed-length streaming for accurate upload
            // GitHub requires accurate Content-Length for release assets
            val fileSize = file.length()
            connection.setRequestProperty("Content-Length", fileSize.toString())
            connection.setFixedLengthStreamingMode(fileSize)

            // Stream the file with progress logging
            log("Starting file upload...")
            var bytesWritten = 0L
            file.inputStream().use { fis ->
                connection.outputStream.use { os ->
                    val buffer = ByteArray(64 * 1024) // 64KB buffer
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                        bytesWritten += bytesRead
                        // Log progress every 10MB
                        if (bytesWritten % (10 * 1024 * 1024) < (64 * 1024)) {
                            log("Upload progress: ${formatSize(bytesWritten)} / ${formatSize(fileSize)}")
                        }
                    }
                    os.flush()
                }
            }
            log("File upload complete, waiting for response...")

            val responseCode = connection.responseCode
            log("Upload response code: $responseCode")

            if (responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                Result.success(responseBody)
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                } catch (e: Exception) {
                    "Could not read error body: ${e.message}"
                }
                log("Upload failed with error: $errorBody")
                Result.failure(Exception("Upload failed ($responseCode): $errorBody"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            log("Upload timed out: ${e.message}")
            Result.failure(Exception("Upload timed out. The file may be too large or the connection is slow. Try again."))
        } catch (e: java.io.IOException) {
            log("IO error during upload: ${e.message}")
            e.printStackTrace(System.err)
            Result.failure(Exception("Error writing to server: ${e.message}. Check your network connection and try again."))
        } catch (e: Exception) {
            log("Error uploading file: ${e.message}")
            e.printStackTrace(System.err)
            Result.failure(e)
        }
    }

    /**
     * Detect MIME type from file extension
     */
    private fun detectContentType(filename: String): String {
        return when {
            filename.endsWith(".zip") -> "application/zip"
            filename.endsWith(".tar.gz") || filename.endsWith(".tgz") -> "application/gzip"
            filename.endsWith(".tar") -> "application/x-tar"
            filename.endsWith(".gz") -> "application/gzip"
            filename.endsWith(".exe") -> "application/vnd.microsoft.portable-executable"
            filename.endsWith(".msi") -> "application/x-msi"
            filename.endsWith(".dmg") -> "application/x-apple-diskimage"
            filename.endsWith(".deb") -> "application/vnd.debian.binary-package"
            filename.endsWith(".rpm") -> "application/x-rpm"
            filename.endsWith(".jar") -> "application/java-archive"
            filename.endsWith(".apk") -> "application/vnd.android.package-archive"
            filename.endsWith(".pdf") -> "application/pdf"
            filename.endsWith(".json") -> "application/json"
            filename.endsWith(".xml") -> "application/xml"
            filename.endsWith(".txt") -> "text/plain"
            filename.endsWith(".md") -> "text/markdown"
            else -> "application/octet-stream"
        }
    }

    // Tool implementations

    private fun getRepoInfoTool(arguments: JsonObject): McpToolCallResult {
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: repo")

        val result = makeGitHubRequest("GET", "/repos/$owner/$repo")

        return result.fold(
            onSuccess = { responseBody ->
                val repoData = json.parseToJsonElement(responseBody).jsonObject
                val licenseName = repoData["license"].asJsonObjectOrNull()?.get("name").asStringOrNull()
                val output = buildString {
                    appendLine("Repository: ${repoData["full_name"].asStringOrNull()}")
                    appendLine("Description: ${repoData["description"].asStringOrNull() ?: "No description"}")
                    appendLine("Stars: ${repoData["stargazers_count"].asIntOrNull() ?: 0}")
                    appendLine("Forks: ${repoData["forks_count"].asIntOrNull() ?: 0}")
                    appendLine("Open Issues: ${repoData["open_issues_count"].asIntOrNull() ?: 0}")
                    appendLine("Default Branch: ${repoData["default_branch"].asStringOrNull()}")
                    appendLine("Language: ${repoData["language"].asStringOrNull() ?: "Not specified"}")
                    appendLine("License: ${licenseName ?: "No license"}")
                    appendLine("URL: ${repoData["html_url"].asStringOrNull()}")
                }
                McpToolCallResult(
                    content = listOf(McpToolContent(type = "text", text = output)),
                    isError = false
                )
            },
            onFailure = { error ->
                errorResult("Failed to get repository info: ${error.message}")
            }
        )
    }

    private fun listReleasesTool(arguments: JsonObject): McpToolCallResult {
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: repo")
        val perPage = arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 10

        val result = makeGitHubRequest("GET", "/repos/$owner/$repo/releases?per_page=$perPage")

        return result.fold(
            onSuccess = { responseBody ->
                val releases = json.parseToJsonElement(responseBody).jsonArray
                if (releases.isEmpty()) {
                    return McpToolCallResult(
                        content = listOf(McpToolContent(type = "text", text = "No releases found for $owner/$repo")),
                        isError = false
                    )
                }

                val output = buildString {
                    appendLine("Releases for $owner/$repo:")
                    appendLine()
                    releases.forEach { releaseElement ->
                        val release = releaseElement.jsonObject
                        val tagName = release["tag_name"].asStringOrNull() ?: "unknown"
                        val name = release["name"].asStringOrNull() ?: tagName
                        val id = release["id"].asIntOrNull() ?: 0
                        val draft = release["draft"].asBooleanOrNull() ?: false
                        val prerelease = release["prerelease"].asBooleanOrNull() ?: false
                        val publishedAt = release["published_at"].asStringOrNull() ?: "N/A"
                        val assetsCount = release["assets"].asJsonArrayOrNull()?.size ?: 0

                        appendLine("- $name ($tagName)")
                        appendLine("  ID: $id")
                        appendLine("  Published: $publishedAt")
                        if (draft) appendLine("  [DRAFT]")
                        if (prerelease) appendLine("  [PRE-RELEASE]")
                        appendLine("  Assets: $assetsCount")
                        appendLine()
                    }
                }
                McpToolCallResult(
                    content = listOf(McpToolContent(type = "text", text = output)),
                    isError = false
                )
            },
            onFailure = { error ->
                errorResult("Failed to list releases: ${error.message}")
            }
        )
    }

    private fun getReleaseTool(arguments: JsonObject): McpToolCallResult {
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: repo")
        val tag = arguments["tag"]?.jsonPrimitive?.contentOrNull
        val releaseId = arguments["release_id"]?.jsonPrimitive?.intOrNull

        val endpoint = when {
            tag != null -> "/repos/$owner/$repo/releases/tags/$tag"
            releaseId != null -> "/repos/$owner/$repo/releases/$releaseId"
            else -> return errorResult("Either 'tag' or 'release_id' must be provided")
        }

        val result = makeGitHubRequest("GET", endpoint)

        return result.fold(
            onSuccess = { responseBody ->
                val release = json.parseToJsonElement(responseBody).jsonObject
                val authorName = release["author"].asJsonObjectOrNull()?.get("login").asStringOrNull()
                val output = buildString {
                    appendLine("Release: ${release["name"].asStringOrNull()}")
                    appendLine("Tag: ${release["tag_name"].asStringOrNull()}")
                    appendLine("ID: ${release["id"].asIntOrNull()}")
                    appendLine("Draft: ${release["draft"].asBooleanOrNull() ?: false}")
                    appendLine("Pre-release: ${release["prerelease"].asBooleanOrNull() ?: false}")
                    appendLine("Published: ${release["published_at"].asStringOrNull() ?: "Not published"}")
                    appendLine("Author: ${authorName ?: "Unknown"}")
                    appendLine()
                    appendLine("Body:")
                    appendLine(release["body"].asStringOrNull() ?: "No release notes")
                    appendLine()

                    val assets = release["assets"].asJsonArrayOrNull()
                    if (assets != null && assets.isNotEmpty()) {
                        appendLine("Assets (${assets.size}):")
                        assets.forEach { assetElement ->
                            val asset = assetElement.jsonObject
                            val assetName = asset["name"].asStringOrNull()
                            val size = asset["size"].asLongOrNull() ?: 0
                            val downloadCount = asset["download_count"].asIntOrNull() ?: 0
                            val assetId = asset["id"].asIntOrNull()
                            appendLine("  - $assetName (${formatSize(size)}, $downloadCount downloads, ID: $assetId)")
                        }
                    } else {
                        appendLine("Assets: None")
                    }

                    appendLine()
                    appendLine("Upload URL: ${release["upload_url"].asStringOrNull()}")
                }
                McpToolCallResult(
                    content = listOf(McpToolContent(type = "text", text = output)),
                    isError = false
                )
            },
            onFailure = { error ->
                errorResult("Failed to get release: ${error.message}")
            }
        )
    }

    private fun createReleaseTool(arguments: JsonObject): McpToolCallResult {
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: repo")
        val tagName = arguments["tag_name"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: tag_name")

        val name = arguments["name"]?.jsonPrimitive?.contentOrNull ?: tagName
        val body = arguments["body"]?.jsonPrimitive?.contentOrNull ?: ""
        val draft = arguments["draft"]?.jsonPrimitive?.booleanOrNull ?: false
        val prerelease = arguments["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
        val targetCommitish = arguments["target_commitish"]?.jsonPrimitive?.contentOrNull
        val generateReleaseNotes = arguments["generate_release_notes"]?.jsonPrimitive?.booleanOrNull ?: false

        val requestBody = buildJsonObject {
            put("tag_name", tagName)
            put("name", name)
            put("body", body)
            put("draft", draft)
            put("prerelease", prerelease)
            put("generate_release_notes", generateReleaseNotes)
            if (targetCommitish != null) {
                put("target_commitish", targetCommitish)
            }
        }

        val result = makeGitHubRequest(
            "POST",
            "/repos/$owner/$repo/releases",
            json.encodeToString(requestBody)
        )

        return result.fold(
            onSuccess = { responseBody ->
                val release = json.parseToJsonElement(responseBody).jsonObject
                val output = buildString {
                    appendLine("Release created successfully!")
                    appendLine()
                    appendLine("Release: ${release["name"].asStringOrNull()}")
                    appendLine("Tag: ${release["tag_name"].asStringOrNull()}")
                    appendLine("ID: ${release["id"].asIntOrNull()}")
                    appendLine("Draft: ${release["draft"].asBooleanOrNull()}")
                    appendLine("Pre-release: ${release["prerelease"].asBooleanOrNull()}")
                    appendLine("URL: ${release["html_url"].asStringOrNull()}")
                    appendLine()
                    appendLine("Upload URL for assets: ${release["upload_url"].asStringOrNull()}")
                }
                McpToolCallResult(
                    content = listOf(McpToolContent(type = "text", text = output)),
                    isError = false
                )
            },
            onFailure = { error ->
                errorResult("Failed to create release: ${error.message}")
            }
        )
    }

    private fun uploadReleaseAssetTool(arguments: JsonObject): McpToolCallResult {
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: repo")
        val filePath = arguments["file_path"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: file_path")

        val tag = arguments["tag"]?.jsonPrimitive?.contentOrNull
        var releaseId = arguments["release_id"]?.jsonPrimitive?.intOrNull

        // If tag is provided, get release ID from tag
        if (releaseId == null && tag != null) {
            val releaseResult = makeGitHubRequest("GET", "/repos/$owner/$repo/releases/tags/$tag")
            releaseResult.fold(
                onSuccess = { responseBody ->
                    val release = json.parseToJsonElement(responseBody).jsonObject
                    releaseId = release["id"].asIntOrNull()
                },
                onFailure = { error ->
                    return errorResult("Failed to find release with tag '$tag': ${error.message}")
                }
            )
        }

        if (releaseId == null) {
            return errorResult("Either 'release_id' or 'tag' must be provided to identify the release")
        }

        // Get the upload URL
        val releaseResult = makeGitHubRequest("GET", "/repos/$owner/$repo/releases/$releaseId")
        val uploadUrl = releaseResult.fold(
            onSuccess = { responseBody ->
                val release = json.parseToJsonElement(responseBody).jsonObject
                release["upload_url"].asStringOrNull()
            },
            onFailure = { return errorResult("Failed to get release: ${it.message}") }
        ) ?: return errorResult("No upload URL found for release")

        // Check if file exists
        val file = File(filePath)
        if (!file.exists()) {
            return errorResult("File not found: $filePath")
        }
        if (!file.isFile) {
            return errorResult("Path is not a file: $filePath")
        }

        val assetName = arguments["asset_name"]?.jsonPrimitive?.contentOrNull ?: file.name
        val contentType = arguments["content_type"]?.jsonPrimitive?.contentOrNull ?: detectContentType(file.name)

        val result = uploadFileToRelease(uploadUrl, file, assetName, contentType)

        return result.fold(
            onSuccess = { responseBody ->
                val asset = json.parseToJsonElement(responseBody).jsonObject
                val output = buildString {
                    appendLine("Asset uploaded successfully!")
                    appendLine()
                    appendLine("Asset Name: ${asset["name"].asStringOrNull()}")
                    appendLine("Asset ID: ${asset["id"].asIntOrNull()}")
                    appendLine("Size: ${formatSize(asset["size"].asLongOrNull() ?: 0)}")
                    appendLine("Content Type: ${asset["content_type"].asStringOrNull()}")
                    appendLine("State: ${asset["state"].asStringOrNull()}")
                    appendLine("Download URL: ${asset["browser_download_url"].asStringOrNull()}")
                }
                McpToolCallResult(
                    content = listOf(McpToolContent(type = "text", text = output)),
                    isError = false
                )
            },
            onFailure = { error ->
                errorResult("Failed to upload asset: ${error.message}")
            }
        )
    }

    private fun deleteReleaseTool(arguments: JsonObject): McpToolCallResult {
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: repo")
        val tag = arguments["tag"]?.jsonPrimitive?.contentOrNull
        var releaseId = arguments["release_id"]?.jsonPrimitive?.intOrNull

        // If tag is provided, get release ID from tag
        if (releaseId == null && tag != null) {
            val releaseResult = makeGitHubRequest("GET", "/repos/$owner/$repo/releases/tags/$tag")
            releaseResult.fold(
                onSuccess = { responseBody ->
                    val release = json.parseToJsonElement(responseBody).jsonObject
                    releaseId = release["id"].asIntOrNull()
                },
                onFailure = { error ->
                    return errorResult("Failed to find release with tag '$tag': ${error.message}")
                }
            )
        }

        if (releaseId == null) {
            return errorResult("Either 'release_id' or 'tag' must be provided")
        }

        val result = makeGitHubRequest("DELETE", "/repos/$owner/$repo/releases/$releaseId")

        return result.fold(
            onSuccess = {
                McpToolCallResult(
                    content = listOf(McpToolContent(type = "text", text = "Release deleted successfully (ID: $releaseId)")),
                    isError = false
                )
            },
            onFailure = { error ->
                errorResult("Failed to delete release: ${error.message}")
            }
        )
    }

    private fun listReleaseAssetsTool(arguments: JsonObject): McpToolCallResult {
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: repo")
        val tag = arguments["tag"]?.jsonPrimitive?.contentOrNull
        var releaseId = arguments["release_id"]?.jsonPrimitive?.intOrNull

        // If tag is provided, get release ID from tag
        if (releaseId == null && tag != null) {
            val releaseResult = makeGitHubRequest("GET", "/repos/$owner/$repo/releases/tags/$tag")
            releaseResult.fold(
                onSuccess = { responseBody ->
                    val release = json.parseToJsonElement(responseBody).jsonObject
                    releaseId = release["id"].asIntOrNull()
                },
                onFailure = { error ->
                    return errorResult("Failed to find release with tag '$tag': ${error.message}")
                }
            )
        }

        if (releaseId == null) {
            return errorResult("Either 'release_id' or 'tag' must be provided")
        }

        val result = makeGitHubRequest("GET", "/repos/$owner/$repo/releases/$releaseId/assets")

        return result.fold(
            onSuccess = { responseBody ->
                val assets = json.parseToJsonElement(responseBody).jsonArray
                if (assets.isEmpty()) {
                    return McpToolCallResult(
                        content = listOf(McpToolContent(type = "text", text = "No assets found for this release")),
                        isError = false
                    )
                }

                val output = buildString {
                    appendLine("Assets for release (ID: $releaseId):")
                    appendLine()
                    assets.forEach { assetElement ->
                        val asset = assetElement.jsonObject
                        val assetName = asset["name"].asStringOrNull()
                        val assetId = asset["id"].asIntOrNull()
                        val size = asset["size"].asLongOrNull() ?: 0
                        val downloadCount = asset["download_count"].asIntOrNull() ?: 0
                        val contentType = asset["content_type"].asStringOrNull()
                        val downloadUrl = asset["browser_download_url"].asStringOrNull()

                        appendLine("- $assetName")
                        appendLine("  ID: $assetId")
                        appendLine("  Size: ${formatSize(size)}")
                        appendLine("  Content Type: $contentType")
                        appendLine("  Downloads: $downloadCount")
                        appendLine("  URL: $downloadUrl")
                        appendLine()
                    }
                }
                McpToolCallResult(
                    content = listOf(McpToolContent(type = "text", text = output)),
                    isError = false
                )
            },
            onFailure = { error ->
                errorResult("Failed to list assets: ${error.message}")
            }
        )
    }

    private fun deleteReleaseAssetTool(arguments: JsonObject): McpToolCallResult {
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: repo")
        val assetId = arguments["asset_id"]?.jsonPrimitive?.intOrNull
            ?: return errorResult("Missing required parameter: asset_id")

        val result = makeGitHubRequest("DELETE", "/repos/$owner/$repo/releases/assets/$assetId")

        return result.fold(
            onSuccess = {
                McpToolCallResult(
                    content = listOf(McpToolContent(type = "text", text = "Asset deleted successfully (ID: $assetId)")),
                    isError = false
                )
            },
            onFailure = { error ->
                errorResult("Failed to delete asset: ${error.message}")
            }
        )
    }

    private fun listCommitsTool(arguments: JsonObject): McpToolCallResult {
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: repo")

        val branch = arguments["branch"]?.jsonPrimitive?.contentOrNull
        val path = arguments["path"]?.jsonPrimitive?.contentOrNull
        val author = arguments["author"]?.jsonPrimitive?.contentOrNull
        val since = arguments["since"]?.jsonPrimitive?.contentOrNull
        val until = arguments["until"]?.jsonPrimitive?.contentOrNull
        val perPage = arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 20

        // Build query parameters
        val queryParams = mutableListOf<String>()
        queryParams.add("per_page=${perPage.coerceIn(1, 100)}")
        branch?.let { queryParams.add("sha=$it") }
        path?.let { queryParams.add("path=$it") }
        author?.let { queryParams.add("author=$it") }
        since?.let { queryParams.add("since=$it") }
        until?.let { queryParams.add("until=$it") }

        val endpoint = "/repos/$owner/$repo/commits?${queryParams.joinToString("&")}"
        val result = makeGitHubRequest("GET", endpoint)

        return result.fold(
            onSuccess = { responseBody ->
                val commits = json.parseToJsonElement(responseBody).jsonArray
                if (commits.isEmpty()) {
                    return McpToolCallResult(
                        content = listOf(McpToolContent(type = "text", text = "No commits found matching the criteria")),
                        isError = false
                    )
                }

                val output = buildString {
                    appendLine("Commit History for $owner/$repo")
                    branch?.let { appendLine("Branch: $it") }
                    path?.let { appendLine("Path filter: $it") }
                    author?.let { appendLine("Author filter: $it") }
                    appendLine()
                    appendLine("Found ${commits.size} commits:")
                    appendLine()

                    commits.forEach { commitElement ->
                        val commitObj = commitElement.jsonObject
                        val sha = commitObj["sha"].asStringOrNull()?.take(7) ?: "unknown"
                        val fullSha = commitObj["sha"].asStringOrNull() ?: ""
                        val commitData = commitObj["commit"].asJsonObjectOrNull()
                        val message = commitData?.get("message").asStringOrNull()?.lines()?.firstOrNull() ?: "No message"
                        val authorData = commitData?.get("author").asJsonObjectOrNull()
                        val authorName = authorData?.get("name").asStringOrNull() ?: "Unknown"
                        val authorEmail = authorData?.get("email").asStringOrNull() ?: ""
                        val date = authorData?.get("date").asStringOrNull()?.take(10) ?: "Unknown date"

                        // Get committer if different from author
                        val committerData = commitData?.get("committer").asJsonObjectOrNull()
                        val committerName = committerData?.get("name").asStringOrNull()

                        appendLine("[$sha] $message")
                        appendLine("  Author: $authorName <$authorEmail>")
                        if (committerName != null && committerName != authorName) {
                            appendLine("  Committer: $committerName")
                        }
                        appendLine("  Date: $date")
                        appendLine("  URL: https://github.com/$owner/$repo/commit/$fullSha")
                        appendLine()
                    }
                }

                McpToolCallResult(
                    content = listOf(McpToolContent(type = "text", text = output)),
                    isError = false
                )
            },
            onFailure = { error ->
                errorResult("Failed to list commits: ${error.message}")
            }
        )
    }

    // Allowed gradle tasks (whitelist for safety)
    private val allowedGradleTasks = setOf(
        "build", "assemble", "clean", "test", "check", "jar",
        "package", "packageDmg", "packageMsi", "packageDeb", "packageExe", "packageRpm",
        "createDistributable", "runDistributable", "createReleaseDistributable",
        "packageDistributionForCurrentOS", "packageUberJarForCurrentOS",
        "compileKotlin", "compileJava", "classes", "processResources"
    )

    // Allowed additional arguments (whitelist)
    private val allowedGradleArgs = setOf(
        "--info", "--debug", "--stacktrace", "--full-stacktrace",
        "-x", "--exclude-task", "--no-daemon", "--daemon",
        "--parallel", "--no-parallel", "--build-cache", "--no-build-cache",
        "-q", "--quiet", "--warning-mode", "--continue"
    )

    private fun buildProjectTool(arguments: JsonObject): McpToolCallResult {
        val task = arguments["task"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: task")
        val projectDir = arguments["project_dir"]?.jsonPrimitive?.contentOrNull ?: "."
        val additionalArgs = arguments["additional_args"]?.jsonPrimitive?.contentOrNull

        // Validate task is allowed
        if (task !in allowedGradleTasks) {
            return errorResult("Task '$task' is not allowed. Allowed tasks: ${allowedGradleTasks.joinToString(", ")}")
        }

        // Validate project directory
        val projectPath = File(projectDir)
        if (!projectPath.exists() || !projectPath.isDirectory) {
            return errorResult("Project directory does not exist: $projectDir")
        }

        // Check for gradlew
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val gradlewFile = if (isWindows) File(projectPath, "gradlew.bat") else File(projectPath, "gradlew")
        if (!gradlewFile.exists()) {
            return errorResult("Gradle wrapper not found in $projectDir. Expected: ${gradlewFile.name}")
        }

        // Build command
        val command = mutableListOf<String>()
        if (isWindows) {
            command.add("cmd.exe")
            command.add("/c")
            command.add(gradlewFile.absolutePath)
        } else {
            command.add(gradlewFile.absolutePath)
        }
        command.add(task)

        // Parse and validate additional arguments
        if (!additionalArgs.isNullOrBlank()) {
            val args = additionalArgs.split(" ").filter { it.isNotBlank() }
            for (arg in args) {
                val baseArg = arg.split("=").first()
                // Allow -x followed by task name, or known args
                if (baseArg == "-x" || baseArg in allowedGradleArgs ||
                    args.indexOf(arg) > 0 && args[args.indexOf(arg) - 1] == "-x") {
                    command.add(arg)
                } else if (!baseArg.startsWith("-")) {
                    // Skip non-flag args that follow -x
                    continue
                } else {
                    log("Skipping disallowed argument: $arg")
                }
            }
        }

        log("Executing build command: ${command.joinToString(" ")}")

        return try {
            val processBuilder = ProcessBuilder(command)
                .directory(projectPath)
                .redirectErrorStream(true)

            val process = processBuilder.start()

            // Read output with timeout (10 minutes for builds)
            val outputBuilder = StringBuilder()
            val readerThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            outputBuilder.appendLine(line)
                            // Limit output size to prevent memory issues
                            if (outputBuilder.length > 100000) {
                                outputBuilder.append("\n... [Output truncated] ...\n")
                                return@forEachLine
                            }
                        }
                    }
                } catch (e: Exception) {
                    log("Error reading build output: ${e.message}")
                }
            }
            readerThread.start()

            val completed = process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)

            if (!completed) {
                process.destroyForcibly()
                readerThread.interrupt()
                return errorResult("Build timed out after 10 minutes")
            }

            readerThread.join(5000)

            val exitCode = process.exitValue()
            val output = outputBuilder.toString()

            // List artifacts after successful build
            val artifactsInfo = if (exitCode == 0) {
                listArtifactsInDirectory(projectPath)
            } else {
                ""
            }

            val result = buildString {
                appendLine("=== Build ${if (exitCode == 0) "SUCCESSFUL" else "FAILED"} ===")
                appendLine("Task: $task")
                appendLine("Exit Code: $exitCode")
                appendLine()
                appendLine("=== Build Output ===")
                appendLine(output.takeLast(50000)) // Last 50K chars of output
                if (artifactsInfo.isNotBlank()) {
                    appendLine()
                    appendLine("=== Build Artifacts ===")
                    appendLine(artifactsInfo)
                }
            }

            McpToolCallResult(
                content = listOf(McpToolContent(type = "text", text = result)),
                isError = exitCode != 0
            )
        } catch (e: Exception) {
            log("Build error: ${e.message}")
            errorResult("Build failed: ${e.message}")
        }
    }

    private fun listArtifactsInDirectory(projectPath: File): String {
        // Prioritize native installers for release
        val installerExtensions = setOf(".msi", ".exe", ".dmg", ".deb", ".rpm", ".pkg")
        val artifactDirs = listOf(
            "build/compose/binaries/main/msi",
            "build/compose/binaries/main/exe",
            "build/compose/binaries/main/dmg",
            "build/compose/binaries/main/deb",
            "build/compose/binaries/main/rpm",
            "build/compose/binaries/main-release/msi",
            "build/compose/binaries/main-release/exe",
            "build/compose/binaries/main-release/dmg",
            "build/compose/binaries/main-release/deb",
            "build/compose/binaries",
            "build/distributions",
            "build/libs"
        )

        val installers = mutableListOf<String>()
        val otherArtifacts = mutableListOf<String>()

        for (dirPath in artifactDirs) {
            val dir = File(projectPath, dirPath)
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown()
                    .filter { it.isFile }
                    .filter { !it.name.startsWith(".") }
                    .forEach { file ->
                        val relativePath = file.relativeTo(projectPath).path
                        val entry = "$relativePath (${formatSize(file.length())})\n  Full path: ${file.absolutePath}"

                        if (installerExtensions.any { file.name.lowercase().endsWith(it) }) {
                            installers.add(entry)
                        } else if (!file.name.endsWith(".jar") || otherArtifacts.size < 5) {
                            otherArtifacts.add(entry)
                        }
                    }
            }
        }

        return buildString {
            if (installers.isNotEmpty()) {
                appendLine("=== Native Installers (Ready for Release) ===")
                installers.forEach { appendLine(it) }
            }
            if (otherArtifacts.isNotEmpty() && installers.isEmpty()) {
                appendLine("=== Other Artifacts ===")
                otherArtifacts.take(10).forEach { appendLine(it) }
            }
            if (installers.isEmpty() && otherArtifacts.isEmpty()) {
                append("No artifacts found in standard locations")
            }
        }
    }

    private fun listBuildArtifactsTool(arguments: JsonObject): McpToolCallResult {
        val projectDir = arguments["project_dir"]?.jsonPrimitive?.contentOrNull ?: "."
        val includeAll = arguments["include_all"]?.jsonPrimitive?.booleanOrNull ?: false

        val projectPath = File(projectDir)
        if (!projectPath.exists() || !projectPath.isDirectory) {
            return errorResult("Project directory does not exist: $projectDir")
        }

        val buildDir = File(projectPath, "build")
        if (!buildDir.exists()) {
            return errorResult("Build directory does not exist. Run a build task first.")
        }

        val artifacts = mutableListOf<String>()

        if (includeAll) {
            // List all files in build directory
            buildDir.walkTopDown()
                .filter { it.isFile }
                .filter { !it.path.contains("tmp") && !it.path.contains("cache") }
                .sortedByDescending { it.length() }
                .take(100) // Limit to 100 files
                .forEach { file ->
                    val relativePath = file.relativeTo(projectPath).path
                    artifacts.add("$relativePath (${formatSize(file.length())})")
                }
        } else {
            // Only list common artifact locations - prioritize native installers
            val artifactDirs = listOf(
                // Native installers (preferred for releases)
                "build/compose/binaries/main/msi" to "Windows MSI Installers",
                "build/compose/binaries/main/exe" to "Windows EXE Installers",
                "build/compose/binaries/main/dmg" to "macOS DMG Disk Images",
                "build/compose/binaries/main/deb" to "Linux DEB Packages",
                "build/compose/binaries/main/rpm" to "Linux RPM Packages",
                // Release variants
                "build/compose/binaries/main-release/msi" to "Windows MSI Installers (Release)",
                "build/compose/binaries/main-release/exe" to "Windows EXE Installers (Release)",
                "build/compose/binaries/main-release/dmg" to "macOS DMG Disk Images (Release)",
                "build/compose/binaries/main-release/deb" to "Linux DEB Packages (Release)",
                // Application bundles
                "build/compose/binaries/main/app" to "Application bundles",
                "build/compose/binaries/main-release/app" to "Release application bundles",
                // Other artifacts (lower priority)
                "build/distributions" to "Distribution archives",
                "build/libs" to "JAR files (not recommended for release)",
                "build/compose/jars" to "Compose JARs (not recommended for release)"
            )

            for ((dirPath, description) in artifactDirs) {
                val dir = File(projectPath, dirPath)
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.walkTopDown()
                        .filter { it.isFile }
                        .filter { !it.name.startsWith(".") }
                        .toList()

                    if (files.isNotEmpty()) {
                        artifacts.add("\n[$description] ($dirPath):")
                        files.sortedByDescending { it.length() }.forEach { file ->
                            artifacts.add("  ${file.name} (${formatSize(file.length())})")
                            artifacts.add("    Full path: ${file.absolutePath}")
                        }
                    }
                }
            }
        }

        return if (artifacts.isEmpty()) {
            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "No build artifacts found. Make sure to run a build task first (e.g., build, assemble, packageMsi)"
                )),
                isError = false
            )
        } else {
            McpToolCallResult(
                content = listOf(McpToolContent(
                    type = "text",
                    text = "Build Artifacts in $projectDir:\n${artifacts.joinToString("\n")}"
                )),
                isError = false
            )
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun errorResult(message: String): McpToolCallResult {
        return McpToolCallResult(
            content = listOf(McpToolContent(type = "text", text = "Error: $message")),
            isError = true
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
            "github_get_repo_info" -> getRepoInfoTool(arguments)
            "github_list_releases" -> listReleasesTool(arguments)
            "github_get_release" -> getReleaseTool(arguments)
            "github_create_release" -> createReleaseTool(arguments)
            "github_upload_release_asset" -> uploadReleaseAssetTool(arguments)
            "github_delete_release" -> deleteReleaseTool(arguments)
            "github_list_release_assets" -> listReleaseAssetsTool(arguments)
            "github_delete_release_asset" -> deleteReleaseAssetTool(arguments)
            "github_list_commits" -> listCommitsTool(arguments)
            // Build tools
            "build_project" -> buildProjectTool(arguments)
            "list_build_artifacts" -> listBuildArtifactsTool(arguments)
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
        log("MCP GitHub Server starting...")
        log("Kotlin version: ${KotlinVersion.CURRENT}")

        if (githubToken.isNullOrEmpty()) {
            log("WARNING: GITHUB_TOKEN environment variable is not set!")
            log("Please set GITHUB_TOKEN with a valid GitHub personal access token")
            log("Token needs 'repo' scope for release management")
        } else {
            log("GitHub token found")
        }

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
    val server = GitHubMcpServer()
    server.run()
}
