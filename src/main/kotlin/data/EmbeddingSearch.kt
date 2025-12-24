package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import kotlin.math.sqrt

data class SearchResult(
    val chunk: DocumentChunk,
    val similarity: Float,
    val embeddingFileName: String,
    val embedding: List<Float> = emptyList() // Store embedding for MMR
)

/**
 * In-memory cache for embedding files to avoid repeated disk I/O.
 * Keeps the most recently used embedding files in memory.
 */
object EmbeddingCache {
    private val cache = LinkedHashMap<String, DocumentEmbeddings>(5, 0.75f, true)
    private const val MAX_CACHE_SIZE = 5 // Keep last 5 embedding files in memory

    fun get(filePath: String): DocumentEmbeddings? {
        val embeddings = cache[filePath]
        if (embeddings != null) {
            println("[EmbeddingCache] Cache HIT for $filePath")
        } else {
            println("[EmbeddingCache] Cache MISS for $filePath")
        }
        return embeddings
    }

    fun put(filePath: String, embeddings: DocumentEmbeddings) {
        // Remove oldest entry if cache is full
        if (cache.size >= MAX_CACHE_SIZE) {
            val oldestKey = cache.keys.first()
            cache.remove(oldestKey)
            println("[EmbeddingCache] Evicted $oldestKey from cache")
        }
        cache[filePath] = embeddings
        println("[EmbeddingCache] Cached $filePath (cache size: ${cache.size}/$MAX_CACHE_SIZE)")
    }

    fun clear() {
        cache.clear()
        println("[EmbeddingCache] Cache cleared")
    }

    fun getStats(): String {
        return "Cache: ${cache.size}/$MAX_CACHE_SIZE entries"
    }
}

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

    /**
     * Apply Maximal Marginal Relevance (MMR) to select diverse results.
     *
     * @param results All candidate results sorted by relevance
     * @param topK Number of results to select
     * @param lambda Balance between relevance (1.0) and diversity (0.0). Default 0.5
     * @return Diverse subset of results
     */
    private fun applyMMR(
        results: List<SearchResult>,
        topK: Int,
        lambda: Float = 0.5f
    ): List<SearchResult> {
        if (results.isEmpty() || topK <= 1) return results.take(topK)

        val selected = mutableListOf<SearchResult>()
        val candidates = results.toMutableList()

        // Add the most relevant result first
        selected.add(candidates.removeAt(0))

        println("[EmbeddingSearch] MMR: Selected first result (similarity: ${selected[0].similarity})")

        // Iteratively select results that maximize MMR score
        while (selected.size < topK && candidates.isNotEmpty()) {
            var bestScore = Float.NEGATIVE_INFINITY
            var bestIdx = -1

            candidates.forEachIndexed { idx, candidate ->
                // Relevance score (already normalized via cosine similarity)
                val relevance = candidate.similarity

                // Diversity score: minimum similarity to already selected results
                // Higher diversity = lower similarity to selected results
                val diversity = selected.minOf { selectedResult ->
                    cosineSimilarity(candidate.embedding, selectedResult.embedding)
                }

                // MMR score: balance relevance and diversity
                // lambda=1.0 means pure relevance, lambda=0.0 means pure diversity
                val mmrScore = lambda * relevance - (1 - lambda) * diversity

                if (mmrScore > bestScore) {
                    bestScore = mmrScore
                    bestIdx = idx
                }
            }

            if (bestIdx >= 0) {
                val selectedCandidate = candidates.removeAt(bestIdx)
                selected.add(selectedCandidate)
                println("[EmbeddingSearch] MMR: Selected result ${selected.size} (similarity: ${selectedCandidate.similarity}, MMR score: $bestScore)")
            } else {
                break
            }
        }

        return selected
    }

    suspend fun searchSimilarChunks(
        query: String,
        embeddingFile: File,
        ollamaClient: OllamaClient,
        topK: Int = 3,
        threshold: Float = 0.5f,
        useMmr: Boolean = true,
        mmrLambda: Float = 0.5f
    ): List<SearchResult> {
        try {
            val startTime = System.currentTimeMillis()

            // Try to get from cache first, otherwise load from disk
            val documentEmbeddings = EmbeddingCache.get(embeddingFile.absolutePath)
                ?: run {
                    val loaded = EmbeddingStorage.loadEmbeddings(embeddingFile) ?: run {
                        println("[EmbeddingSearch] Failed to load embeddings from ${embeddingFile.name}")
                        return emptyList()
                    }
                    // Store in cache for future queries
                    EmbeddingCache.put(embeddingFile.absolutePath, loaded)
                    loaded
                }

            val loadTime = System.currentTimeMillis() - startTime
            println("[EmbeddingSearch] Loaded ${documentEmbeddings.totalChunks} chunks from ${embeddingFile.name} in ${loadTime}ms")

            // Generate embedding for the query
            val queryStartTime = System.currentTimeMillis()
            val queryEmbedding = ollamaClient.generateEmbedding(query, documentEmbeddings.model)
            if (queryEmbedding.isEmpty()) {
                println("[EmbeddingSearch] Failed to generate query embedding")
                return emptyList()
            }

            val queryTime = System.currentTimeMillis() - queryStartTime
            println("[EmbeddingSearch] Generated query embedding (size: ${queryEmbedding.size}) in ${queryTime}ms")

            // Calculate similarities in parallel
            val searchStartTime = System.currentTimeMillis()
            val results = coroutineScope {
                documentEmbeddings.chunks.map { chunkEmbedding ->
                    async(Dispatchers.Default) {
                        val similarity = cosineSimilarity(queryEmbedding, chunkEmbedding.embedding)
                        SearchResult(
                            chunk = chunkEmbedding.chunk,
                            similarity = similarity,
                            embeddingFileName = documentEmbeddings.fileName,
                            embedding = chunkEmbedding.embedding
                        )
                    }
                }.awaitAll()
            }

            val searchTime = System.currentTimeMillis() - searchStartTime
            println("[EmbeddingSearch] Calculated ${results.size} similarities in ${searchTime}ms (parallel)")

            // Filter by threshold and sort by similarity
            val candidateResults = results
                .filter { it.similarity >= threshold }
                .sortedByDescending { it.similarity }

            println("[EmbeddingSearch] Found ${candidateResults.size} chunks above threshold $threshold")

            // Apply MMR or simple top-K selection
            val finalResults = if (useMmr && candidateResults.size > topK) {
                println("[EmbeddingSearch] Applying MMR with lambda=$mmrLambda")
                applyMMR(candidateResults, topK, mmrLambda)
            } else {
                candidateResults.take(topK)
            }

            finalResults.forEach { result ->
                println("[EmbeddingSearch]   - Similarity: ${result.similarity}, Preview: ${result.chunk.text.take(100)}...")
            }

            return finalResults
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