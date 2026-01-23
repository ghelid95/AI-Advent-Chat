package data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Configuration for Ollama model optimization
 */
@Serializable
data class OllamaOptimizationConfig(
    val configName: String = "Default",
    val numCtx: Int = 2048,  // Context window size
    val numGpu: Int = 0,  // Number of GPU layers (0 = auto)
    val numThread: Int = 8,  // Number of threads
    val repeatPenalty: Float = 1.1f,  // Penalty for repetition
    val topK: Int = 40,  // Top-K sampling
    val topP: Float = 0.9f,  // Top-P (nucleus) sampling
    val temperature: Float = 0.7f,  // Temperature
    val maxTokens: Int = 1024  // Max tokens to generate
)

/**
 * Predefined optimization presets
 */
object OllamaPresets {
    val BALANCED = OllamaOptimizationConfig(
        configName = "Balanced",
        numCtx = 2048,
        numGpu = 0,
        numThread = 8,
        repeatPenalty = 1.1f,
        topK = 40,
        topP = 0.9f,
        temperature = 0.7f,
        maxTokens = 1024
    )

    val FAST = OllamaOptimizationConfig(
        configName = "Fast (Speed Priority)",
        numCtx = 1024,
        numGpu = 0,
        numThread = 4,
        repeatPenalty = 1.1f,
        topK = 20,
        topP = 0.8f,
        temperature = 0.5f,
        maxTokens = 512
    )

    val QUALITY = OllamaOptimizationConfig(
        configName = "Quality (Accuracy Priority)",
        numCtx = 4096,
        numGpu = 0,
        numThread = 16,
        repeatPenalty = 1.15f,
        topK = 50,
        topP = 0.95f,
        temperature = 0.3f,
        maxTokens = 2048
    )

    val CREATIVE = OllamaOptimizationConfig(
        configName = "Creative",
        numCtx = 2048,
        numGpu = 0,
        numThread = 8,
        repeatPenalty = 1.0f,
        topK = 60,
        topP = 0.95f,
        temperature = 0.9f,
        maxTokens = 1024
    )

    val PRECISE = OllamaOptimizationConfig(
        configName = "Precise (Deterministic)",
        numCtx = 2048,
        numGpu = 0,
        numThread = 12,
        repeatPenalty = 1.2f,
        topK = 10,
        topP = 0.7f,
        temperature = 0.1f,
        maxTokens = 1024
    )

    fun getAllPresets() = listOf(BALANCED, FAST, QUALITY, CREATIVE, PRECISE)
}

/**
 * Result of a comparison test
 */
@Serializable
data class ComparisonResult(
    val configName: String,
    val prompt: String,
    val response: String,
    val tokensPerSecond: Float,
    val totalTimeMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val config: OllamaOptimizationConfig,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Storage for Ollama optimization settings
 */
@Serializable
data class OllamaSettings(
    val selectedConfig: OllamaOptimizationConfig = OllamaPresets.BALANCED,
    val customConfigs: List<OllamaOptimizationConfig> = emptyList(),
    val comparisonHistory: List<ComparisonResult> = emptyList()
)

/**
 * Service for Ollama optimization and testing
 */
class OllamaOptimizer(private val baseUrl: String = "http://localhost:11434") {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val settingsFile: File = run {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".ai-advent-chat")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        File(appDir, "ollama-settings.json")
    }

    /**
     * Load Ollama settings
     */
    fun loadSettings(): OllamaSettings {
        return try {
            if (settingsFile.exists()) {
                json.decodeFromString<OllamaSettings>(settingsFile.readText())
            } else {
                OllamaSettings()
            }
        } catch (e: Exception) {
            println("[OllamaOptimizer] Error loading settings: ${e.message}")
            OllamaSettings()
        }
    }

    /**
     * Save Ollama settings
     */
    fun saveSettings(settings: OllamaSettings) {
        try {
            val jsonString = json.encodeToString(settings)
            settingsFile.writeText(jsonString)
            println("[OllamaOptimizer] Saved Ollama settings")
        } catch (e: Exception) {
            println("[OllamaOptimizer] Error saving settings: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Test a configuration with a given prompt
     */
    suspend fun testConfiguration(
        config: OllamaOptimizationConfig,
        prompt: String,
        model: String
    ): ComparisonResult {
        val client = OllamaLlmClient(baseUrl)

        val messages = listOf(
            ChatMessage(
                role = "user",
                content = ChatMessageContent.Text(prompt)
            )
        )

        val startTime = System.currentTimeMillis()

        // Create custom options based on config
        val result = client.sendMessageWithConfig(
            messages = messages,
            systemPrompt = "",
            temperature = config.temperature,
            model = model,
            maxTokens = config.maxTokens,
            config = config
        )

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime

        return result.fold(
            onSuccess = { llmMessage ->
                val tokensPerSecond = if (totalTime > 0) {
                    (llmMessage.usage?.outputTokens ?: 0) * 1000f / totalTime
                } else {
                    0f
                }

                ComparisonResult(
                    configName = config.configName,
                    prompt = prompt,
                    response = llmMessage.answer,
                    tokensPerSecond = tokensPerSecond,
                    totalTimeMs = totalTime,
                    inputTokens = llmMessage.usage?.inputTokens ?: 0,
                    outputTokens = llmMessage.usage?.outputTokens ?: 0,
                    config = config
                )
            },
            onFailure = { error ->
                ComparisonResult(
                    configName = config.configName,
                    prompt = prompt,
                    response = "Error: ${error.message}",
                    tokensPerSecond = 0f,
                    totalTimeMs = endTime - startTime,
                    inputTokens = 0,
                    outputTokens = 0,
                    config = config
                )
            }
        )
    }

    /**
     * Run A/B comparison test with multiple configurations
     */
    suspend fun runComparison(
        configs: List<OllamaOptimizationConfig>,
        prompt: String,
        model: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<ComparisonResult> {
        val results = mutableListOf<ComparisonResult>()

        configs.forEachIndexed { index, config ->
            println("[OllamaOptimizer] Testing config ${index + 1}/${configs.size}: ${config.configName}")
            onProgress(index + 1, configs.size)

            val result = testConfiguration(config, prompt, model)
            results.add(result)

            println("[OllamaOptimizer] Result: ${result.tokensPerSecond} tok/s, ${result.totalTimeMs}ms")
        }

        return results
    }

    /**
     * Save comparison results to history
     */
    fun saveComparisonResults(results: List<ComparisonResult>) {
        val settings = loadSettings()
        val updatedSettings = settings.copy(
            comparisonHistory = (settings.comparisonHistory + results).takeLast(50) // Keep last 50 results
        )
        saveSettings(updatedSettings)
    }

    /**
     * Get best configuration from history based on tokens per second
     */
    fun getBestConfiguration(): OllamaOptimizationConfig? {
        val settings = loadSettings()
        return settings.comparisonHistory
            .maxByOrNull { it.tokensPerSecond }
            ?.config
    }
}

