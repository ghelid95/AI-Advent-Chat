package data

import kotlinx.serialization.Serializable
import java.io.File

/**
 * Supported data formats
 */
enum class DataFormat {
    CSV,
    JSON,
    LOG
}

/**
 * Loaded dataset with metadata
 */
@Serializable
data class Dataset(
    val name: String,
    val format: DataFormat,
    val filePath: String,
    val rows: List<DataRow>,
    val columns: List<String>,
    val rowCount: Int,
    val summary: DataSummary,
    val loadedAt: Long = System.currentTimeMillis()
) {
    fun toContextString(maxRows: Int = 100): String {
        val header = "Dataset: $name\n" +
                "Format: $format\n" +
                "Rows: $rowCount\n" +
                "Columns: ${columns.joinToString(", ")}\n\n"

        val summaryStr = "Summary:\n${summary.toString()}\n\n"

        val sampleRows = "Sample Data (first ${minOf(maxRows, rows.size)} rows):\n" +
                rows.take(maxRows).joinToString("\n") { it.toString() }

        return header + summaryStr + sampleRows
    }
}

/**
 * Single row of data
 */
@Serializable
data class DataRow(
    val values: Map<String, String>
) {
    override fun toString(): String {
        return values.entries.joinToString(", ") { "${it.key}: ${it.value}" }
    }
}

/**
 * Statistical summary of dataset
 */
@Serializable
data class DataSummary(
    val totalRows: Int,
    val totalColumns: Int,
    val columnTypes: Map<String, ColumnType>,
    val numericStats: Map<String, NumericStats>,
    val categoricalStats: Map<String, CategoricalStats>,
    val nullCounts: Map<String, Int>
) {
    override fun toString(): String {
        val lines = mutableListOf<String>()
        lines.add("Total Rows: $totalRows")
        lines.add("Total Columns: $totalColumns")
        lines.add("\nColumn Types:")
        columnTypes.forEach { (col, type) ->
            lines.add("  $col: $type")
        }

        if (numericStats.isNotEmpty()) {
            lines.add("\nNumeric Statistics:")
            numericStats.forEach { (col, stats) ->
                lines.add("  $col: min=${stats.min}, max=${stats.max}, avg=${String.format("%.2f", stats.average)}")
            }
        }

        if (categoricalStats.isNotEmpty()) {
            lines.add("\nCategorical Statistics:")
            categoricalStats.forEach { (col, stats) ->
                lines.add("  $col: ${stats.uniqueValues} unique values, top: ${stats.topValues.entries.take(3).joinToString { "${it.key}(${it.value})" }}")
            }
        }

        return lines.joinToString("\n")
    }
}

/**
 * Column data type
 */
enum class ColumnType {
    NUMERIC,
    CATEGORICAL,
    DATETIME,
    BOOLEAN,
    TEXT
}

/**
 * Statistics for numeric columns
 */
@Serializable
data class NumericStats(
    val min: Double,
    val max: Double,
    val average: Double,
    val median: Double,
    val stdDev: Double
)

/**
 * Statistics for categorical columns
 */
@Serializable
data class CategoricalStats(
    val uniqueValues: Int,
    val topValues: Map<String, Int>  // Value -> Count
)

/**
 * Analysis query and result
 */
@Serializable
data class AnalysisQuery(
    val question: String,
    val dataset: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AnalysisResult(
    val query: AnalysisQuery,
    val answer: String,
    val confidence: String = "N/A",
    val dataPoints: List<String> = emptyList(),
    val processingTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Log entry structure
 */
@Serializable
data class LogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toDataRow(): DataRow {
        val values = mutableMapOf(
            "timestamp" to timestamp,
            "level" to level,
            "message" to message
        )
        values.putAll(metadata)
        return DataRow(values)
    }
}

/**
 * Common log patterns for parsing
 */
object LogPatterns {
    // [2024-01-25 10:30:45] ERROR: Connection timeout
    val TIMESTAMPED_LEVEL = Regex("""^\[(.+?)\]\s+(\w+):\s+(.+)$""")

    // 2024-01-25 10:30:45 ERROR Connection timeout
    val SPACE_SEPARATED = Regex("""^(\S+\s+\S+)\s+(\w+)\s+(.+)$""")

    // ERROR [2024-01-25 10:30:45] Connection timeout
    val LEVEL_FIRST = Regex("""^(\w+)\s+\[(.+?)\]\s+(.+)$""")

    // JSON-like: {"timestamp":"2024-01-25 10:30:45","level":"ERROR","message":"Connection timeout"}
    // Will be handled separately by JSON parser
}
