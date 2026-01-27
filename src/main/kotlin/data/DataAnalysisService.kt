package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

/**
 * Service for analyzing datasets using local LLM (Ollama)
 */
class DataAnalysisService(
    private val ollamaClient: OllamaLlmClient
) {

    private var currentDataset: Dataset? = null
    private val analysisHistory = mutableListOf<AnalysisResult>()
    private val dataLoader = DataLoader()

    /**
     * Load dataset from file
     */
    suspend fun loadDataset(file: java.io.File): Result<Dataset> {
        return withContext(Dispatchers.IO) {
            try {
                val result = dataLoader.loadDataset(file)
                result.onSuccess { dataset ->
                    currentDataset = dataset
                    println("[DataAnalysis] Loaded dataset: ${dataset.name} with ${dataset.rowCount} rows")
                }
                result
            } catch (e: Exception) {
                println("[DataAnalysis] Error loading dataset: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Get currently loaded dataset
     */
    fun getCurrentDataset(): Dataset? = currentDataset

    /**
     * Ask analytical question about the dataset
     */
    suspend fun analyzeQuestion(
        question: String,
        model: String = "llama2",
        maxDataRows: Int = 100
    ): Result<AnalysisResult> {
        val dataset = currentDataset
            ?: return Result.failure(IllegalStateException("No dataset loaded"))

        return withContext(Dispatchers.Default) {
            try {
                val query = AnalysisQuery(question, dataset.name)

                var answer = ""
                val processingTime = measureTimeMillis {
                    // Prepare context with dataset information
                    val dataContext = dataset.toContextString(maxDataRows)

                    // Create prompt
                    val prompt = DataAnalysisPrompts.createAnalysisPrompt(question, dataContext)

                    // Query Ollama
                    val messages = listOf(
                        ChatMessage(
                            role = "user",
                            content = ChatMessageContent.Text(prompt)
                        )
                    )

                    val result = ollamaClient.sendMessage(
                        messages = messages,
                        systemPrompt = DataAnalysisPrompts.SYSTEM_PROMPT,
                        temperature = 0.3f, // Low temperature for factual analysis
                        model = model,
                        maxTokens = 1024,
                        tools = null
                    )

                    result.fold(
                        onSuccess = { llmMessage ->
                            answer = llmMessage.answer
                        },
                        onFailure = { error ->
                            throw error
                        }
                    )
                }

                val analysisResult = AnalysisResult(
                    query = query,
                    answer = answer,
                    processingTimeMs = processingTime
                )

                analysisHistory.add(analysisResult)

                Result.success(analysisResult)
            } catch (e: Exception) {
                println("[DataAnalysis] Error analyzing question: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Perform error analysis on log data
     */
    suspend fun analyzeErrors(
        model: String = "llama2",
        maxDataRows: Int = 200
    ): Result<AnalysisResult> {
        val dataset = currentDataset
            ?: return Result.failure(IllegalStateException("No dataset loaded"))

        if (dataset.format != DataFormat.LOG) {
            return Result.failure(IllegalStateException("Error analysis requires log data"))
        }

        return withContext(Dispatchers.Default) {
            try {
                val query = AnalysisQuery("Error Analysis", dataset.name)

                var answer = ""
                val processingTime = measureTimeMillis {
                    val dataContext = dataset.toContextString(maxDataRows)
                    val prompt = DataAnalysisPrompts.createErrorAnalysisPrompt(dataContext)

                    val messages = listOf(
                        ChatMessage(
                            role = "user",
                            content = ChatMessageContent.Text(prompt)
                        )
                    )

                    val result = ollamaClient.sendMessage(
                        messages = messages,
                        systemPrompt = DataAnalysisPrompts.SYSTEM_PROMPT,
                        temperature = 0.2f,
                        model = model,
                        maxTokens = 1024,
                        tools = null
                    )

                    result.fold(
                        onSuccess = { llmMessage -> answer = llmMessage.answer },
                        onFailure = { error -> throw error }
                    )
                }

                val analysisResult = AnalysisResult(
                    query = query,
                    answer = answer,
                    processingTimeMs = processingTime
                )

                analysisHistory.add(analysisResult)
                Result.success(analysisResult)
            } catch (e: Exception) {
                println("[DataAnalysis] Error in error analysis: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Get dataset summary
     */
    suspend fun getSummary(
        model: String = "llama2",
        maxDataRows: Int = 50
    ): Result<AnalysisResult> {
        val dataset = currentDataset
            ?: return Result.failure(IllegalStateException("No dataset loaded"))

        return withContext(Dispatchers.Default) {
            try {
                val query = AnalysisQuery("Dataset Summary", dataset.name)

                var answer = ""
                val processingTime = measureTimeMillis {
                    val dataContext = dataset.toContextString(maxDataRows)
                    val prompt = DataAnalysisPrompts.createSummaryPrompt(dataContext)

                    val messages = listOf(
                        ChatMessage(
                            role = "user",
                            content = ChatMessageContent.Text(prompt)
                        )
                    )

                    val result = ollamaClient.sendMessage(
                        messages = messages,
                        systemPrompt = DataAnalysisPrompts.SYSTEM_PROMPT,
                        temperature = 0.3f,
                        model = model,
                        maxTokens = 1024,
                        tools = null
                    )

                    result.fold(
                        onSuccess = { llmMessage -> answer = llmMessage.answer },
                        onFailure = { error -> throw error }
                    )
                }

                val analysisResult = AnalysisResult(
                    query = query,
                    answer = answer,
                    processingTimeMs = processingTime
                )

                analysisHistory.add(analysisResult)
                Result.success(analysisResult)
            } catch (e: Exception) {
                println("[DataAnalysis] Error getting summary: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Get analysis history
     */
    fun getHistory(): List<AnalysisResult> = analysisHistory.toList()

    /**
     * Clear history
     */
    fun clearHistory() {
        analysisHistory.clear()
    }

    /**
     * Get suggested questions for current dataset
     */
    fun getSuggestedQuestions(): List<String> {
        val dataset = currentDataset ?: return emptyList()
        return DataAnalysisPrompts.getSuggestedQuestions(dataset)
    }

    /**
     * Export analysis results
     */
    fun exportResults(outputFile: java.io.File) {
        val content = buildString {
            appendLine("# Data Analysis Report")
            appendLine()

            currentDataset?.let { dataset ->
                appendLine("## Dataset: ${dataset.name}")
                appendLine("Format: ${dataset.format}")
                appendLine("Rows: ${dataset.rowCount}")
                appendLine("Columns: ${dataset.columns.joinToString(", ")}")
                appendLine()
                appendLine("### Summary")
                appendLine(dataset.summary.toString())
                appendLine()
            }

            appendLine("## Analysis Results")
            appendLine()

            analysisHistory.forEach { result ->
                appendLine("### ${result.query.question}")
                appendLine("**Time:** ${result.processingTimeMs}ms")
                appendLine()
                appendLine(result.answer)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }

        outputFile.writeText(content, Charsets.UTF_8)
        println("[DataAnalysis] Exported results to ${outputFile.absolutePath}")
    }
}
