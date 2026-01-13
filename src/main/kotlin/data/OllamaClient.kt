package data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OllamaEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
data class OllamaEmbeddingResponse(
    val model: String,
    val embeddings: List<List<Float>>
)

@Serializable
data class OllamaErrorResponse(
    val error: String
)

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

class OllamaClient(
    private val baseUrl: String = "http://localhost:11434"
) {
    companion object {
        // nomic-embed-text has 8192 token context
        // Using conservative estimate: ~4 chars per token
        // Set max to ~6000 tokens worth of characters for safety
        private const val MAX_EMBEDDING_INPUT_LENGTH = 24000
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120000 // 2 minutes for embeddings
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 120000
        }
    }

    suspend fun generateEmbedding(text: String, model: String = "nomic-embed-text"): List<Float> {
        return withContext(Dispatchers.IO) {
            try {
                // Truncate input if it exceeds maximum length
                val truncatedText = if (text.length > MAX_EMBEDDING_INPUT_LENGTH) {
                    println("[Ollama] Input too long (${text.length} chars), truncating to $MAX_EMBEDDING_INPUT_LENGTH")
                    text.take(MAX_EMBEDDING_INPUT_LENGTH)
                } else {
                    text
                }

                val response = client.post("$baseUrl/api/embed") {
                    contentType(ContentType.Application.Json)
                    setBody(OllamaEmbeddingRequest(
                        model = model,
                        input = truncatedText
                    ))
                }

                // Get response body as text first to check for errors
                val bodyText = response.body<String>()

                // Try to parse as error response first
                try {
                    val errorResponse = json.decodeFromString<OllamaErrorResponse>(bodyText)
                    println("Error generating embedding: ${errorResponse.error}")
                    return@withContext emptyList()
                } catch (_: Exception) {
                    // Not an error response, continue parsing as embedding response
                }

                // Parse as embedding response
                try {
                    val embeddingResponse = json.decodeFromString<OllamaEmbeddingResponse>(bodyText)
                    embeddingResponse.embeddings.firstOrNull() ?: emptyList()
                } catch (e: Exception) {
                    println("Error parsing embedding response: ${e.message}")
                    println("Response body: ${bodyText.take(200)}")
                    e.printStackTrace()
                    emptyList()
                }
            } catch (e: Exception) {
                println("Error generating embedding: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun generateEmbeddings(texts: List<String>, model: String = "nomic-embed-text"): List<List<Float>> {
        return texts.map { text ->
            generateEmbedding(text, model)
        }
    }

    fun close() {
        client.close()
    }
}