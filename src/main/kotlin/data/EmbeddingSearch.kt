package data

import java.io.File
import kotlin.math.sqrt

data class SearchResult(
    val chunk: DocumentChunk,
    val similarity: Float,
    val embeddingFileName: String
)

object EmbeddingSearch {

    fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Float {
        if (vec1.size != vec2.size || vec1.isEmpty()) return 0f

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }

    suspend fun searchSimilarChunks(
        query: String,
        embeddingFile: File,
        ollamaClient: OllamaClient,
        topK: Int = 3,
        threshold: Float = 0.5f
    ): List<SearchResult> {
        try {
            // Load the embedding file
            val documentEmbeddings = EmbeddingStorage.loadEmbeddings(embeddingFile) ?: run {
                println("[EmbeddingSearch] Failed to load embeddings from ${embeddingFile.name}")
                return emptyList()
            }

            println("[EmbeddingSearch] Loaded ${documentEmbeddings.totalChunks} chunks from ${embeddingFile.name}")

            // Generate embedding for the query
            val queryEmbedding = ollamaClient.generateEmbedding(query, documentEmbeddings.model)
            if (queryEmbedding.isEmpty()) {
                println("[EmbeddingSearch] Failed to generate query embedding")
                return emptyList()
            }

            println("[EmbeddingSearch] Generated query embedding (size: ${queryEmbedding.size})")

            // Calculate similarities
            val results = documentEmbeddings.chunks.map { chunkEmbedding ->
                val similarity = cosineSimilarity(queryEmbedding, chunkEmbedding.embedding)
                SearchResult(
                    chunk = chunkEmbedding.chunk,
                    similarity = similarity,
                    embeddingFileName = documentEmbeddings.fileName
                )
            }

            // Filter by threshold and sort by similarity
            val filteredResults = results
                .filter { it.similarity >= threshold }
                .sortedByDescending { it.similarity }
                .take(topK)

            println("[EmbeddingSearch] Found ${filteredResults.size} chunks above threshold $threshold")
            filteredResults.forEach { result ->
                println("[EmbeddingSearch]   - Similarity: ${result.similarity}, Preview: ${result.chunk.text.take(100)}...")
            }

            return filteredResults
        } catch (e: Exception) {
            println("[EmbeddingSearch] Error during search: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    fun formatSearchResultsAsContext(results: List<SearchResult>): String {
        if (results.isEmpty()) return ""

        return buildString {
            appendLine("=== Retrieved Context ===")
            appendLine()
            results.forEachIndexed { index, result ->
                appendLine("Context ${index + 1} (similarity: ${"%.3f".format(result.similarity)}):")
                appendLine(result.chunk.text)
                appendLine()
            }
            appendLine("=== End of Retrieved Context ===")
        }
    }
}