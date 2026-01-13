package data.projectdocs

import data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class DocFile(
    val path: String,
    val name: String,
    val content: String,
    val lastModified: Long
)

class ProjectDocsService(
    private var ollamaClient: OllamaClient?
) {
    companion object {
        private const val MAX_FILE_SIZE = 500_000
        private const val MAX_COMBINED_SIZE = 1_000_000
        private const val MAX_DOCS_FILES = 10
        private const val CHUNK_SIZE = 1000
        private const val CHUNK_OVERLAP = 100
    }

    fun updateOllamaClient(client: OllamaClient) {
        this.ollamaClient = client
    }

    suspend fun discoverProjectDocs(workingDir: File): List<DocFile> = withContext(Dispatchers.IO) {
        val docs = mutableListOf<DocFile>()

        if (!workingDir.exists() || !workingDir.isDirectory) {
            println("[ProjectDocs] Working directory does not exist: ${workingDir.absolutePath}")
            return@withContext emptyList()
        }

        val readmeVariants = listOf("README.md", "readme.md", "README.txt", "README", "Readme.md")
        for (variant in readmeVariants) {
            val file = File(workingDir, variant)
            if (file.exists() && file.isFile) {
                val size = file.length()
                if (size > MAX_FILE_SIZE) {
                    println("[ProjectDocs] Skipping ${file.name} (too large: ${size / 1024}KB)")
                    continue
                }
                try {
                    val content = file.readText()
                    docs.add(DocFile(
                        path = file.absolutePath,
                        name = file.name,
                        content = content,
                        lastModified = file.lastModified()
                    ))
                    println("[ProjectDocs] Found ${file.name} (${size / 1024}KB)")
                    break
                } catch (e: Exception) {
                    println("[ProjectDocs] Error reading ${file.name}: ${e.message}")
                }
            }
        }

        val contributing = File(workingDir, "CONTRIBUTING.md")
        if (contributing.exists() && contributing.isFile) {
            val size = contributing.length()
            if (size <= MAX_FILE_SIZE) {
                try {
                    val content = contributing.readText()
                    docs.add(DocFile(
                        path = contributing.absolutePath,
                        name = contributing.name,
                        content = content,
                        lastModified = contributing.lastModified()
                    ))
                    println("[ProjectDocs] Found CONTRIBUTING.md (${size / 1024}KB)")
                } catch (e: Exception) {
                    println("[ProjectDocs] Error reading CONTRIBUTING.md: ${e.message}")
                }
            } else {
                println("[ProjectDocs] Skipping CONTRIBUTING.md (too large: ${size / 1024}KB)")
            }
        }

        val docsDir = File(workingDir, "docs")
        if (docsDir.exists() && docsDir.isDirectory) {
            val docFiles = docsDir.listFiles { file ->
                file.isFile && file.extension.equals("md", ignoreCase = true) && file.length() <= MAX_FILE_SIZE
            }?.take(MAX_DOCS_FILES) ?: emptyList()

            for (file in docFiles) {
                try {
                    val content = file.readText()
                    docs.add(DocFile(
                        path = file.absolutePath,
                        name = "docs/${file.name}",
                        content = content,
                        lastModified = file.lastModified()
                    ))
                    println("[ProjectDocs] Found docs/${file.name} (${file.length() / 1024}KB)")
                } catch (e: Exception) {
                    println("[ProjectDocs] Error reading docs/${file.name}: ${e.message}")
                }
            }
        }

        if (docs.isEmpty()) {
            println("[ProjectDocs] No documentation files found in ${workingDir.absolutePath}")
        } else {
            println("[ProjectDocs] Discovered ${docs.size} documentation file(s)")
        }

        return@withContext docs
    }

    suspend fun initializeProjectDocs(workingDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val client = ollamaClient ?: return@withContext Result.failure(
                IllegalStateException("OllamaClient not initialized")
            )

            val docs = discoverProjectDocs(workingDir)
            if (docs.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No documentation files found")
                )
            }

            val combinedText = combineDocuments(docs)
            if (combinedText.length > MAX_COMBINED_SIZE) {
                println("[ProjectDocs] Combined documentation is large (${combinedText.length / 1024}KB), using first ${MAX_COMBINED_SIZE / 1024}KB")
            }
            val finalText = if (combinedText.length > MAX_COMBINED_SIZE) {
                combinedText.take(MAX_COMBINED_SIZE)
            } else {
                combinedText
            }

            println("[ProjectDocs] Chunking documentation (strategy: PARAGRAPH, size: $CHUNK_SIZE)...")
            val chunks = FileChunking.chunkByParagraphs(
                text = finalText,
                maxChunkSize = CHUNK_SIZE
            )
            println("[ProjectDocs] Created ${chunks.size} chunks")

            println("[ProjectDocs] Generating embeddings for ${chunks.size} chunks...")
            val model = "nomic-embed-text"
            val chunkEmbeddings = mutableListOf<ChunkEmbedding>()

            for ((index, chunk) in chunks.withIndex()) {
                try {
                    val embedding = client.generateEmbedding(chunk.text, model)
                    if (embedding.isNotEmpty()) {
                        val docChunk = DocumentChunk(
                            text = chunk.text,
                            index = chunk.index,
                            startChar = chunk.startChar,
                            endChar = chunk.endChar
                        )
                        chunkEmbeddings.add(ChunkEmbedding(docChunk, embedding))
                        if ((index + 1) % 10 == 0) {
                            println("[ProjectDocs] Generated ${index + 1}/${chunks.size} embeddings...")
                        }
                    } else {
                        println("[ProjectDocs] Failed to generate embedding for chunk ${index + 1}")
                    }
                } catch (e: Exception) {
                    println("[ProjectDocs] Error generating embedding for chunk ${index + 1}: ${e.message}")
                }
            }

            if (chunkEmbeddings.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to generate any embeddings")
                )
            }

            println("[ProjectDocs] Successfully generated ${chunkEmbeddings.size} embeddings")

            val documentEmbeddings = DocumentEmbeddings(
                fileName = "Project Documentation",
                filePath = docs.joinToString(", ") { it.path },
                model = model,
                createdAt = java.time.Instant.now().toString(),
                chunkSize = CHUNK_SIZE,
                overlap = CHUNK_OVERLAP,
                strategy = "PARAGRAPH",
                chunks = chunkEmbeddings,
                totalChunks = chunkEmbeddings.size
            )

            val embeddingsDir = File(System.getProperty("user.home"), ".ai-advent-chat/embeddings")
            embeddingsDir.mkdirs()

            val fileName = generateEmbeddingFileName(workingDir)
            val embeddingFile = File(embeddingsDir, fileName)

            val json = kotlinx.serialization.json.Json { prettyPrint = true; ignoreUnknownKeys = true }
            val jsonString = json.encodeToString(kotlinx.serialization.serializer(), documentEmbeddings)
            embeddingFile.writeText(jsonString)
            println("[ProjectDocs] Saved embeddings to ${embeddingFile.name}")

            return@withContext Result.success(embeddingFile)

        } catch (e: Exception) {
            println("[ProjectDocs] Error during initialization: ${e.message}")
            e.printStackTrace()
            return@withContext Result.failure(e)
        }
    }

    suspend fun isProjectDocsStale(workingDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val embeddingFile = getProjectDocsEmbeddingFile(workingDir)
            if (embeddingFile == null || !embeddingFile.exists()) {
                return@withContext true
            }

            val documentEmbeddings = EmbeddingStorage.loadEmbeddings(embeddingFile)
                ?: return@withContext true

            val embeddingCreationTime = try {
                java.time.Instant.parse(documentEmbeddings.createdAt).toEpochMilli()
            } catch (e: Exception) {
                println("[ProjectDocs] Cannot parse creation time: ${documentEmbeddings.createdAt}")
                return@withContext true
            }

            val sourcePaths = documentEmbeddings.filePath.split(", ")
            for (sourcePath in sourcePaths) {
                val sourceFile = File(sourcePath.trim())
                if (!sourceFile.exists()) {
                    println("[ProjectDocs] Source file missing: ${sourceFile.name}, marked as stale")
                    return@withContext true
                }
                if (sourceFile.lastModified() > embeddingCreationTime) {
                    println("[ProjectDocs] Source file modified: ${sourceFile.name}, marked as stale")
                    return@withContext true
                }
            }

            return@withContext false

        } catch (e: Exception) {
            println("[ProjectDocs] Error checking staleness: ${e.message}")
            return@withContext true
        }
    }

    fun getProjectDocsEmbeddingFile(workingDir: File): File? {
        val embeddingsDir = File(System.getProperty("user.home"), ".ai-advent-chat/embeddings")
        if (!embeddingsDir.exists()) {
            return null
        }

        val fileName = generateEmbeddingFileName(workingDir)
        val embeddingFile = File(embeddingsDir, fileName)

        return if (embeddingFile.exists()) embeddingFile else null
    }

    private fun generateEmbeddingFileName(workingDir: File): String {
        val hash = hashWorkingDirectory(workingDir.absolutePath)
        return "_project_docs_$hash.json"
    }

    private fun hashWorkingDirectory(path: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }

    private fun combineDocuments(docs: List<DocFile>): String {
        return buildString {
            docs.forEachIndexed { index, doc ->
                if (index > 0) {
                    appendLine()
                    appendLine()
                }
                appendLine("=== ${doc.name} ===")
                appendLine()
                appendLine(doc.content.trim())
            }
        }
    }
}
