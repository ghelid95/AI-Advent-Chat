package data

/**
 * Specialized prompts for data analysis tasks
 */
object DataAnalysisPrompts {

    /**
     * Base system prompt for data analysis
     */
    const val SYSTEM_PROMPT = """You are a data analyst AI assistant. Your task is to analyze datasets and answer questions based on the provided data.

Guidelines:
1. Base your answers ONLY on the provided dataset
2. Be specific and cite actual data points when possible
3. If the data doesn't support a conclusion, say so
4. Provide numerical evidence when available
5. Keep answers concise but informative
6. If asked about trends, look for patterns in the data
7. For error analysis, count occurrences and identify the most common
8. For user behavior, look at the data flow and drop-off points"""

    /**
     * Template for general data question
     */
    fun createAnalysisPrompt(question: String, dataContext: String): String {
        return """Dataset Information:
$dataContext

Question: $question

Please analyze the dataset and provide a clear, data-driven answer to the question. Include specific numbers and examples from the data."""
    }

    /**
     * Template for error analysis
     */
    fun createErrorAnalysisPrompt(dataContext: String): String {
        return """Dataset Information:
$dataContext

Perform an error analysis:
1. What are the most common errors (by count and percentage)?
2. Are there any patterns in when errors occur?
3. Which error types are most critical?
4. Provide specific counts and examples

Format your answer as:
- Top errors (with counts)
- Patterns observed
- Recommendations"""
    }

    /**
     * Template for user funnel analysis
     */
    fun createFunnelAnalysisPrompt(dataContext: String, stages: List<String>): String {
        val stagesStr = stages.joinToString(" → ")
        return """Dataset Information:
$dataContext

Analyze the user funnel through these stages: $stagesStr

Provide:
1. Drop-off rate at each stage
2. Where users are lost most (biggest drop-off point)
3. Conversion rate through the entire funnel
4. Specific numbers for each stage

Use the data to calculate actual percentages and counts."""
    }

    /**
     * Template for trend analysis
     */
    fun createTrendAnalysisPrompt(dataContext: String, metric: String): String {
        return """Dataset Information:
$dataContext

Analyze trends for: $metric

Provide:
1. Overall trend direction (increasing/decreasing/stable)
2. Key patterns or anomalies
3. Time periods with notable changes
4. Numerical evidence (rates, percentages, counts)

Base your analysis on the actual data provided."""
    }

    /**
     * Template for comparison analysis
     */
    fun createComparisonPrompt(dataContext: String, groupBy: String): String {
        return """Dataset Information:
$dataContext

Compare data grouped by: $groupBy

Provide:
1. Key differences between groups
2. Which group performs best/worst
3. Statistical comparison (counts, averages, percentages)
4. Notable outliers or patterns

Support all conclusions with specific data points."""
    }

    /**
     * Template for summary/overview
     */
    fun createSummaryPrompt(dataContext: String): String {
        return """Dataset Information:
$dataContext

Provide a comprehensive data summary:
1. Dataset overview (size, structure)
2. Key insights and patterns
3. Notable findings or anomalies
4. Data quality observations
5. Top 3 most important insights

Be specific and include actual numbers from the data."""
    }

    /**
     * Template for correlation analysis
     */
    fun createCorrelationPrompt(dataContext: String, variables: List<String>): String {
        val varsStr = variables.joinToString(", ")
        return """Dataset Information:
$dataContext

Analyze potential correlations between: $varsStr

Look for:
1. Relationships between these variables
2. Patterns when one changes, does another change?
3. Strong vs weak associations
4. Provide examples from the data

Focus on observable patterns in the provided data."""
    }

    /**
     * Template for data quality check
     */
    fun createQualityCheckPrompt(dataContext: String): String {
        return """Dataset Information:
$dataContext

Perform a data quality assessment:
1. Missing values or null counts
2. Potential data errors or inconsistencies
3. Outliers or unusual values
4. Completeness of the dataset
5. Recommendations for data cleaning

Provide specific examples of any issues found."""
    }

    /**
     * Quick insight templates
     */
    object QuickInsights {
        const val TOP_VALUES = "What are the top 5 most common values in the dataset?"
        const val RECENT_ACTIVITY = "What happened most recently in this data?"
        const val ANOMALIES = "Are there any anomalies or unusual patterns?"
        const val TIME_DISTRIBUTION = "How is the data distributed over time?"
        const val CATEGORY_BREAKDOWN = "What's the breakdown by categories?"
    }

    /**
     * Get suggested questions based on dataset characteristics
     */
    fun getSuggestedQuestions(dataset: Dataset): List<String> {
        val questions = mutableListOf<String>()

        // Based on format
        when (dataset.format) {
            DataFormat.LOG -> {
                questions.add("What are the most common error types?")
                questions.add("How many errors occurred in the last hour?")
                questions.add("What are the critical errors that need attention?")
            }
            DataFormat.CSV, DataFormat.JSON -> {
                // Based on column types
                val hasNumeric = dataset.summary.numericStats.isNotEmpty()
                val hasCategorical = dataset.summary.categoricalStats.isNotEmpty()

                if (hasNumeric) {
                    questions.add("What are the average values across numeric columns?")
                    questions.add("Are there any outliers in the numeric data?")
                }

                if (hasCategorical) {
                    questions.add("What's the distribution of categories?")
                    questions.add("Which category appears most frequently?")
                }

                // Generic questions
                questions.add("Summarize the key insights from this dataset")
                questions.add("Are there any trends or patterns?")
            }
        }

        // Based on columns
        if (dataset.columns.any { it.contains("user", ignoreCase = true) }) {
            questions.add("How many unique users are in the dataset?")
        }

        if (dataset.columns.any { it.contains("time", ignoreCase = true) || it.contains("date", ignoreCase = true) }) {
            questions.add("How is the data distributed over time?")
        }

        if (dataset.columns.any { it.contains("error", ignoreCase = true) || it.contains("status", ignoreCase = true) }) {
            questions.add("What's the error rate or failure rate?")
        }

        return questions.take(5)
    }
}
