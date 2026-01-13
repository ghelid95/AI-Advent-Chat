package data

import java.io.File

object FileChunking {

    data class TextChunk(
        val text: String,
        val index: Int,
        val startChar: Int,
        val endChar: Int
    )

    fun chunkText(
        text: String,
        chunkSize: Int = 500,
        overlap: Int = 50
    ): List<TextChunk> {
        if (text.isEmpty()) return emptyList()
        if (chunkSize <= 0) throw IllegalArgumentException("Chunk size must be positive")
        if (overlap < 0) throw IllegalArgumentException("Overlap must be non-negative")
        if (overlap >= chunkSize) throw IllegalArgumentException("Overlap must be less than chunk size")

        val chunks = mutableListOf<TextChunk>()
        var startIndex = 0
        var chunkIndex = 0

        while (startIndex < text.length) {
            val endIndex = minOf(startIndex + chunkSize, text.length)
            val chunkText = text.substring(startIndex, endIndex)

            chunks.add(TextChunk(
                text = chunkText,
                index = chunkIndex,
                startChar = startIndex,
                endChar = endIndex
            ))

            chunkIndex++
            startIndex += chunkSize - overlap
        }

        return chunks
    }

    fun chunkByParagraphs(
        text: String,
        maxChunkSize: Int = 1000
    ): List<TextChunk> {
        if (text.isEmpty()) return emptyList()

        val paragraphs = text.split(Regex("\n\n+"))
        val chunks = mutableListOf<TextChunk>()
        var currentChunk = StringBuilder()
        var chunkIndex = 0
        var startChar = 0
        var currentPosition = 0

        for (paragraph in paragraphs) {
            val trimmedParagraph = paragraph.trim()
            if (trimmedParagraph.isEmpty()) {
                currentPosition += paragraph.length + 2 // account for \n\n
                continue
            }

            // If paragraph itself exceeds max size, split it into smaller chunks
            if (trimmedParagraph.length > maxChunkSize) {
                // Finish current chunk if it has content
                if (currentChunk.isNotEmpty()) {
                    val chunkText = currentChunk.toString().trim()
                    chunks.add(TextChunk(
                        text = chunkText,
                        index = chunkIndex,
                        startChar = startChar,
                        endChar = startChar + chunkText.length
                    ))
                    chunkIndex++
                    currentChunk.clear()
                }

                // Split oversized paragraph into fixed-size chunks
                var paragraphStart = 0
                while (paragraphStart < trimmedParagraph.length) {
                    val paragraphEnd = minOf(paragraphStart + maxChunkSize, trimmedParagraph.length)
                    val chunkText = trimmedParagraph.substring(paragraphStart, paragraphEnd)
                    chunks.add(TextChunk(
                        text = chunkText,
                        index = chunkIndex,
                        startChar = currentPosition + paragraphStart,
                        endChar = currentPosition + paragraphEnd
                    ))
                    chunkIndex++
                    paragraphStart = paragraphEnd
                }

                startChar = currentPosition + trimmedParagraph.length
                currentPosition += paragraph.length + 2
                continue
            }

            if (currentChunk.length + trimmedParagraph.length > maxChunkSize && currentChunk.isNotEmpty()) {
                val chunkText = currentChunk.toString().trim()
                chunks.add(TextChunk(
                    text = chunkText,
                    index = chunkIndex,
                    startChar = startChar,
                    endChar = startChar + chunkText.length
                ))
                chunkIndex++
                currentChunk.clear()
                startChar = currentPosition
            }

            if (currentChunk.isNotEmpty()) {
                currentChunk.append("\n\n")
            }
            currentChunk.append(trimmedParagraph)
            currentPosition += paragraph.length + 2
        }

        if (currentChunk.isNotEmpty()) {
            val chunkText = currentChunk.toString().trim()
            chunks.add(TextChunk(
                text = chunkText,
                index = chunkIndex,
                startChar = startChar,
                endChar = startChar + chunkText.length
            ))
        }

        return chunks
    }

    fun chunkBySentences(
        text: String,
        maxChunkSize: Int = 1000
    ): List<TextChunk> {
        if (text.isEmpty()) return emptyList()

        val sentencePattern = Regex("(?<=[.!?])\\s+")
        val sentences = text.split(sentencePattern)
        val chunks = mutableListOf<TextChunk>()
        var currentChunk = StringBuilder()
        var chunkIndex = 0
        var startChar = 0
        var currentPosition = 0

        for (sentence in sentences) {
            val trimmedSentence = sentence.trim()
            if (trimmedSentence.isEmpty()) continue

            if (currentChunk.length + trimmedSentence.length > maxChunkSize && currentChunk.isNotEmpty()) {
                val chunkText = currentChunk.toString().trim()
                chunks.add(TextChunk(
                    text = chunkText,
                    index = chunkIndex,
                    startChar = startChar,
                    endChar = startChar + chunkText.length
                ))
                chunkIndex++
                currentChunk.clear()
                startChar = currentPosition
            }

            if (currentChunk.isNotEmpty()) {
                currentChunk.append(" ")
            }
            currentChunk.append(trimmedSentence)
            currentPosition += sentence.length
        }

        if (currentChunk.isNotEmpty()) {
            val chunkText = currentChunk.toString().trim()
            chunks.add(TextChunk(
                text = chunkText,
                index = chunkIndex,
                startChar = startChar,
                endChar = startChar + chunkText.length
            ))
        }

        return chunks
    }

    fun readAndChunkFile(
        file: File,
        chunkSize: Int = 500,
        overlap: Int = 50,
        strategy: ChunkStrategy = ChunkStrategy.FIXED_SIZE
    ): List<TextChunk> {
        val text = file.readText()
        return when (strategy) {
            ChunkStrategy.FIXED_SIZE -> chunkText(text, chunkSize, overlap)
            ChunkStrategy.PARAGRAPH -> chunkByParagraphs(text, chunkSize)
            ChunkStrategy.SENTENCE -> chunkBySentences(text, chunkSize)
        }
    }

    enum class ChunkStrategy {
        FIXED_SIZE,
        PARAGRAPH,
        SENTENCE
    }
}