package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * Client for local speech recognition using Vosk.
 * Vosk is a free, offline speech recognition toolkit.
 *
 * Models can be downloaded from: https://alphacephei.com/vosk/models
 */
class VoskClient(private val modelPath: String) {

    private var model: Model? = null
    private var isInitialized = false

    // System default charset for mojibake fix (Windows-1251 for Russian Windows)
    private val systemCharset: Charset = Charset.defaultCharset()

    init {
        initializeModel()
    }

    /**
     * Fixes mojibake when UTF-8 text was incorrectly decoded using system charset.
     * This happens when Vosk JNI returns UTF-8 bytes but Java interprets them
     * using the platform default charset (Windows-1251 on Russian Windows).
     */
    private fun fixMojibake(text: String): String {
        return try {
            // Convert back to bytes using system charset, then decode as UTF-8
            val bytes = text.toByteArray(Charset.forName("windows-1251"))
            val fixed = String(bytes, Charsets.UTF_8)
            // Check if the fix made sense (contains valid Cyrillic characters)
            if (fixed.any { it in '\u0400'..'\u04FF' } || fixed.all { it.code < 128 }) {
                println("[Vosk] Fixed encoding: '$text' -> '$fixed'")
                fixed
            } else {
                text // Return original if fix didn't help
            }
        } catch (_: Exception) {
            text // Return original on any error
        }
    }

    /**
     * Initializes the Vosk model from the specified path.
     */
    private fun initializeModel() {
        try {
            val modelDir = File(modelPath)
            if (!modelDir.exists()) {
                throw VoskException("Model directory not found: $modelPath")
            }

            println("[Vosk] Initializing model from: $modelPath")
            model = Model(modelPath)
            isInitialized = true
            println("[Vosk] Model loaded successfully")
        } catch (e: Exception) {
            throw VoskException("Failed to initialize Vosk model: ${e.message}", e)
        }
    }

    /**
     * Transcribes audio file to text using Vosk.
     *
     * @param audioFile The audio file to transcribe (WAV format, 16kHz, 16-bit, mono)
     * @return Transcribed text
     * @throws VoskException if transcription fails
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        if (!isInitialized || model == null) {
            throw VoskException("Vosk model is not initialized")
        }

        try {
            println("[Vosk] Transcribing audio file: ${audioFile.name} (${audioFile.length()} bytes)")

            // Create recognizer with 16kHz sample rate
            val recognizer = Recognizer(model, 16000f)

            FileInputStream(audioFile).use { fis ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalBytesRead = 0L

                // Feed audio data to recognizer
                while (fis.read(buffer).also { bytesRead = it } >= 0) {
                    totalBytesRead += bytesRead
                    recognizer.acceptWaveForm(buffer, bytesRead)
                }

                println("[Vosk] Processed $totalBytesRead bytes of audio")

                // Get final result
                val finalResult = recognizer.finalResult
                recognizer.close()

                // Parse JSON result (Vosk returns UTF-8 encoded JSON)
                // Fix potential mojibake from JNI layer on Windows
                val fixedResult = fixMojibake(finalResult)
                val json = Json { ignoreUnknownKeys = true }

                val resultJson = json.parseToJsonElement(fixedResult).jsonObject
                val rawText = resultJson["text"]?.jsonPrimitive?.content ?: ""
                val text = fixMojibake(rawText)

                if (text.isBlank()) {
                    throw VoskException("No speech detected in audio")
                }

                println("[Vosk] Transcription successful: ${text.take(100)}...")
                text
            }
        } catch (e: Exception) {
            if (e is VoskException) throw e
            throw VoskException("Failed to transcribe audio: ${e.message}", e)
        }
    }

    /**
     * Transcribes audio file with partial results callback.
     * Useful for real-time transcription display.
     *
     * @param audioFile The audio file to transcribe
     * @param onPartialResult Callback for partial results during transcription
     * @return Final transcribed text
     */
    suspend fun transcribeWithPartials(
        audioFile: File,
        onPartialResult: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (!isInitialized || model == null) {
            throw VoskException("Vosk model is not initialized")
        }

        try {
            val recognizer = Recognizer(model, 16000f)
            val json = Json { ignoreUnknownKeys = true }

            FileInputStream(audioFile).use { fis ->
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (fis.read(buffer).also { bytesRead = it } >= 0) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        // Full result available
                        val result = fixMojibake(recognizer.result)
                        val resultJson = json.parseToJsonElement(result).jsonObject
                        val text = resultJson["text"]?.jsonPrimitive?.content?.let { fixMojibake(it) }
                        if (!text.isNullOrBlank()) {
                            onPartialResult(text)
                        }
                    } else {
                        // Partial result
                        val partialResult = fixMojibake(recognizer.partialResult)
                        val partialJson = json.parseToJsonElement(partialResult).jsonObject
                        val partialText = partialJson["partial"]?.jsonPrimitive?.content?.let { fixMojibake(it) }
                        if (!partialText.isNullOrBlank()) {
                            onPartialResult(partialText)
                        }
                    }
                }

                // Get final result
                val finalResult = fixMojibake(recognizer.finalResult)
                recognizer.close()

                val resultJson = json.parseToJsonElement(finalResult).jsonObject
                val rawText = resultJson["text"]?.jsonPrimitive?.content ?: ""
                fixMojibake(rawText)
            }
        } catch (e: Exception) {
            if (e is VoskException) throw e
            throw VoskException("Failed to transcribe audio: ${e.message}", e)
        }
    }

    /**
     * Checks if the model is initialized and ready.
     */
    fun isReady(): Boolean = isInitialized && model != null

    /**
     * Gets information about the loaded model.
     */
    fun getModelInfo(): String {
        return if (isInitialized) {
            "Model loaded from: $modelPath"
        } else {
            "Model not initialized"
        }
    }

    /**
     * Closes the Vosk model and releases resources.
     */
    fun close() {
        try {
            model?.close()
            model = null
            isInitialized = false
            println("[Vosk] Model closed")
        } catch (e: Exception) {
            println("[Vosk] Error closing model: ${e.message}")
        }
    }
}

