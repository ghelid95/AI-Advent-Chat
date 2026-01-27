package data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

@Serializable
data class DocumentChunk(
    val text: String,
    val index: Int,
    val startChar: Int,
    val endChar: Int
)

@Serializable
data class ChunkEmbedding(
    val chunk: DocumentChunk,
    val embedding: List<Float>
)

@Serializable
data class DocumentEmbeddings(
    val fileName: String,
    val filePath: String,
    val model: String,
    val createdAt: String,
    val chunkSize: Int,
    val overlap: Int,
    val strategy: String,
    val chunks: List<ChunkEmbedding>,
    val totalChunks: Int
)

object EmbeddingStorage {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storageDir: File
        get() {
            val userHome = System.getProperty("user.home")
            val dir = File(userHome, ".ai-advent-chat/embeddings")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun saveEmbeddings(embeddings: DocumentEmbeddings, customName: String? = null): File {
        val timestamp = System.currentTimeMillis()
        val baseName = customName ?: embeddings.fileName
        val sanitizedFileName = baseName
            .replace(Regex("[^a-zA-Z0-9.-]"), "_")
            .take(50)
        val outputFile = File(storageDir, "${sanitizedFileName}_${timestamp}.json")

        val jsonString = json.encodeToString(embeddings)
        outputFile.writeText(jsonString, Charsets.UTF_8)

        return outputFile
    }

    fun loadEmbeddings(file: File): DocumentEmbeddings? {
        return try {
            val jsonString = file.readText(Charsets.UTF_8)
            json.decodeFromString<DocumentEmbeddings>(jsonString)
        } catch (e: Exception) {
            println("Error loading embeddings from ${file.absolutePath}: ${e.message}")
            null
        }
    }

    fun listEmbeddingFiles(): List<File> {
        return storageDir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteEmbeddings(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            println("Error deleting embeddings file: ${e.message}")
            false
        }
    }

    fun getStorageDirectory(): File = storageDir

    fun createDocumentEmbeddings(
        fileName: String,
        filePath: String,
        model: String,
        chunkSize: Int,
        overlap: Int,
        strategy: FileChunking.ChunkStrategy,
        chunks: List<FileChunking.TextChunk>,
        embeddings: List<List<Float>>
    ): DocumentEmbeddings {
        val chunkEmbeddings = chunks.zip(embeddings).map { (chunk, embedding) ->
            ChunkEmbedding(
                chunk = DocumentChunk(
                    text = chunk.text,
                    index = chunk.index,
                    startChar = chunk.startChar,
                    endChar = chunk.endChar
                ),
                embedding = embedding
            )
        }

        return DocumentEmbeddings(
            fileName = fileName,
            filePath = filePath,
            model = model,
            createdAt = Instant.now().toString(),
            chunkSize = chunkSize,
            overlap = overlap,
            strategy = strategy.name,
            chunks = chunkEmbeddings,
            totalChunks = chunks.size
        )
    }
}
