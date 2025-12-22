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

class OllamaClient(
    private val baseUrl: String = "http://localhost:11434"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
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
                val response = client.post("$baseUrl/api/embed") {
                    contentType(ContentType.Application.Json)
                    setBody(OllamaEmbeddingRequest(
                        model = model,
                        input = text
                    ))
                }

                val embeddingResponse = response.body<OllamaEmbeddingResponse>()
                embeddingResponse.embeddings.firstOrNull() ?: emptyList()
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