/**
 * Exception thrown when Vosk operations fail.
 */
class VoskException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Helper class to download and manage Vosk models.
 */
object VoskModelManager {

    private val modelsDir: File = run {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".ai-advent-chat")
        val vosk = File(appDir, "vosk-models")
        if (!vosk.exists()) {
            vosk.mkdirs()
        }
        vosk
    }

    /**
     * Gets the path to a model, or null if not installed.
     */
    fun getModelPath(language: String): String? {
        val modelDir = File(modelsDir, getModelName(language))
        return if (modelDir.exists() && modelDir.isDirectory) {
            modelDir.absolutePath
        } else {
            null
        }
    }

    /**
     * Checks if a model is installed.
     */
    fun isModelInstalled(language: String): Boolean {
        return getModelPath(language) != null
    }

    /**
     * Gets the expected model directory name for a language.
     */
    private fun getModelName(language: String): String {
        return when (language) {
            "en" -> "vosk-model-small-en-us-0.15"
            "ru" -> "vosk-model-small-ru-0.22"
            "es" -> "vosk-model-small-es-0.42"
            "fr" -> "vosk-model-small-fr-0.22"
            "de" -> "vosk-model-small-de-0.15"
            "pt" -> "vosk-model-small-pt-0.3"
            "it" -> "vosk-model-small-it-0.22"
            "zh" -> "vosk-model-small-cn-0.22"
            "ja" -> "vosk-model-small-ja-0.22"
            "ko" -> "vosk-model-small-ko-0.22"
            else -> "vosk-model-small-en-us-0.15" // Default to English
        }
    }

    /**
     * Gets list of installed models.
     */
    fun getInstalledModels(): List<String> {
        return modelsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }

    /**
     * Gets the models directory path.
     */
    fun getModelsDirectory(): String {
        return modelsDir.absolutePath
    }

    /**
     * Gets download URL for a model.
     */
    fun getModelDownloadUrl(language: String): String {
        val modelName = getModelName(language)
        return "https://alphacephei.com/vosk/models/$modelName.zip"
    }

    /**
     * Gets instructions for downloading models.
     */
    fun getDownloadInstructions(language: String): String {
        val url = getModelDownloadUrl(language)
        val modelName = getModelName(language)
        val targetPath = File(modelsDir, modelName).absolutePath

        return """
            To use voice-to-text, download the Vosk model:

            1. Download: $url
            2. Extract the ZIP file
            3. Place the extracted folder at: $targetPath

            The folder should contain files like:
            - am/
            - graph/
            - conf/
            - ivector/

            Alternative: Use the auto-download feature in settings (if implemented).
        """.trimIndent()
    }
}
